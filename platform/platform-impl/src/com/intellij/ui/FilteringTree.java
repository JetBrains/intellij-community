// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Function;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.JBTreeTraverser;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeListener;
import java.util.*;

public abstract class FilteringTree<T extends DefaultMutableTreeNode, U> {
  public static final SpeedSearchSupply DUMMY_SEARCH = new SpeedSearchSupply() {
    @Override
    public @Nullable Iterable<TextRange> matchingFragments(@NotNull String text) { return null; }

    @Override
    public void refreshSelection() { }

    @Override
    public boolean isPopupActive() { return false; }

    @Override
    public void addChangeListener(@NotNull PropertyChangeListener listener) { }

    @Override
    public void removeChangeListener(@NotNull PropertyChangeListener listener) { }

    @Override
    public void findAndSelectElement(@NotNull String searchQuery) { }
  };
  private final T myRoot;
  private final Tree myTree;

  public FilteringTree(@NotNull Tree tree, @NotNull T root) {
    myRoot = root;
    myTree = tree;
    myTree.setModel(new SearchTreeModel<>(myRoot, DUMMY_SEARCH, o -> getText(o), this::createNode, this::getChildren,
                                          useIdentityHashing()));
  }

  public @NotNull SearchTextField installSearchField() {
    SearchTextField field = new SearchTextField(false) {
      @Override
      protected boolean preprocessEventForTextField(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_DOWN
            || e.getKeyCode() == KeyEvent.VK_UP) {
          myTree.dispatchEvent(e);
          return true;
        }
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE && getText().isEmpty()) {
          UIUtil.requestFocus(myTree);
          return true;
        }
        return false;
      }
    };

    getSearchModel().setSpeedSearch(createSpeedSearch(field));
    return field;
  }

  protected @NotNull SpeedSearchSupply createSpeedSearch(@NotNull SearchTextField searchTextField) {
    return new FilteringSpeedSearch<T, U>(this, searchTextField);
  }

  public void installSimple() {
    TreeSpeedSearch supply =
      new TreeSpeedSearch(myTree,
                          true,
                          null,
                          p -> StringUtil.notNullize(getText(p == null ? null : getUserObject((TreeNode)p.getLastPathComponent())))) {
        @Override
        protected void onSearchFieldUpdated(String pattern) {
          super.onSearchFieldUpdated(pattern);
          //constructor of popup
          if (StringUtil.isNotEmpty(pattern) && !isPopupActive()) {
            SwingUtilities.invokeLater(() -> {
              getSearchModel().refilter();
              if (StringUtil.isNotEmpty(pattern)) TreeUtil.expandAll(myTree);
            });
          }
          else {
            getSearchModel().refilter();
          }
        }
      };
    supply.setupListeners();
    getSearchModel().setSpeedSearch(supply);
  }

  /**
   * Used to simplify implementation.
   * Returning anything but T is not adviced and might not work properly.
   */
  protected abstract Class<? extends T> getNodeClass();

  protected abstract @NotNull T createNode(@NotNull U obj);

  protected abstract @NotNull Iterable<U> getChildren(@NotNull U obj);

  public @NotNull Tree getTree() {
    return myTree;
  }

  public @NotNull JComponent getComponent() {
    return myTree;
  }

  protected void expandTreeOnSearchUpdateComplete(@Nullable String pattern) {
    if (StringUtil.isNotEmpty(pattern)) TreeUtil.expandAll(myTree);
  }

  protected void onSpeedSearchUpdateComplete(@Nullable String pattern) {
  }

  /**
   * Comparison method to be used for {@link U}.
   * <p>
   * Note: Tree CAN NOT contain multiple equal elements, even if they do not share the same parent.
   */
  protected boolean useIdentityHashing() {
    return true;
  }

  protected abstract @Nullable String getText(@Nullable U object);

  @SuppressWarnings("unchecked")
  public @NotNull SearchTreeModel<T, U> getSearchModel() {
    return (SearchTreeModel)myTree.getModel();
  }

  public @NotNull T getRoot() {
    return myRoot;
  }

  public static final class SearchTreeModel<N extends DefaultMutableTreeNode, U> extends DefaultTreeModel {
    private final @NotNull Function<? super U, String> myNamer;
    private final @NotNull Function<? super U, ? extends N> myFactory;
    private final U myRootObject;
    private final Function<? super U, ? extends Iterable<? extends U>> myStructure;
    private final boolean myUseIdentityHashing;
    private SpeedSearchSupply mySpeedSearch;
    private Map<U, N> myNodeCache;

    public SearchTreeModel(@NotNull N root, @NotNull SpeedSearchSupply speedSearch,
                           @NotNull Function<? super U, String> namer, @NotNull Function<? super U, ? extends N> nodeFactory,
                           @NotNull Function<? super U, ? extends Iterable<? extends U>> structure, boolean useIdentityHashing) {
      super(root);
      myRootObject = Objects.requireNonNull(getUserObject(root));
      mySpeedSearch = speedSearch;
      myNamer = namer;
      myFactory = nodeFactory;
      myStructure = structure;
      myUseIdentityHashing = useIdentityHashing;
      myNodeCache = createUserObjectMap();
    }

    public void setSpeedSearch(@NotNull SpeedSearchSupply supply) {
      mySpeedSearch = supply;
      updateStructure();
    }

    public SpeedSearchSupply getSpeedSearch() {
      return mySpeedSearch;
    }

    /**
     * Rebuild tree on non-filtered tree structure is changes
     */
    @RequiresEdt
    public void updateStructure() {
      Map<U, N> newNodes = createUserObjectMap();
      for (U node : JBTreeTraverser.from(myStructure).withRoot(getRootObject())) {
        N treeNode = myNodeCache.get(node);
        newNodes.put(node, treeNode == null ? createNode(node) : treeNode);
      }
      Set<N> nodesToRemove = getNodesToRemove(newNodes);
      myNodeCache = newNodes;
      for (N node : nodesToRemove) {
        if (node.getParent() != null) removeNodeFromParent(node);
      }
      refilter();
    }

    private @NotNull Set<N> getNodesToRemove(Map<U, N> newNodes) {
      Set<N> nodesToRemove = new ReferenceOpenHashSet<>();
      for (Map.Entry<U, N> entry : myNodeCache.entrySet()) {
        if (!newNodes.containsKey(entry.getKey())) {
          N node = entry.getValue();
          boolean shouldRemove = true;
          for (TreeNode treeNode : node.getPath()) {
            if (nodesToRemove.contains(treeNode)) {
              shouldRemove = false;
              break;
            }
          }
          if (shouldRemove) {
            nodesToRemove.add(node);
          }
        }
      }
      return nodesToRemove;
    }

    @Override
    @SuppressWarnings("unchecked")
    public N getRoot() {
      return (N)root;
    }

    public @NotNull U getRootObject() {
      return myRootObject;
    }

    public @NotNull N getNode(@NotNull U object) {
      N node = getCachedNode(object);
      if (node == null) myNodeCache.put(object, node = createNode(object));
      return node;
    }

    public @Nullable N getCachedNode(@Nullable U object) {
      if (object == null) return null;
      if (object == myRootObject) return getRoot();
      return myNodeCache.get(object);
    }

    private @NotNull N createNode(@NotNull U object) {
      assert !(object instanceof DefaultMutableTreeNode);
      return myFactory.fun(object);
    }

    /**
     * Rebuild tree on filter changes
     */
    @RequiresEdt
    public void refilter() {
      if (mySpeedSearch.isPopupActive()) {
        Set<U> acceptCache = createUserObjectSet();
        computeAcceptCache(myRootObject, acceptCache);
        filterChildren(myRootObject, x -> acceptCache.contains(x));
      }
      else {
        filterChildren(myRootObject, x -> true);
      }
    }

    private @NotNull Set<U> createUserObjectSet() {
      return myUseIdentityHashing ? new ReferenceOpenHashSet<>() : new HashSet<>();
    }

    private @NotNull Map<U, N> createUserObjectMap() {
      return myUseIdentityHashing ? new Reference2ObjectLinkedOpenHashMap<>() : new LinkedHashMap<>();
    }

    private boolean equalUserObjects(@Nullable U u1, @Nullable U u2) {
      return myUseIdentityHashing ? u1 == u2 : Objects.equals(u1, u2);
    }

    private boolean computeAcceptCache(@NotNull U object, @NotNull Set<? super U> cache) {
      boolean isAccepted = false;
      Iterable<? extends U> children = getChildren(object);
      for (U child : children) {
        isAccepted |= computeAcceptCache(child, cache);
      }
      isAccepted = isAccepted || object == myRootObject || accept(object);
      if (isAccepted) {
        for (U child : children) {
          if (myNamer.fun(child) == null) cache.add(child);
        }
        cache.add(object);
      }
      return isAccepted;
    }

    private boolean accept(@NotNull U object) {
      String name = myNamer.fun(object);
      if (name == null) return false;

      Iterable<TextRange> matchingFragments = mySpeedSearch.matchingFragments(name);
      if (mySpeedSearch instanceof FilteringTreeUserObjectMatcher filteringTreeSpeedSearch) {
        //noinspection unchecked
        return filteringTreeSpeedSearch.checkMatching(object, matchingFragments) != Matching.NONE;
      } else {
        return matchingFragments != null;
      }
    }

    public @NotNull Iterable<? extends U> getChildren(@Nullable U object) {
      return object == null ? JBIterable.empty() : myStructure.fun(object);
    }

    public @NotNull Function<? super U, ? extends Iterable<? extends U>> getStructure() {
      return myStructure;
    }

    private static @Nullable <N extends DefaultMutableTreeNode> N getChildSafe(@NotNull N node, int i) {
      return node.getChildCount() <= i ? null : getChild(node, i);
    }

    @SuppressWarnings("unchecked")
    private static <N extends DefaultMutableTreeNode> N getChild(@NotNull N node, int i) {
      return (N)node.getChildAt(i);
    }

    private void filterChildren(@Nullable U object, @NotNull Condition<? super U> filter) {
      if (object == null) return;
      N node = getNode(object);
      filterDirectChildren(node, filter);

      for (int i = 0, c = node.getChildCount(); i < c; ++i) {
        filterChildren(getUserObject(getChild(node, i)), filter);
      }
    }

    private void filterDirectChildren(@NotNull N node, @NotNull Condition<? super U> filter) {
      Set<U> acceptedSet = createUserObjectSet();
      List<U> acceptedList = new ArrayList<>();

      for (U child : getChildren(getUserObject(node))) {
        if (filter.value(child)) {
          acceptedSet.add(child);
          acceptedList.add(child);
        }
      }

      if (acceptedList.size() != acceptedSet.size()) {
        throw new AssertionError("Duplicate nodes will cause failure");
      }
      removeNotAccepted(node, acceptedSet);
      mergeAcceptedNodes(node, acceptedList);
    }

    private void mergeAcceptedNodes(@NotNull N node, List<? extends U> accepted) {
      int k = 0;
      N cur = getChildSafe(node, 0);
      IntList newIds = new IntArrayList();
      for (U child : accepted) {
        U curUsrObject = getUserObject(cur);
        boolean isCur = cur != null && equalUserObjects(child, curUsrObject);
        if (isCur) {
          cur = getChildSafe(node, k + 1);
        }
        else {
          newIds.add(k);
          node.insert(getNode(child), k);
        }
        ++k;
      }
      if (!newIds.isEmpty()) {
        nodesWereInserted(node, newIds.toIntArray());
      }
      if (node.getChildCount() > k) {
        IntList leftIds = new IntArrayList();
        List<N> leftNodes = new ArrayList<>();
        for (int i = node.getChildCount() - 1; i >= k; --i) {
          leftNodes.add(getChild(node, i));
          node.remove(i);
          leftIds.add(i);
        }
        if (!leftIds.isEmpty()) {
          int[] ints = leftIds.toIntArray();
          for (int i = 0; i < ints.length; i++) {
            int temp = ints[i];
            ints[i] = ints[ints.length - i - 1];
            ints[ints.length - i - 1] = temp;
          }
          Collections.reverse(leftNodes);
          nodesWereRemoved(node, ints, leftNodes.toArray());
        }
      }
    }

    private void removeNotAccepted(@NotNull N node, Set<U> accepted) {
      IntList removedIds = new IntArrayList();
      List<N> removedNodes = new ArrayList<>();
      for (int i = node.getChildCount() - 1; i >= 0; --i) {
        N child = getChild(node, i);
        if (!accepted.contains(getUserObject(child))) {
          removedIds.add(i);
          removedNodes.add(child);
          node.remove(i);
        }
      }
      if (!removedIds.isEmpty()) {
        Collections.reverse(removedNodes);
        int[] ints = removedIds.toIntArray();
        for (int i = 0; i < ints.length / 2; i++) {
          int temp = ints[i];
          ints[i] = ints[ints.length - i - 1];
          ints[ints.length - i - 1] = temp;
        }
        nodesWereRemoved(node, ints, removedNodes.toArray());
      }
    }

    @Override
    public boolean isLeaf(Object node) {
      return getRoot() != node && super.isLeaf(node);
    }

    @SuppressWarnings("unchecked")
    public @Nullable U getUserObject(@Nullable N node) {
      return node == null ? null : (U)node.getUserObject();
    }
  }

  @SuppressWarnings("unchecked")
  public final @Nullable U getUserObject(@Nullable TreeNode node) {
    return node == null || !getNodeClass().isAssignableFrom(node.getClass()) ? null : (U)((T)node).getUserObject();
  }

  public enum Matching {NONE, PARTIAL, FULL}

  protected interface FilteringTreeUserObjectMatcher<U> {
    @NotNull Matching checkMatching(@NotNull U userObject, @Nullable Iterable<TextRange> matchingFragments);
  }
}
