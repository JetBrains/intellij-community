// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.speedSearch.SpeedSearch;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.JBIterator;
import com.intellij.util.containers.JBTreeTraverser;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.JTextComponent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.event.KeyAdapter;
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
    return new FilteringSpeedSearch(searchTextField);
  }

  protected class FilteringSpeedSearch extends MySpeedSearch<T> {

    protected FilteringSpeedSearch(@NotNull SearchTextField field) {
      super(myTree, field.getTextEditor());
    }

    @Override
    protected void onSearchFieldUpdated(String pattern) {
      TreePath[] paths = myTree.getSelectionModel().getSelectionPaths();
      getSearchModel().refilter();
      expandTreeOnSearchUpdateComplete(pattern);
      myTree.getSelectionModel().setSelectionPaths(paths);
      onSpeedSearchUpdateComplete(pattern);
    }

    @Override
    public void select(@NotNull T node) {
      TreeUtil.selectInTree(node, false, myTree);
    }

    @Override
    public @NotNull Matching checkMatching(@NotNull T node) {
      String text = getText(getUserObject(node));
      if (text == null) return Matching.NONE;

      Iterable<TextRange> matchingFragments = matchingFragments(text);
      if (matchingFragments == null) {
        return Matching.NONE;
      }

      TextRange onlyFragment = getOnlyElement(matchingFragments);
      if (onlyFragment != null && onlyFragment.getStartOffset() == 0) {
        return Matching.FULL;
      }
      else {
        return Matching.PARTIAL;
      }
    }

    private static <T> @Nullable T getOnlyElement(@NotNull Iterable<T> iterable) {
      Iterator<T> it = iterable.iterator();
      if (!it.hasNext()) return null;
      T firstValue = it.next();
      return it.hasNext() ? null : firstValue;
    }

    @Override
    public @Nullable T getSelection() {
      return ArrayUtil.getFirstElement(myTree.getSelectedNodes(getNodeClass(), null));
    }

    @Override
    public @NotNull Iterator<T> iterate(@Nullable T start, boolean fwd) {
      JBTreeTraverser<T> traverser = JBTreeTraverser.from(n -> {
        int count = n.getChildCount();
        List<T> children = new ArrayList<>(count);
        for (int i = 0; i < count; ++i) {
          T c = ObjectUtils.tryCast(n.getChildAt(fwd ? i : count - i - 1), getNodeClass());
          if (c != null) children.add(c);
        }
        return children;
      });
      if (start == null) {
        traverser = traverser.withRoot(getRoot());
      }
      else {
        List<T> roots = new ArrayList<>();
        for (TreeNode node = null, parent = start; parent != null; node = parent, parent = node.getParent()) {
          int idx = node == null ? -1 : parent.getIndex(node);
          for (int i = fwd ? idx + 1 : 0, c = fwd ? parent.getChildCount() : idx; i < c; ++i) {
            T child = ObjectUtils.tryCast(parent.getChildAt(fwd ? i : idx - i - 1), getNodeClass());
            if (child != null) roots.add(child);
          }
        }
        traverser = traverser.withRoots(roots);
      }
      return traverser.preOrderDfsTraversal().iterator();
    }
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
      List<N> oldNodes = new ArrayList<>();
      for (Map.Entry<U, N> entry : myNodeCache.entrySet()) {
        if (!newNodes.containsKey(entry.getKey())) oldNodes.add(entry.getValue());
      }
      myNodeCache = newNodes;
      for (N node : oldNodes) {
        if (node.getParent() != null) removeNodeFromParent(node);
      }
      refilter();
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

    @NotNull
    private N createNode(@NotNull U object) {
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
      return myUseIdentityHashing ? new IdentityHashMap<>() : new HashMap<>();
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
      String name = myNamer.fun(object);
      isAccepted |= object == myRootObject || name != null && accept(name);
      if (isAccepted) {
        for (U child : children) {
          if (myNamer.fun(child) == null) cache.add(child);
        }
        cache.add(object);
      }
      return isAccepted;
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
      if (newIds.size() > 0) {
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
        if (leftIds.size() > 0) {
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

    private boolean accept(@Nullable String name) {
      if (name == null) return true;
      return mySpeedSearch.matchingFragments(name) != null;
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

  private abstract static class MySpeedSearch<Item> extends SpeedSearch {
    private boolean myUpdating = false;
    private final JTextComponent myField;

    MySpeedSearch(@NotNull JComponent comp, @NotNull JTextComponent field) {
      myField = field;
      myField.getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        protected void textChanged(@NotNull DocumentEvent e) {
          if (!myUpdating) {
            myUpdating = true;
            try {
              String text = myField.getText();
              updatePattern(text);
              onUpdatePattern(text);
              update();
            }
            finally {
              myUpdating = false;
            }
          }
        }
      });
      setEnabled(true);
      comp.addKeyListener(new KeyAdapter() {
        @Override
        public void keyPressed(KeyEvent e) {
          if (selectTargetElement(e.getKeyCode())) {
            e.consume();
          }
        }
      });
      comp.addKeyListener(this);
      installSupplyTo(comp);
    }

    @Override
    public void update() {
      String filter = getFilter();
      if (!myUpdating) {
        myUpdating = true;
        try {
          myField.setText(filter);
        }
        finally {
          myUpdating = false;
        }
      }
      onSearchFieldUpdated(filter);
      updateSelection();
    }

    @Override
    public void noHits() {
      myField.setBackground(LightColors.RED);
    }

    public void updateSelection() {
      Item selection = getSelection();

      Matching currentMatching = selection != null ? checkMatching(selection) : Matching.NONE;
      if (currentMatching == Matching.FULL) {
        return;
      }

      Item fullMatch = findNextMatchingNode(selection, true);
      if (fullMatch != null) {
        select(fullMatch);
      }
      else if (currentMatching == Matching.NONE) {
        Item partialMatch = findNextMatchingNode(selection, false);
        if (partialMatch != null) {
          select(partialMatch);
        }
      }
    }

    private @Nullable Item findNextMatchingNode(@Nullable Item selection, boolean fullMatch) {
      JBIterator<Item> allNodeIterator = JBIterator.from(iterate(selection, true, true));
      JBIterator<Item> fullMatches = filterMatchingNodes(allNodeIterator, fullMatch)
        .filter(item -> item != selection);
      if (fullMatches.advance()) {
        return fullMatches.current();
      }
      return null;
    }

    @NotNull
    private JBIterator<Item> filterMatchingNodes(JBIterator<Item> nodes, boolean fullMatch) {
      return nodes.filter(item -> {
        Matching matching = checkMatching(item);
        return fullMatch ? matching == Matching.FULL : matching != Matching.NONE;
      });
    }

    protected void onUpdatePattern(@Nullable String text) { }

    protected void onSearchFieldUpdated(String pattern) { }

    public abstract void select(@NotNull Item item);

    public abstract @Nullable Item getSelection();

    public abstract @NotNull Matching checkMatching(@NotNull Item item);

    public abstract @NotNull Iterator<Item> iterate(@Nullable Item start, boolean fwd);

    public @NotNull Iterator<Item> iterate(@Nullable Item start, boolean fwd, boolean wrap) {
      if (!wrap || start == null) return iterate(start, fwd);
      return new JBIterator<>() {
        boolean wrapped = false;
        Iterator<Item> it = iterate(start, fwd);

        @Override
        protected Item nextImpl() {
          if (it.hasNext()) return it.next();
          if (wrapped) return stop();
          wrapped = true;
          it = JBIterator.from(iterate(null, fwd)).takeWhile(item -> item != start);
          return it.hasNext() ? it.next() : stop();
        }
      };
    }

    private boolean selectTargetElement(int keyCode) {
      if (!isPopupActive()) return false;
      Iterator<Item> it;
      if (keyCode == KeyEvent.VK_UP) {
        it = iterate(getSelection(), false, UISettings.getInstance().getCycleScrolling());
      }
      else if (keyCode == KeyEvent.VK_DOWN) {
        it = iterate(getSelection(), true, UISettings.getInstance().getCycleScrolling());
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
  }

  @SuppressWarnings("unchecked")
  public final @Nullable U getUserObject(@Nullable TreeNode node) {
    return node == null || !getNodeClass().isAssignableFrom(node.getClass()) ? null : (U)((T)node).getUserObject();
  }

  public enum Matching {NONE, PARTIAL, FULL}
}
