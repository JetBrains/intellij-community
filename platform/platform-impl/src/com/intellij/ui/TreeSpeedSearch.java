// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Conditions;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Function;

import static com.intellij.ui.tree.TreePathUtil.toTreePathArray;
import static javax.swing.tree.TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION;

public class TreeSpeedSearch extends SpeedSearchBase<JTree> {
  protected boolean myCanExpand;

  private static final Function<TreePath, String> TO_STRING = path -> path.getLastPathComponent().toString();

  private final @NotNull Function<? super TreePath, String> myPresentableStringFunction;

  /**
   * @deprecated use {@link #NODE_PRESENTATION_FUNCTION} instead.
   */
  @Deprecated
  public static final Convertor<TreePath, String> NODE_DESCRIPTOR_TOSTRING = path -> {
    NodeDescriptor descriptor = TreeUtil.getLastUserObject(NodeDescriptor.class, path);
    if (descriptor != null) return descriptor.toString();
    return TO_STRING.apply(path);
  };

  public static final Function<TreePath, String> NODE_PRESENTATION_FUNCTION = path -> {
    NodeDescriptor<?> descriptor = TreeUtil.getLastUserObject(NodeDescriptor.class, path);
    return descriptor != null ? descriptor.toString() : TO_STRING.apply(path);
  };


  public TreeSpeedSearch(JTree tree) {
    this(tree, false, TO_STRING);
  }

  public TreeSpeedSearch(@NotNull JTree tree, boolean canExpand, @NotNull Function<? super TreePath, String> presentableStringFunction) {
    super(tree);
    setComparator(new SpeedSearchComparator(false, true));
    myPresentableStringFunction = presentableStringFunction;
    myCanExpand = canExpand;

    new MySelectAllAction(tree, this).registerCustomShortcutSet(tree, null);
  }


  /**
   * @deprecated use the constructor with Function.
   */
  @Deprecated
  public TreeSpeedSearch(JTree tree, Convertor<? super TreePath, String> toString) {
    this(tree, false, toString.asFunction());
  }

  /**
   * @deprecated use the constructor with Function.
   */
  @Deprecated
  public TreeSpeedSearch(Tree tree, Convertor<? super TreePath, String> toString) {
    this(tree, false, toString.asFunction());
  }

  /**
   * @deprecated use the constructor with Function.
   */
  @Deprecated
  public TreeSpeedSearch(Tree tree, Convertor<? super TreePath, String> toString, boolean canExpand) {
    this(tree, canExpand, toString.asFunction());
  }

  /**
   * @deprecated use the constructor with Function.
   */
  @Deprecated
  public TreeSpeedSearch(JTree tree, Convertor<? super TreePath, String> toString, boolean canExpand) {
    this (tree, canExpand, toString.asFunction());
  }


  public void setCanExpand(boolean canExpand) {
    myCanExpand = canExpand;
  }

  @Override
  protected void selectElement(Object element, String selectedText) {
    TreeUtil.selectPath(myComponent, (TreePath)element);
  }

  @Override
  protected int getSelectedIndex() {
    if (myCanExpand) {
      return allPaths().indexOf(Conditions.equalTo(myComponent.getSelectionPath()));
    }
    int[] selectionRows = myComponent.getSelectionRows();
    return selectionRows == null || selectionRows.length == 0 ? -1 : selectionRows[0];
  }

  @NotNull
  @Override
  protected final ListIterator<Object> getElementIterator(int startingViewIndex) {
    return allPaths().addAllTo(new ArrayList<Object>()).listIterator(startingViewIndex);
  }

  @Override
  protected final int getElementCount() {
    return allPaths().size();
  }

  @NotNull
  protected JBIterable<TreePath> allPaths() {
    return allPaths(getComponent(), myCanExpand);
  }

  static @NotNull JBIterable<TreePath> allPaths(JTree tree, boolean expand) {
    JBIterable<TreePath> paths;
    if (expand) {
      paths = TreeUtil.treePathTraverser(tree).traverse();
    }
    else {
      TreePath[] arr = new TreePath[tree.getRowCount()];
      for (int i = 0; i < arr.length; i++) {
        arr[i] = tree.getPathForRow(i);
      }
      paths = JBIterable.of(arr);
    }
    return paths.filter(o -> !(o.getLastPathComponent() instanceof LoadingNode));
  }

  @Override
  protected String getElementText(Object element) {
    TreePath path = (TreePath)element;
    String string = myPresentableStringFunction.apply(path);
    if (string == null) return TO_STRING.apply(path);
    return string;
  }

  @NotNull
  private List<TreePath> findAllFilteredElements(String s) {
    List<TreePath> paths = new ArrayList<>();
    String _s = s.trim();

    ListIterator<Object> it = getElementIterator(0);
    while (it.hasNext()) {
      Object element = it.next();
      if (isMatchingElement(element, _s)) paths.add((TreePath)element);
    }
    return paths;
  }

  private static class MySelectAllAction extends DumbAwareAction {
    @NotNull private final JTree myTree;
    @NotNull private final TreeSpeedSearch mySearch;

    MySelectAllAction(@NotNull JTree tree, @NotNull TreeSpeedSearch search) {
      myTree = tree;
      mySearch = search;
      copyShortcutFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_SELECT_ALL));
      setEnabledInModalContext(true);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(mySearch.isPopupActive() &&
                                     myTree.getSelectionModel().getSelectionMode() == DISCONTIGUOUS_TREE_SELECTION);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      TreeSelectionModel sm = myTree.getSelectionModel();

      String query = mySearch.getEnteredPrefix();
      if (query == null) return;

      List<TreePath> filtered = mySearch.findAllFilteredElements(query);
      if (filtered.isEmpty()) return;

      boolean alreadySelected = sm.getSelectionCount() == filtered.size() &&
                                ContainerUtil.and(filtered, (path) -> sm.isPathSelected(path));

      if (alreadySelected) {
        TreePath anchor = myTree.getAnchorSelectionPath();

        sm.setSelectionPath(anchor);
        myTree.setAnchorSelectionPath(anchor);

        mySearch.findAndSelectElement(query);
      }
      else {
        TreePath currentElement = (TreePath)mySearch.findElement(query);
        TreePath anchor = ObjectUtils.chooseNotNull(currentElement, filtered.get(0));

        sm.setSelectionPaths(toTreePathArray(filtered));
        myTree.setAnchorSelectionPath(anchor);
      }
    }
  }
}
