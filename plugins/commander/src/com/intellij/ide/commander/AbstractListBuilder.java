// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.commander;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.IndexComparator;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.ui.ScrollingUtil;
import com.intellij.util.concurrency.AppExecutorUtil;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author Eugene Belyaev
 */
public abstract class AbstractListBuilder implements Disposable {
  protected final Project myProject;
  protected final JList myList;
  protected final Model myModel;
  protected final AbstractTreeStructure myTreeStructure;
  private final Comparator myComparator;

  protected JLabel myParentTitle = null;
  private boolean myIsDisposed;
  private AbstractTreeNode myCurrentParent = null;
  private final AbstractTreeNode myShownRoot;

  public interface Model {
    void removeAllElements();

    void addElement(final Object node);

    void replaceElements(final List newElements);

    Object[] toArray();

    int indexOf(final Object o);

    int getSize();

    Object getElementAt(int idx);
  }

  public AbstractListBuilder(final Project project,
                             final JList list,
                             final Model model,
                             final AbstractTreeStructure treeStructure,
                             final Comparator comparator,
                             final boolean showRoot) {
    myProject = project;
    myList = list;
    myModel = model;
    myTreeStructure = treeStructure;
    myComparator = comparator;
    myShownRoot = (AbstractTreeNode) getShownRoot(showRoot);
  }

  private Object getShownRoot(boolean showRoot) {
    final Object rootElement = myTreeStructure.getRootElement();
    if (showRoot) return rootElement;

    final Object[] rootChildren = myTreeStructure.getChildElements(rootElement);
    if (rootChildren.length == 1 && shouldEnterSingleTopLevelElement(rootChildren[0])) {
      return rootChildren[0];
    }
    else {
      return rootElement;
    }
  }

  protected abstract boolean shouldEnterSingleTopLevelElement(Object rootChild);

  public final void setParentTitle(final JLabel parentTitle) {
    myParentTitle = parentTitle;
  }

  public final void drillDown() {
    final Object value = getSelectedValue();
    if (value instanceof AbstractTreeNode node) {
      drillDown(node, getChildren(node));
    }
    else { // an element that denotes parent
      goUp();
    }
  }

  public final void drillDown(final AbstractTreeNode node, final Object[] children) {
    try {
      buildList(node, children);
      ensureSelectionExist();
    }
    finally {
      updateParentTitle();
    }
  }

  public final void goUp() {
    if (myCurrentParent == myShownRoot.getParent()) {
      return;
    }
    final AbstractTreeNode element = myCurrentParent.getParent();
    if (element == null) {
      return;
    }

    try {
      AbstractTreeNode oldParent = myCurrentParent;

      buildList(element);

      for (int i = 0; i < myModel.getSize(); i++) {
        if (myModel.getElementAt(i) instanceof NodeDescriptor desc) {
          final Object elem = desc.getElement();
          if (oldParent.equals(elem)) {
            selectItem(i);
            break;
          }
        }
      }
    }
    finally {
      updateParentTitle();
    }
  }

  protected Object getSelectedValue() {
    return myList.getSelectedValue();
  }

  protected void selectItem(int i) {
    ScrollingUtil.selectItem(myList, i);
  }

  protected void ensureSelectionExist() {
    ScrollingUtil.ensureSelectionExists(myList);
  }

  public final void selectElement(final Object element, VirtualFile virtualFile) {
    if (element == null) {
      return;
    }

    try {
      AbstractTreeNode node = goDownToElement(element, virtualFile);
      if (node == null) return;
      AbstractTreeNode parentElement = node.getParent();
      if (parentElement == null) return;

      buildList(parentElement);

      for (int i = 0; i < myModel.getSize(); i++) {
        if (myModel.getElementAt(i) instanceof AbstractTreeNode) {
          final AbstractTreeNode<?> desc = (AbstractTreeNode)myModel.getElementAt(i);
          if (desc.getValue() instanceof StructureViewTreeElement treeelement) {
            if (element.equals(treeelement.getValue())) {
              selectItem(i);
            break;
            }
          }
          else {
            if (element.equals(desc.getValue())) {
              selectItem(i);
            break;
            }
          }
        }
      }
    }
    finally {
      updateParentTitle();
    }
  }

  public final void enterElement(final PsiElement element, VirtualFile file) {
    try {
      AbstractTreeNode lastPathNode = goDownToElement(element, file);
      if (lastPathNode == null) return;
      buildList(lastPathNode);
      ensureSelectionExist();
    }
    finally {
      updateParentTitle();
    }
  }

  private AbstractTreeNode goDownToElement(final Object element, VirtualFile file) {
    return goDownToNode((AbstractTreeNode)myTreeStructure.getRootElement(), element, file);
  }

  public final void enterElement(final AbstractTreeNode element) {
    try {
      buildList(element);
      ensureSelectionExist();
    }
    finally {
      updateParentTitle();
    }
  }

  private AbstractTreeNode goDownToNode(AbstractTreeNode lastPathNode, final Object lastPathElement, VirtualFile file) {
    if (file == null) return lastPathNode;
    AbstractTreeNode found = lastPathNode;
    while (found != null) {
      if (nodeIsAcceptableForElement(lastPathNode, lastPathElement)) {
        break;
      }
      else {
        found = findInChildren(lastPathNode, file, lastPathElement);
        if (found != null) {
          lastPathNode = found;
        }
      }
    }
    return lastPathNode;
  }

  private AbstractTreeNode findInChildren(AbstractTreeNode<?> rootElement, VirtualFile file, Object element) {
    Object[] childElements = getChildren(rootElement);
    List<AbstractTreeNode<?>> nodes = getAllAcceptableNodes(childElements, file);
    if (nodes.size() == 1) return nodes.get(0);
    if (nodes.isEmpty()) return null;
    if (file.isDirectory()) {
      return nodes.get(0);
    }
    else {
      return performDeepSearch(nodes.toArray(), element, new HashSet<>());
    }
  }

  private AbstractTreeNode performDeepSearch(Object[] nodes, Object element, Set<? super AbstractTreeNode<?>> visited) {
    for (Object node1 : nodes) {
      AbstractTreeNode<?> node = (AbstractTreeNode)node1;
      if (nodeIsAcceptableForElement(node, element)) return node;
      Object[] children = getChildren(node);
      if (visited.add(node)) {
        AbstractTreeNode<?> nodeResult = performDeepSearch(children, element, visited);
        if (nodeResult != null) {
          return nodeResult;
        }
      }
    }
    return null;
  }

  protected abstract boolean nodeIsAcceptableForElement(AbstractTreeNode node, Object element);

  protected abstract List<AbstractTreeNode<?>> getAllAcceptableNodes(Object[] childElements, VirtualFile file);

  @Override
  public void dispose() {
    myIsDisposed = true;
  }

  private void buildList(final AbstractTreeNode parentElement) {
    buildList(parentElement, getChildren(parentElement));
  }

  private void buildList(final AbstractTreeNode parentElement, final Object[] children) {
    myCurrentParent = parentElement;
    Future<?> future = AppExecutorUtil.getAppScheduledExecutorService().schedule(
      () -> myList.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR)),
      200, TimeUnit.MILLISECONDS
    );

    myModel.removeAllElements();
    if (shouldAddTopElement()) {
      Object value = parentElement.getValue();
      if (value != null) {
        myModel.addElement(new TopLevelNode(myProject, value));
      }
    }

    for (Object aChildren : children) {
      AbstractTreeNode child = (AbstractTreeNode)aChildren;
      ReadAction.run(() -> child.update());
    }
    if (myComparator != null) {
      Arrays.sort(children, myComparator);
    }
    for (Object aChildren : children) {
      myModel.addElement(aChildren);
    }

    boolean canceled = future.cancel(false);
    if (!canceled) {
      ApplicationManager.getApplication().executeOnPooledThread(() -> myList.setCursor(Cursor.getDefaultCursor()));
    }
  }

  protected boolean shouldAddTopElement() {
    return !myShownRoot.equals(myCurrentParent);
  }

  private Object[] getChildren(final AbstractTreeNode parentElement) {
     if (parentElement == null) {
       return new Object[]{myTreeStructure.getRootElement()};
     }
     else {
       return myTreeStructure.getChildElements(parentElement);
     }
  }

  protected final void updateList() {
    if (myIsDisposed || myCurrentParent == null) {
      return;
    }
    if (myTreeStructure.hasSomethingToCommit()) {
      myTreeStructure.commit();
    }

    AbstractTreeNode<?> parentDescriptor = myCurrentParent;

    while (true) {
      parentDescriptor.update();
      if (parentDescriptor.getValue() != null) break;
      parentDescriptor = parentDescriptor.getParent();
    }

    Object[] children = getChildren(parentDescriptor);
    HashMap<Object, Integer> elementToIndexMap = new HashMap<>();
    for (int i = 0; i < children.length; i++) {
      elementToIndexMap.put(children[i], Integer.valueOf(i));
    }

    List<NodeDescriptor<?>> resultDescriptors = new ArrayList<>();
    Object[] listChildren = myModel.toArray();
    for (final Object child : listChildren) {
      if (!(child instanceof NodeDescriptor descriptor)) {
        continue;
      }
      descriptor.update();
      final Object newElement = descriptor.getElement();
      final Integer index = newElement != null ? elementToIndexMap.get(newElement) : null;
      if (index != null) {
        resultDescriptors.add(descriptor);
        descriptor.setIndex(index.intValue());
        elementToIndexMap.remove(newElement);
      }
    }

    for (final Object child : elementToIndexMap.keySet()) {
      final Integer index = elementToIndexMap.get(child);
      if (index != null) {
        final NodeDescriptor childDescr = myTreeStructure.createDescriptor(child, parentDescriptor);
        childDescr.setIndex(index.intValue());
        childDescr.update();
        resultDescriptors.add(childDescr);
      }
    }

    SelectionInfo selection = storeSelection();
    if (myComparator != null) {
      resultDescriptors.sort(myComparator);
    }
    else {
      resultDescriptors.sort(IndexComparator.INSTANCE);
    }

    if (shouldAddTopElement()) {
      final List<NodeDescriptor<?>> elems = new ArrayList<>();
      Object value = parentDescriptor.getValue();
      if (value != null) {
        elems.add(new TopLevelNode(myProject, value));
      }
      elems.addAll(resultDescriptors);
      myModel.replaceElements(elems);
    }
    else {
      myModel.replaceElements(resultDescriptors);
    }

    restoreSelection(selection);
    updateParentTitle();
  }

  private static final class SelectionInfo {
    public final ArrayList<Object> mySelectedObjects;
    public final Object myLeadSelection;
    public final int myLeadSelectionIndex;

    SelectionInfo(final ArrayList<Object> selectedObjects, final int leadSelectionIndex, final Object leadSelection) {
      myLeadSelection = leadSelection;
      myLeadSelectionIndex = leadSelectionIndex;
      mySelectedObjects = selectedObjects;
    }
  }

  private SelectionInfo storeSelection() {
    final ListSelectionModel selectionModel = myList.getSelectionModel();
    final ArrayList<Object> selectedObjects = new ArrayList<>();
    final int[] selectedIndices = myList.getSelectedIndices();
    final int leadSelectionIndex = selectionModel.getLeadSelectionIndex();
    Object leadSelection = null;
    for (final int index : selectedIndices) {
      if (index < myList.getModel().getSize()) {
        final Object o = myModel.getElementAt(index);
        selectedObjects.add(o);
        if (index == leadSelectionIndex) {
          leadSelection = o;
        }
      }
    }
    return new SelectionInfo(selectedObjects, leadSelectionIndex, leadSelection);
  }

  private void restoreSelection(final SelectionInfo selection) {
    final ArrayList<Object> selectedObjects = selection.mySelectedObjects;

    final ListSelectionModel selectionModel = myList.getSelectionModel();

    selectionModel.clearSelection();
    if (!selectedObjects.isEmpty()) {
      int leadIndex = -1;
      for (int i = 0; i < selectedObjects.size(); i++) {
        final Object o = selectedObjects.get(i);
        final int index = myModel.indexOf(o);
        if (index > -1) {
          selectionModel.addSelectionInterval(index, index);
          if (o == selection.myLeadSelection) {
            leadIndex = index;
          }
        }
      }

      if (selectionModel.getMinSelectionIndex() == -1) {
        final int toSelect = Math.min(selection.myLeadSelectionIndex, myModel.getSize() - 1);
        if (toSelect >= 0) {
          myList.setSelectedIndex(toSelect);
        }
      }
      else if (leadIndex != -1) {
        selectionModel.setLeadSelectionIndex(leadIndex);
      }
    }
  }

  public final AbstractTreeNode getParentNode() {
    return myCurrentParent;
  }

  protected abstract void updateParentTitle();

  public final void buildRoot() {
    buildList(myShownRoot);
  }
}