/*******************************************************************************
 * Copyright (c) 2009 Progress Software, Inc.
 * Copyright (c) 2008 IBM Corporation and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.fusesource.hawtjni.ui;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

import net.miginfocom.swt.MigLayout;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.TableEditor;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Dialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Sash;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.swt.widgets.TreeItem;
import org.fusesource.hawtjni.generator.ProgressMonitor;
import org.fusesource.hawtjni.generator.HawtJNI.UsageException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static org.eclipse.swt.SWT.*;
import static org.fusesource.hawtjni.ui.UISupport.*;

import static org.eclipse.swt.SWT.*;
import static org.fusesource.hawtjni.ui.UISupport.*;

import static org.eclipse.swt.SWT.*;
import static org.fusesource.hawtjni.ui.UISupport.*;

/**
 * 
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public class MacGeneratorUI {
	MacGenerator gen;
	boolean actions = true;

	Tree nodesTree;
	Table attribTable;

	public MacGeneratorUI(MacGenerator gen) {
		this.gen = gen;
	}

	TreeItem lastParent;
	TreeItem addChild (Node node, TreeItem superItem) {
		if (node.getNodeType() == Node.TEXT_NODE) return null;
		String name = node.getNodeName();
		TreeItem parentItem = null;
		if (lastParent != null && !lastParent.isDisposed() && lastParent.getParentItem() == superItem && name.equals(lastParent.getData())) {
			parentItem = lastParent;
		} else {
			TreeItem[] items = superItem.getItems();
			for (int i = 0; i < items.length; i++) {
				if (name.equals(items[i].getData())) {
					parentItem = items[i];
					break;
				}
			}
			if (parentItem == null) {
				parentItem = new TreeItem(superItem, SWT.NONE);
				parentItem.setData(name);
				parentItem.setText(getPrettyText(name));
			}
			lastParent = parentItem;
		}
		TreeItem item = new TreeItem(parentItem, SWT.NONE);
		Node idAttrib = gen.getIDAttribute(node);
		item.setText(idAttrib != null ? idAttrib.getNodeValue() : name);
		item.setData(node);
		checkItem(node, item);
		NodeList childNodes = node.getChildNodes();
		if (childNodes.getLength() > 0) new TreeItem(item, SWT.NONE);
		return item;
	}
	
	void checkPath(TreeItem item, boolean checked, boolean grayed) {
	    if (item == null) return;
	    if (grayed) {
	        checked = true;
	    } else {
	        int index = 0;
	        TreeItem[] items = item.getItems();
	        while (index < items.length) {
	            TreeItem child = items[index];
	            if (child.getGrayed() || checked != child.getChecked()) {
	                checked = grayed = true;
	                break;
	            }
	            index++;
	        }
	    }
	    item.setChecked(checked);
	    item.setGrayed(grayed);
	    updateGenAttribute(item);
	    checkPath(item.getParentItem(), checked, grayed);
	}
	
	void checkItem(Node node, TreeItem item) {
		NamedNodeMap attributes = node.getAttributes();
		Node gen = attributes.getNamedItem("hawtjni_gen");
		if (gen != null) {
			String value = gen.getNodeValue();
			boolean grayed = value.equals("mixed");
			boolean checked = grayed || value.equals("true");
			item.setChecked(checked);
			item.setGrayed(grayed);
		}
	}
	
	boolean getEditable(TableItem item, int column) {
		if (!(item.getData() instanceof Node)) return false;
		String attribName = item.getText();
		return attribName.startsWith("hawtjni_");
	}

	String getPrettyText(String text) {
		if (text.equals("class")) return "Classes";
		if (text.equals("depends_on")) return "Depends_on";
		return text.substring(0, 1).toUpperCase() + text.substring(1) + "s";
	}

	void checkChildren(TreeItem item) {
		TreeItem dummy;
		if (item.getItemCount() == 1 && (dummy = item.getItem(0)).getData() == null) {
			dummy.dispose();
			Node node = (Node)item.getData();
			NodeList childNodes = node.getChildNodes();
			for (int i = 0, length = childNodes.getLength(); i < length; i++) {
				addChild(childNodes.item(i), item);
			}
			/* Figure out categories state */
			TreeItem[] items = item.getItems();
			for (int i = 0; i < items.length; i++) {
				TreeItem[] children = items[i].getItems();
				int checkedCount = 0;
				for (int j = 0; j < children.length; j++) {
					if (children[j].getChecked()) checkedCount++;
					if (children[j].getGrayed()) break;
				}
				items[i].setChecked(checkedCount != 0);
				items[i].setGrayed(checkedCount != children.length);
			}
		}
	}
	
	void checkItems(TreeItem item, boolean checked) {
	    item.setGrayed(false);
	    item.setChecked(checked);
	    updateGenAttribute(item);
	    TreeItem[] items = item.getItems();
	    if (items.length == 1 && items[0].getData() == null) {
	    	/* Update model only if view is not created */
			Node node = (Node)item.getData();
			NodeList childNodes = node.getChildNodes();
			for (int i = 0, length = childNodes.getLength(); i < length; i++) {
				checkNodes(childNodes.item(i), checked);
			}
	    } else {
		    for (int i = 0; i < items.length; i++) {
		        checkItems(items[i], checked);
		    }
	    }
	}
	
	void checkNodes(Node node, boolean checked) {
		if (node instanceof Element) {
			if (checked) {
				((Element)node).setAttribute("hawtjni_gen", "true");
			} else {
				((Element)node).removeAttribute("hawtjni_gen");
			}
		}
		NodeList childNodes = node.getChildNodes();
		for (int i = 0, length = childNodes.getLength(); i < length; i++) {
			checkNodes(childNodes.item(i), checked);
		}
	}
	
	void cleanup() {
	}

    
	private Composite createSourceTargetPanel(Composite parent) {
        Composite panel = new Composite(parent, SWT.NONE);
        panel.setLayout(mig("insets 0", "[]10[grow]", "[]10[]"));
        // First row..
        {
            Label label = mig(new Label(panel, NONE), "");        
            label.setText("Output Directory");
            final Text text = mig(new Text(panel, SWT.BORDER | SWT.SINGLE | SWT.SEARCH), "grow,wrap"); 
            text.addModifyListener(new ModifyListener() {
                public void modifyText(ModifyEvent event) {
                    gen.setOutputDir(text.getText());
                }
            });
            text.setText(gen.outputDir);
        }
        // 2nd row..
        {
            Label label = mig(new Label(panel, NONE), "");        
            label.setText("Target Class");
            final Text text = mig(new Text(panel, SWT.BORDER | SWT.SINGLE | SWT.SEARCH), "grow,wrap"); 
            text.addModifyListener(new ModifyListener() {
                public void modifyText(ModifyEvent event) {
                    gen.setMainClass(text.getText());
                }
            });
            text.setText(gen.mainClassName);
        }
                
        return panel;
    }
    	
    private Composite createSignaturesPanel(Composite parent) {
		Composite comp = new Composite(parent, SWT.NONE);
		comp.setLayout(mig("insets 0", "[]10[grow]10", "[]10[]"));
		
		Label label = new Label(comp, SWT.NONE);
		label.setText("Signatures:");
		
		final Text search = mig(new Text(comp, SWT.BORDER | SWT.SINGLE | SWT.SEARCH), "grow,wrap");
		search.setText(".*");
		search.addListener(SWT.DefaultSelection, new Listener() {
			public void handleEvent(Event arg0) {
				searchFor(search.getText());
			}
		});
		search.addListener(SWT.KeyDown, new Listener() {
			public void handleEvent(Event event) {
				if (event.keyCode == SWT.F6) {
					searchFor(search.getText());					
				}
			}
		});
		
		nodesTree = mig(new Tree(comp, SWT.SINGLE | SWT.CHECK | SWT.BORDER | SWT.FULL_SELECTION), "grow,span,height 100::");
		
		nodesTree.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event event) {
				TreeItem item = (TreeItem)event.item;
				if (item == null) return;
				if (event.detail != SWT.CHECK) {
					selectChild(item);
					return;
				}
				boolean checked = item.getChecked();
				item.getParent().setRedraw(false);
                checkItems(item, checked);
                checkPath(item.getParentItem(), checked, false);
                item.getParent().setRedraw(true);
			}
		});
		nodesTree.addListener(SWT.Expand, new Listener() {
			public void handleEvent(Event event) {
				checkChildren((TreeItem)event.item);				
			}
		});
		
		return comp;
	}
	
	private Composite createPropertiesPanel(Composite parent) {
		Composite comp = new Composite(parent, SWT.NONE);
        comp.setLayout(mig("insets 0", "10[grow]", "[]10[grow]"));
		
		Label label = mig(new Label(comp, SWT.NONE), "wrap");
		label.setText("Properties:");
		
		attribTable = mig(new Table(comp, SWT.BORDER | SWT.FULL_SELECTION), "grow");
		attribTable.setLinesVisible(true);
		attribTable.setHeaderVisible(true);
		TableColumn nameColumn = new TableColumn(attribTable, SWT.NONE);
		nameColumn.setText("Name");
		nameColumn.pack();
		TableColumn valueColumn = new TableColumn(attribTable, SWT.NONE);
		valueColumn.setText("Value");
		valueColumn.pack();
		
		final Text editorTx = new Text(attribTable, SWT.SINGLE);
		final TableEditor editor = new TableEditor(attribTable);
		editor.grabHorizontal = true;
		editor.setEditor(editorTx);
		Listener textListener = new Listener() {
			public void handleEvent(Event e) {
				if (e.type == SWT.KeyDown) {
					if (e.keyCode != SWT.F6) return;
				}
				if (e.type == SWT.Traverse) {
					switch (e.detail) {
						case SWT.TRAVERSE_ESCAPE:
							editor.setItem(null);
							break;
						default:
							return;
					}
				}
				editorTx.setVisible(false);
				TableItem item = editor.getItem();
				if (item == null) return;
				int column = editor.getColumn();
				String value = editorTx.getText();
				item.setText(column, value);
				Element node = (Element)item.getData();
				String name = item.getText();
				if (value.length() != 0) {
					node.setAttribute(name, value);
				} else {
					node.removeAttribute(name);
				}
			}
		};
		editorTx.addListener(SWT.DefaultSelection, textListener);
//		editorTx.addListener(SWT.FocusOut, textListener);
		editorTx.addListener(SWT.KeyDown, textListener);
		editorTx.addListener(SWT.Traverse, textListener);
		attribTable.addListener(SWT.MouseDown, new Listener() {
			public void handleEvent(final Event e) {
				e.display.asyncExec (new Runnable () {
					public void run () {
						if (attribTable.isDisposed ()) return;
						if (e.button != 1) return;
						Point pt = new Point(e.x, e.y);
						TableItem item = attribTable.getItem(pt);
						if (item == null) return;
						int column = -1;
						for (int i = 0; i < attribTable.getColumnCount(); i++) {
							if (item.getBounds(i).contains(pt)) {
								column = i;
								break;
							}				
						}
						if (column == -1) return;
						if (!getEditable(item, column)) return;
						editor.setColumn(column);
						editor.setItem(item);
						editorTx.setText(item.getText(column));
						editorTx.selectAll();
						editorTx.setVisible(true);
						editorTx.setFocus();
					}
				});
			}
		});
		
		return comp;
	}
	
	private Composite createActionsPanel(final Composite parent) {
		Composite panel = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(1, true);
		layout.marginWidth = 10;
		panel.setLayout(layout);
		
		Button generate = new Button(panel, SWT.PUSH);
		generate.setText("Generate");
		generate.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event event) {
				generate(null);
				MessageBox messageBox = new MessageBox(parent.getShell());
				messageBox.setMessage("Done");
                messageBox.open();
			}
		});
		return panel;
	}
	
    private Composite createMainPanel(Composite parent) {
        SashForm sash = mig(new SashForm(parent, HORIZONTAL|SMOOTH), "grow");
        sash.setLayout(mig("insets 0", "", "") );
        sash.setBackground(color(sash, COLOR_GRAY));
        sash.setSashWidth(2);
        mig(createSignaturesPanel(sash), "grow");
        mig(createPropertiesPanel(sash), "grow");        
        return sash;
    }

    public void open(Composite parent) {
        parent.setLayout(new MigLayout("wrap 1", "[grow]", "[][grow][]"));
        mig(createSourceTargetPanel(parent), "growx");
        mig(createMainPanel(parent), "grow,wmin 0,hmin 0");
        mig(createActionsPanel(parent), "align right");
        updateNodes();
    }

    public void generate(ProgressMonitor progress) {
		try {
            gen.generate(progress);
        } catch (UsageException e) {
            e.printStackTrace();
        }
	}
	
	public boolean getActionsVisible() {
		return actions;
	}

    public void dispose() {
		cleanup();
	}
	
	ArrayList<Node> flatNodes;
	void searchFor(String name) {
		TreeItem[] selection = nodesTree.getSelection();
		Node node = null;
		if (selection.length != 0) {
			if (selection[0].getData() instanceof Node) {
				node = (Node)selection[0].getData();
			} else {
				if (selection[0].getItemCount() > 0 && selection[0].getItem(0).getData() instanceof Node) {
					node = (Node)selection[0].getItem(0).getData();
				}
			}
		}
		Document[] documents = gen.getDocuments();
		if (node == null && documents.length > 0) node = gen.getDocuments()[0];
		if (flatNodes == null) {
			flatNodes = new ArrayList<Node>();
			for (int i = 0; i < documents.length; i++) {
				addNodes(documents[i], flatNodes);
			}
		}
		int index = 0;
		while (flatNodes.get(index++) != node);		
		int start = index;
		while (index < flatNodes.size()) {
			Node child = flatNodes.get(index);
			Node attribName = gen.getIDAttribute(child);
			if (attribName != null && attribName.getNodeValue().matches(name)) {
				selectNode(child);
				return;
			}
			index++;
		}
		index = 0;
		while (index < start) {
			Node child = flatNodes.get(index);
			Node attribName = gen.getIDAttribute(child);
			if (attribName != null && attribName.getNodeValue().matches(name)) {
				selectNode(child);
				return;
			}
			index++;
		}
		nodesTree.getDisplay().beep();
	}
	
	void selectNode(Node node) {
		ArrayList<Node> path = new ArrayList<Node>();
		do {
			path.add(node);
			node = node.getParentNode();
		} while (node != null);
		TreeItem[] items = nodesTree.getItems();
		Collections.reverse(path);
		path.remove(0);
		while (true) {
			TreeItem item = findItem(items, path.remove(0));
			if (item == null) return;
			if (path.isEmpty()) {
				nodesTree.setSelection(item);
				selectChild(item);
				return;
			}
			items = item.getItems();
		}
	}
	
	TreeItem findItem(TreeItem[] items, Node node) {
		for (int i = 0; i < items.length; i++) {
			TreeItem item = items[i];
			checkChildren(item);
			if (item.getData() == node) return item;
		}
		for (int i = 0; i < items.length; i++) {
			TreeItem child = findItem(items[i].getItems(), node);
			if (child != null) return child;
		}
		return null;
	}
	
	void addNodes(Node node, ArrayList<Node> list) {
		if (node.getNodeType() == Node.TEXT_NODE) return;
		list.add(node);
		NodeList children = node.getChildNodes();
		for (int i = 0, length = children.getLength(); i < length; i++) {
			Node child = children.item(i);
			addNodes(child, list);
		}	
	}
	
	void selectChild(TreeItem item) {
		attribTable.removeAll();
		if (!(item.getData() instanceof Node)) return;
		Node node = (Node)item.getData();
		NamedNodeMap attributes = node.getAttributes();
		String[] extraAttribs = gen.getExtraAttributeNames(node);
		for (int i = 0; i < extraAttribs.length; i++) {
			TableItem attribItem = new TableItem(attribTable, SWT.NONE);
			attribItem.setText(extraAttribs[i]);
			attribItem.setData(node);
			attribItem.setForeground(item.getDisplay().getSystemColor(SWT.COLOR_BLUE));
			Node attrib = attributes.getNamedItem(extraAttribs[i]);
			if (attrib != null) {
				attribItem.setText(1, attrib.getNodeValue());
			}
			
		}
		checkItem(node, item);
		for (int i = 0, length = attributes.getLength(); i < length; i++) {
			Node attrib = attributes.item(i);
			String attribName = attrib.getNodeName();
			if (attribName.startsWith("hawtjni_")) continue;
			TableItem attribItem = new TableItem(attribTable, SWT.NONE);
			attribItem.setText(attribName);
			attribItem.setText(1, attrib.getNodeValue());
		}
		attribTable.getColumn(0).pack();
		attribTable.getColumn(1).setWidth(500);
	}
	
	void updateGenAttribute (TreeItem item) {
		if (item.getData() instanceof Element) {
			Element node = (Element)item.getData();
			if (item.getChecked()) {
				if (item.getGrayed()) {
					node.setAttribute("hawtjni_gen", "mixed");
				} else {
					node.setAttribute("hawtjni_gen", "true");
				}
			} else {
				node.removeAttribute("hawtjni_gen");
			}
		}
	}
	
	void updateNodes() {
		String[] xmls = gen.getXmls();
		if (xmls == null) return;
		Document[] documents = gen.getDocuments();
		for (int x = 0; x < xmls.length; x++) {
			String xmlPath = xmls[x];
			Document document = documents[x];
			if (document == null) {
				System.out.println("Could not find: " + xmlPath);
				continue;
			}
			TreeItem item = new TreeItem(nodesTree, SWT.NONE);
			String fileName = gen.getFileName(xmlPath);
			if (fileName.endsWith("Full.bridgesupport")) {
				fileName =  fileName.substring(0, fileName.length() - "Full.bridgesupport".length());
			}
			item.setText(fileName);
			Node node = document.getDocumentElement();
			item.setData(node);
			checkItem(node, item);
			new TreeItem(item, SWT.NONE);
		}
		TreeColumn[] columns = nodesTree.getColumns();
		for (int i = 0; i < columns.length; i++) {
			columns[i].pack();
		}
	}
	
	public void refresh () {
		if (nodesTree == null) return;
		gen.setXmls(null);
		flatNodes = null;
		nodesTree.getDisplay().asyncExec(new Runnable() {
			 public void run() {
				 if (nodesTree == null || nodesTree.isDisposed()) return;
				 nodesTree.removeAll();
				 attribTable.removeAll();
				 updateNodes();
			}
		});
	}
	
	public void setActionsVisible(boolean visible) {
		this.actions = visible;
	}
	
	public void setFocus() {
		nodesTree.setFocus();
	}

	public static void main(String[] args) {
//		args = new String[]{
//			"./Mac Generation/org/eclipse/hawtjni/tools/internal/AppKitFull.bridgesupport",
//			"./Mac Generation/org/eclipse/hawtjni/tools/internal/FoundationFull.bridgesupport",
//			"./Mac Generation/org/eclipse/hawtjni/tools/internal/WebKitFull.bridgesupport",
//		};
		try {
			Display display = new Display();
			Shell shell = new Shell(display);
			MacGenerator gen = new MacGenerator();
			gen.setXmls(args);
			gen.setOutputDir(new File("target").getCanonicalPath());
			gen.setMainClass("org.example.Framework");
			MacGeneratorUI ui = new MacGeneratorUI(gen);
			ui.open(shell);
			shell.open();
			while (!shell.isDisposed()) {
				if (!display.readAndDispatch())
					display.sleep();
			}
			ui.dispose();
			display.dispose();
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}
}
