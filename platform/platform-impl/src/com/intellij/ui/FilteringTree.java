// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.speedSearch.SpeedSearch;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ArrayUtil;
import com.intellij.util.EventDispatcher;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.JBIterator;
import com.intellij.util.containers.JBTreeTraverser;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
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
    @Nullable
    @Override
    public Iterable<TextRange> matchingFragments(@NotNull String text) { return null; }

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
  private final Project myProject;
  private final T myRoot;
  private final Tree myTree;

  public FilteringTree(@NotNull Project project, @NotNull Tree tree, @NotNull T root) {
    myProject = project;
    myRoot = root;
    myTree = tree;
    myTree.setModel(new SearchTreeModel<>(myRoot, DUMMY_SEARCH, o -> getText(o), this::createNode, this::getChildren));
    SwingUtilities.invokeLater(() -> rebuildTree());
  }

  @NotNull
  public SearchTextField installSearchField() {
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

  @NotNull
  protected SpeedSearchSupply createSpeedSearch(@NotNull SearchTextField searchTextField) {
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
    public boolean isMatching(@NotNull T node) {
      String text = getText(getUserObject(node));
      return text != null && matchingFragments(text) != null;
    }

    @Nullable
    @Override
    public T getSelection() {
      return ArrayUtil.getFirstElement(myTree.getSelectedNodes(getNodeClass(), null));
    }

    @NotNull
    @Override
    public Iterator<T> iterate(@Nullable T start, boolean fwd) {
      JBTreeTraverser<T> traverser = JBTreeTraverser.<T>from(n -> {
        int count = n.getChildCount();
        List<T> children = new ArrayList<>(count);
        for (int i = 0; i < count; ++i) {
          T c = ObjectUtils.tryCast(n.getChildAt(fwd ? i : count - i - 1), getNodeClass());
          if (c != null) children.add(c);
        }
        return children;
      }).expand(Conditions.alwaysTrue());
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
    SpeedSearchSupply supply = new TreeSpeedSearch(myTree, p -> StringUtil.notNullize(getText(p == null ? null : getUserObject((TreeNode)p.getLastPathComponent()))), true) {
      @Override
      protected void onSearchFieldUpdated(String pattern) {
        if (StringUtil.isEmpty(pattern)) hidePopup();
        //constructor of popup
        if (StringUtil.isNotEmpty(pattern) && !isPopupActive()) SwingUtilities.invokeLater(() -> {
          getSearchModel().refilter();
          if (StringUtil.isNotEmpty(pattern)) TreeUtil.expandAll(myTree);
        });
        else getSearchModel().refilter();
      }
    };
    getSearchModel().setSpeedSearch(supply);
  }

  protected abstract Class<? extends T> getNodeClass();

  @NotNull
  protected abstract T createNode(@NotNull U obj);

  @NotNull
  protected abstract Iterable<U> getChildren(@NotNull U obj);

  @NotNull
  public Tree getTree() {
    return myTree;
  }

  @NotNull
  public JComponent getComponent() {
    return myTree;
  }

  protected void rebuildTree() {
  }

  protected void expandTreeOnSearchUpdateComplete(@Nullable String pattern) {
    if (StringUtil.isNotEmpty(pattern)) TreeUtil.expandAll(myTree);
  }

  protected void onSpeedSearchUpdateComplete(@Nullable String pattern) {
  }

  @Nullable
  protected abstract String getText(@Nullable U object);

  @NotNull
  @SuppressWarnings("unchecked")
  public SearchTreeModel<T, U> getSearchModel() {
    return (SearchTreeModel)myTree.getModel();
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public T getRoot() {
    return myRoot;
  }

  public void update() {
    rebuildTree();
    myTree.revalidate();
    myTree.repaint();
  }

  public static class SearchTreeModel<N extends DefaultMutableTreeNode, U> extends DefaultTreeModel {
    public interface Listener<U> extends EventListener {
      void beforeNodeChanged(U x);
      void nodeChanged(U x);
    }
    private final Function<U, String> myNamer;
    private final Function<U, N> myFactory;
    private final U myRootObject;
    private final Function<U, Iterable<U>> myStructure;
    private SpeedSearchSupply mySpeedSearch;
    private Map<U, N> myNodeCache = new IdentityHashMap<>();
    @SuppressWarnings("unchecked")
    private final EventDispatcher<Listener<U>> myNodeChanged = (EventDispatcher)EventDispatcher.create(Listener.class);

    public SearchTreeModel(@NotNull N root, @NotNull SpeedSearchSupply speedSearch,
                           @NotNull Function<U, String> namer, @NotNull Function<U, N> nodeFactory,
                           @NotNull Function<U, Iterable<U>> structure) {
      super(root);
      myRootObject = Objects.requireNonNull(getUserObject(root));
      mySpeedSearch = speedSearch;
      myNamer = namer;
      myFactory = nodeFactory;
      myStructure = structure;
      addTreeModelListener(new TreeModelListener() {
        @Override
        @SuppressWarnings("unchecked")
        public void treeNodesChanged(TreeModelEvent e) {
          U object = getUserObject((N)e.getTreePath().getLastPathComponent());
          if (object != null) myNodeChanged.getMulticaster().nodeChanged(object);
        }

        @Override
        public void treeNodesInserted(TreeModelEvent e) {}

        @Override
        public void treeNodesRemoved(TreeModelEvent e) {}

        @Override
        public void treeStructureChanged(TreeModelEvent e) {}
      });
    }

    public void modifyNode(@NotNull U object, @NotNull Runnable r) {
      myNodeChanged.getMulticaster().beforeNodeChanged(object);
      try {
        r.run();
      }
      finally {
        myNodeChanged.getMulticaster().nodeChanged(object);
      }
    }

    public void setSpeedSearch(@NotNull SpeedSearchSupply supply) {
      mySpeedSearch = supply;
      updateStructure();
    }

    public SpeedSearchSupply getSpeedSearch() {
      return mySpeedSearch;
    }

    public void addNodeListener(@NotNull Listener<U> listener) {
      myNodeChanged.addListener(listener);
    }

    public void updateStructure() {
      Map<U, N> newNodes = new IdentityHashMap<>();
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

    @NotNull
    public U getRootObject() {
      return myRootObject;
    }

    @NotNull
    public N getNode(@NotNull U object) {
      N node = getCachedNode(object);
      if (node == null) myNodeCache.put(object, node = createNode(object));
      return node;
    }

    @Nullable
    public N getCachedNode(@Nullable U object) {
      if (object == null) return null;
      if (object == myRootObject) return getRoot();
      return myNodeCache.get(object);
    }

    @NotNull
    protected N createNode(@NotNull U object) {
      assert !(object instanceof DefaultMutableTreeNode);
      return myFactory.fun(object);
    }

    public void refilter() {
      if (mySpeedSearch.isPopupActive()) {
        Set<U> acceptCache = ContainerUtil.newIdentityTroveSet();
        computeAcceptCache(myRootObject, acceptCache);
        filterChildren(myRootObject, x -> acceptCache.contains(x));
      }
      else {
        filterChildren(myRootObject, x -> true);
      }
    }

    private boolean computeAcceptCache(@NotNull U object, @NotNull Set<U> cache) {
      boolean isAccepted = false;
      Iterable<U> children = getChildren(object);
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

    @NotNull
    public Iterable<U> getChildren(@Nullable U object) {
      return object == null ? JBIterable.empty() : myStructure.fun(object);
    }

    @NotNull
    public Function<U, Iterable<U>> getStructure() {
      return myStructure;
    }

    @Nullable
    private static <N extends DefaultMutableTreeNode> N getChildSafe(@NotNull N node, int i) {
      return node.getChildCount() <= i ? null : getChild(node, i);
    }

    @SuppressWarnings("unchecked")
    private static <N extends DefaultMutableTreeNode> N getChild(@NotNull N node, int i) {
      return (N)node.getChildAt(i);
    }

    private void filterChildren(@Nullable U object, @NotNull Condition<U> filter) {
      if (object == null) return;
      N node = getNode(object);
      filterDirectChildren(node, filter);

      for (int i = 0, c = node.getChildCount(); i < c; ++i) {
        filterChildren(getUserObject(getChild(node, i)), filter);
      }
    }

    private void filterDirectChildren(@NotNull N node, @NotNull Condition<U> filter) {
      Set<U> accepted = new LinkedHashSet<>();

      for (U child : getChildren(getUserObject(node))) {
        if (filter.value(child)) accepted.add(child);
      }

      removeNotAccepted(node, accepted);
      mergeAcceptedNodes(node, accepted);
    }

    private void mergeAcceptedNodes(@NotNull N node, Set<U> accepted) {
      int k = 0;
      N cur = getChildSafe(node, 0);
      TIntArrayList newIds = new TIntArrayList();
      for (U child : accepted) {
        boolean isCur = cur != null && getUserObject(cur) == child;
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
        nodesWereInserted(node, newIds.toNativeArray());
      }
      if (node.getChildCount() > k) {
        TIntArrayList leftIds = new TIntArrayList();
        List<N> leftNodes = new ArrayList<>();
        for (int i = node.getChildCount() - 1; i >= k; --i) {
          leftNodes.add(getChild(node, i));
          node.remove(i);
          leftIds.add(i);
        }
        leftIds.reverse();
        Collections.reverse(leftNodes);
        if (leftIds.size() > 0) {
          nodesWereRemoved(node, leftIds.toNativeArray(), leftNodes.toArray());
        }
      }
    }

    private void removeNotAccepted(@NotNull N node, Set<U> accepted) {
      TIntArrayList removedIds = new TIntArrayList();
      List<N> removedNodes = new ArrayList<>();
      for (int i = node.getChildCount() - 1; i >= 0; --i) {
        N child = getChild(node, i);
        if (!accepted.contains(getUserObject(child))) {
          removedIds.add(i);
          removedNodes.add(child);
          node.remove(i);
        }
      }
      removedIds.reverse();
      Collections.reverse(removedNodes);
      if (removedIds.size() > 0) {
        nodesWereRemoved(node, removedIds.toNativeArray(), removedNodes.toArray());
      }
    }

    protected boolean accept(@Nullable String name) {
      if (name == null) return true;
      return mySpeedSearch.matchingFragments(name) != null;
    }

    @Override
    public boolean isLeaf(Object node) {
      return getRoot() != node && super.isLeaf(node);
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public final U getUserObject(@Nullable N node) {
      return node == null ? null : (U)node.getUserObject();
    }
  }

  private static class MySpeedSearch<Item> extends SpeedSearch {
    private boolean myUpdating = false;
    private final JTextComponent myField;

    protected void onUpdatePattern(@Nullable String text) { }

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
      if (selection != null && isMatching(selection)) return;
      JBIterator<Item> it = JBIterator.from(iterate(selection, true, true))
        .filter(item -> item != selection && isMatching(item));
      if (!it.advance()) return;
      select(it.current());
    }

    protected void onSearchFieldUpdated(String pattern) {
    }

    public void select(@NotNull Item item) {
    }

    @Nullable
    public Item getSelection() {
      return null;
    }

    public boolean isMatching(@NotNull Item item) {
      return false;
    }

    @NotNull
    public Iterator<Item> iterate(@Nullable Item start, boolean fwd) {
      return new JBIterator<Item>() {
        @Override
        protected Item nextImpl() {
          return stop();
        }
      };
    }

    @NotNull
    public Iterator<Item> iterate(@Nullable Item start, boolean fwd, boolean wrap) {
      if (!wrap || start == null) return iterate(start, fwd);
      return new JBIterator<Item>() {
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
      it = JBIterator.from(it).filter(item -> isMatching(item));
      if (it.hasNext()) {
        select(it.next());
      }
      return true;
    }

  }

  @Nullable
  @SuppressWarnings("unchecked")
  public final U getUserObject(@Nullable TreeNode node) {
    return node == null || !getNodeClass().isAssignableFrom(node.getClass()) ? null : (U)((T)node).getUserObject();
  }
}
