// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

/**
 * To install the speed search on a {@link JTree} component,
 * use {@link TreeUIHelper#installTreeSpeedSearch} or one of the {@link TreeSpeedSearch#installOn} static methods
 */
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


  /**
   * @param sig parameter is used to avoid clash with the deprecated constructor
   */
  protected TreeSpeedSearch(@NotNull JTree tree, Void sig) {
    this(tree, false, sig, TO_STRING);
  }

  /**
   * @param sig parameter is used to avoid clash with the deprecated constructor
   */
  protected TreeSpeedSearch(@NotNull JTree tree,
                            boolean canExpand,
                            Void sig,
                            @NotNull Function<? super TreePath, String> presentableStringFunction) {
    super(tree, sig);
    setComparator(new SpeedSearchComparator(false, true));
    myPresentableStringFunction = presentableStringFunction;
    myCanExpand = canExpand;
  }

  /**
   * Prefer {@link TreeUIHelper#installTreeSpeedSearch(JTree, Convertor, boolean)} as it located in the API module
   */
  public static @NotNull TreeSpeedSearch installOn(@NotNull JTree tree,
                                                   boolean canExpand,
                                                   @NotNull Function<? super TreePath, String> presentableStringFunction) {
    TreeSpeedSearch search = new TreeSpeedSearch(tree, canExpand, null, presentableStringFunction);
    search.setupListeners();
    return search;
  }

  /**
   * Prefer {@link TreeUIHelper#installTreeSpeedSearch(JTree)} as it located in the API module
   */
  public static @NotNull TreeSpeedSearch installOn(@NotNull JTree tree) {
    return installOn(tree, false, TO_STRING);
  }

  @Override
  public void setupListeners() {
    super.setupListeners();

    new MySelectAllAction(myComponent, this).registerCustomShortcutSet(myComponent, null);
  }

  /**
   * @deprecated Use {@link TreeUIHelper#installTreeSpeedSearch(JTree)}
   * or the static method {@link TreeSpeedSearch#installOn(JTree)} to install a speed search on tree.
   * The {@link TreeUIHelper#installTreeSpeedSearch(JTree)} is preferable over the static call as it located in the API module
   * <p>
   * For inheritance use the non-deprecated constructor.
   * <p>
   * Also, note that non-deprecated constructor is side effect free, and you should call for {@link TreeSpeedSearch#setupListeners()}
   * method to enable speed search
   */
  @Deprecated
  public TreeSpeedSearch(JTree tree) {
    this(tree, false, TO_STRING);
  }

  /**
   * @deprecated Use {@link TreeUIHelper#installTreeSpeedSearch(JTree, Convertor, boolean)}
   * or the static method {@link TreeSpeedSearch#installOn(JTree, boolean, Function)} to install a speed search on tree.
   * The {@link TreeUIHelper#installTreeSpeedSearch(JTree, Convertor, boolean)} is preferable over the static call as it located in the API module
   * <p>
   * For inheritance use the non-deprecated constructor.
   * <p>
   * Also, note that non-deprecated constructor is side effect free, and you should call for {@link TreeSpeedSearch#setupListeners()}
   * method to enable speed search
   */
  @Deprecated
  public TreeSpeedSearch(@NotNull JTree tree, boolean canExpand, @NotNull Function<? super TreePath, String> presentableStringFunction) {
    super(tree);
    setComparator(new SpeedSearchComparator(false, true));
    myPresentableStringFunction = presentableStringFunction;
    myCanExpand = canExpand;

    new MySelectAllAction(tree, this).registerCustomShortcutSet(tree, null);
  }

  /**
   * @deprecated Use {@link TreeUIHelper#installTreeSpeedSearch(JTree, Convertor, boolean)}
   * or the static method {@link TreeSpeedSearch#installOn(JTree, boolean, Function)} to install a speed search on tree.
   * The {@link TreeUIHelper#installTreeSpeedSearch(JTree, Convertor, boolean)} is preferable over the static call as it located in the API module
   * <p>
   * For inheritance use the non-deprecated constructor.
   * <p>
   * Also, note that non-deprecated constructor is side effect free, and you should call for {@link TreeSpeedSearch#setupListeners()}
   * method to enable speed search
   */
  @Deprecated
  public TreeSpeedSearch(JTree tree, Convertor<? super TreePath, String> toString) {
    this(tree, false, toString);
  }

  /**
   * @deprecated Use {@link TreeUIHelper#installTreeSpeedSearch(JTree, Convertor, boolean)}
   * or the static method {@link TreeSpeedSearch#installOn(JTree, boolean, Function)} to install a speed search on tree.
   * The {@link TreeUIHelper#installTreeSpeedSearch(JTree, Convertor, boolean)} is preferable over the static call as it located in the API module
   * <p>
   * For inheritance use the non-deprecated constructor.
   * <p>
   * Also, note that non-deprecated constructor is side effect free, and you should call for {@link TreeSpeedSearch#setupListeners()}
   * method to enable speed search
   */
  @Deprecated
  public TreeSpeedSearch(Tree tree, Convertor<? super TreePath, String> toString) {
    this(tree, false, toString);
  }

  /**
   * @deprecated Use {@link TreeUIHelper#installTreeSpeedSearch(JTree, Convertor, boolean)}
   * or the static method {@link TreeSpeedSearch#installOn(JTree, boolean, Function)} to install a speed search on tree.
   * The {@link TreeUIHelper#installTreeSpeedSearch(JTree, Convertor, boolean)} is preferable over the static call as it located in the API module
   * <p>
   * For inheritance use the non-deprecated constructor.
   * <p>
   * Also, note that non-deprecated constructor is side effect free, and you should call for {@link TreeSpeedSearch#setupListeners()}
   * method to enable speed search
   */
  @Deprecated
  public TreeSpeedSearch(Tree tree, Convertor<? super TreePath, String> toString, boolean canExpand) {
    this(tree, canExpand, toString);
  }

  /**
   * @deprecated Use {@link TreeUIHelper#installTreeSpeedSearch(JTree, Convertor, boolean)}
   * or the static method {@link TreeSpeedSearch#installOn(JTree, boolean, Function)} to install a speed search on tree.
   * The {@link TreeUIHelper#installTreeSpeedSearch(JTree, Convertor, boolean)} is preferable over the static call as it located in the API module
   * <p>
   * For inheritance use the non-deprecated constructor.
   * <p>
   * Also, note that non-deprecated constructor is side effect free, and you should call for {@link TreeSpeedSearch#setupListeners()}
   * method to enable speed search
   */
  @Deprecated
  public TreeSpeedSearch(JTree tree, Convertor<? super TreePath, String> toString, boolean canExpand) {
    this (tree, canExpand, toString);
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

  @Override
  protected final @NotNull ListIterator<Object> getElementIterator(int startingViewIndex) {
    return allPaths().addAllTo(new ArrayList<Object>()).listIterator(startingViewIndex);
  }

  @Override
  protected final int getElementCount() {
    return allPaths().size();
  }

  protected @NotNull JBIterable<TreePath> allPaths() {
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

  private @NotNull List<TreePath> findAllFilteredElements(String s) {
    List<TreePath> paths = new ArrayList<>();
    String _s = s.trim();

    ListIterator<Object> it = getElementIterator(0);
    while (it.hasNext()) {
      Object element = it.next();
      if (isMatchingElement(element, _s)) paths.add((TreePath)element);
    }
    return paths;
  }

  private static final class MySelectAllAction extends DumbAwareAction {
    private final @NotNull JTree myTree;
    private final @NotNull TreeSpeedSearch mySearch;

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
