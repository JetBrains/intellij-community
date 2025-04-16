// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.util.TextRange;
import com.intellij.ui.speedSearch.SpeedSearch;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.HashingStrategy;
import com.intellij.util.containers.JBIterator;
import com.intellij.util.containers.JBTreeTraverser;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import javax.swing.event.DocumentEvent;
import javax.swing.text.JTextComponent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class FilteringSpeedSearch<T extends DefaultMutableTreeNode, U> extends SpeedSearch implements FilteringTree.FilteringTreeUserObjectMatcher<U> {
  private final JTextComponent myField;
  private final FilteringTree<T, U> myFilteringTree;

  protected FilteringSpeedSearch(@NotNull FilteringTree<T, U> filteringTree, @NotNull SearchTextField field) {
    myFilteringTree = filteringTree;
    myField = field.getTextEditor();
    myField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        String text = myField.getText();
        updatePattern(text);
        onSearchPatternUpdated(text);
      }
    });
    setEnabled(true);
    getTreeComponent().addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (selectTargetElement(e.getKeyCode())) {
          e.consume();
        }
      }
    });
    getTreeComponent().addKeyListener(this);
    installSupplyTo(getTreeComponent());
  }

  public void select(@NotNull T node) {
    TreeUtil.selectInTree(node, false, getTreeComponent());
  }

  public @NotNull FilteringTree.Matching checkMatching(@NotNull T node) {
    U userObject = myFilteringTree.getUserObject(node);
    if (userObject == null) return FilteringTree.Matching.NONE;

    String text = myFilteringTree.getText(userObject);
    if (text == null) return FilteringTree.Matching.NONE;

    Iterable<TextRange> matchingFragments = matchingFragments(text);
    return checkMatching(userObject, matchingFragments);
  }


  @Override
  public final @NotNull FilteringTree.Matching checkMatching(@NotNull U userObject, @Nullable Iterable<TextRange> matchingFragments) {
    FilteringTree.Matching result;
    if (matchingFragments == null) {
      result = FilteringTree.Matching.NONE;
    } else {
      TextRange onlyFragment = getOnlyElement(matchingFragments);
      result = onlyFragment != null && onlyFragment.getStartOffset() == 0 ? FilteringTree.Matching.FULL : FilteringTree.Matching.PARTIAL;
    }
    onMatchingChecked(userObject, matchingFragments, result);

    return result;
  }

  protected void onMatchingChecked(@NotNull U userObject, @Nullable Iterable<TextRange> matchingFragments, @NotNull FilteringTree.Matching result) {
  }

  @Override
  public void update() {
    String filter = getFilter();
    myField.setText(filter);
  }

  protected void onSearchPatternUpdated(@Nullable String pattern) {
    refilter(pattern);
  }

  @VisibleForTesting
  public void refilter(@Nullable String pattern) {
    TreePath[] paths = getTreeComponent().getSelectionModel().getSelectionPaths();
    myFilteringTree.getSearchModel().refilter();
    myFilteringTree.expandTreeOnSearchUpdateComplete(pattern);
    getTreeComponent().getSelectionModel().setSelectionPaths(paths);
    myFilteringTree.onSpeedSearchUpdateComplete(pattern);
    updateSelection();
  }

  @Override
  public void noHits() {
    myField.setBackground(LightColors.RED);
  }

  public void updateSelection() {
    T selection = getSelection();

    FilteringTree.Matching currentMatching = selection != null ? checkMatching(selection) : FilteringTree.Matching.NONE;
    if (currentMatching == FilteringTree.Matching.FULL) {
      return;
    }

    T fullMatch = findNextMatchingNode(selection, true);
    if (fullMatch != null) {
      select(fullMatch);
    }
    else if (currentMatching == FilteringTree.Matching.NONE) {
      T partialMatch = findNextMatchingNode(selection, false);
      if (partialMatch != null) {
        select(partialMatch);
      }
    }
  }

  private T findNextMatchingNode(T selection, boolean fullMatch) {
    JBIterator<T> allNodeIterator = JBIterator.from(iterate(selection, true, true));
    JBIterator<T> fullMatches = filterMatchingNodes(allNodeIterator, fullMatch)
      .filter(item -> item != selection);
    if (fullMatches.advance()) {
      return fullMatches.current();
    }
    return null;
  }

  private @NotNull JBIterator<T> filterMatchingNodes(JBIterator<T> nodes, boolean fullMatch) {
    return nodes.filter(item -> {
      FilteringTree.Matching matching = checkMatching(item);
      return fullMatch ? matching == FilteringTree.Matching.FULL : matching != FilteringTree.Matching.NONE;
    });
  }

  public @NotNull Iterator<T> iterate(T start, boolean fwd, boolean wrap) {
    if (!wrap || start == null) return iterate(start, fwd);
    return new JBIterator<>() {
      boolean wrapped = false;
      Iterator<T> it = iterate(start, fwd);

      @Override
      protected T nextImpl() {
        if (it.hasNext()) return it.next();
        if (wrapped) return stop();
        wrapped = true;
        it = from(iterate(null, fwd)).takeWhile(item -> item != start);
        return it.hasNext() ? it.next() : stop();
      }
    };
  }

  private boolean selectTargetElement(int keyCode) {
    if (!isPopupActive()) return false;
    Iterator<T> it;
    if (keyCode == KeyEvent.VK_UP) {
      it = iterate(getSelection(), false, TreeUtil.isCyclicScrollingAllowed());
    }
    else if (keyCode == KeyEvent.VK_DOWN) {
      it = iterate(getSelection(), true, TreeUtil.isCyclicScrollingAllowed());
    }
    else if (keyCode == KeyEvent.VK_HOME) {
      it = iterate(null, true);
    }
    else if (keyCode == KeyEvent.VK_END) {
      it = iterate(null, false);
    }
    else {
      return false;
    }
    it = filterMatchingNodes(JBIterator.from(it), false);
    if (it.hasNext()) {
      select(it.next());
    }
    return true;
  }

  private static <T> @Nullable T getOnlyElement(@NotNull Iterable<T> iterable) {
    Iterator<T> it = iterable.iterator();
    if (!it.hasNext()) return null;
    T firstValue = it.next();
    return it.hasNext() ? null : firstValue;
  }

  public @Nullable T getSelection() {
    return ArrayUtil.getFirstElement(getTreeComponent().getSelectedNodes(myFilteringTree.getNodeClass(), null));
  }

  public @NotNull Iterator<T> iterate(@Nullable T start, boolean fwd) {
    Set<TreeNode> rootPath = CollectionFactory.createCustomHashingStrategySet(HashingStrategy.identity());
    for (TreeNode node = start; node != null; node = node.getParent()) {
      rootPath.add(node);
    }
    // Lazy traversal. Skip preceding nodes for a forward flag and following nodes for the opposite.
    JBTreeTraverser<T> traverser = JBTreeTraverser.from(n -> {
      boolean onRootPath = rootPath.contains(n);
      int count = n.getChildCount();
      List<T> children = new ArrayList<>(count);
      boolean skip = onRootPath && n != start;
      for (int i = 0; i < count; ++i) {
        T c = ObjectUtils.tryCast(n.getChildAt(fwd ? i : count - i - 1), myFilteringTree.getNodeClass());
        if (skip && rootPath.contains(c)) skip = false;
        if (c != null && !skip) children.add(c);
      }
      return children;
    });

    traverser = traverser.withRoots(myFilteringTree.getRoot());
    if (start == null) return traverser.preOrderDfsTraversal().iterator();
    if (!fwd) return traverser.postOrderDfsTraversal().skipWhile(n -> n != start).skip(1).iterator();
    return traverser.preOrderDfsTraversal().skipWhile(n -> n != start).skip(1).iterator();
  }

  private @NotNull Tree getTreeComponent() {
    return myFilteringTree.getTree();
  }
}
