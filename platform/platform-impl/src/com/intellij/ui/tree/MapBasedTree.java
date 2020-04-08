// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.TreeModelEvent;
import javax.swing.tree.TreePath;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.Collections.*;

public final class MapBasedTree<K, N> {
  private static final Logger LOG = Logger.getInstance(MapBasedTree.class);

  private final Map<K, Entry<N>> map;
  private final Function<? super N, ? extends K> keyFunction;
  private final TreePath path;
  private volatile Entry<N> root;
  private volatile Consumer<? super N> nodeRemoved;
  private volatile Consumer<? super N> nodeInserted;

  public MapBasedTree(boolean identity, @NotNull Function<? super N, ? extends K> keyFunction) {
    this(identity, keyFunction, null);
  }

  public MapBasedTree(boolean identity, @NotNull Function<? super N, ? extends K> keyFunction, TreePath path) {
    map = identity ? new IdentityHashMap<>() : new HashMap<>();
    this.keyFunction = keyFunction;
    this.path = path;
  }

  public void invalidate() {
    if (root != null) root.invalidate();
    map.values().forEach(entry -> entry.invalidate());
  }

  public void onRemove(@NotNull Consumer<? super N> consumer) {
    Consumer old = nodeRemoved;
    nodeRemoved = old == null ? consumer : old.andThen(consumer);
  }

  public void onInsert(@NotNull Consumer<? super N> consumer) {
    Consumer old = nodeInserted;
    nodeInserted = old == null ? consumer : old.andThen(consumer);
  }

  public Entry<N> findEntry(K key) {
    return key == null ? null : map.get(key);
  }

  public N findNode(K key) {
    Entry<N> entry = findEntry(key);
    return entry == null ? null : entry.node;
  }

  public Entry<N> getEntry(N node) {
    K key = getKey(node);
    Entry<N> entry = findEntry(key);
    return entry == null || entry.node == node ? entry : null;
  }

  public Entry<N> getRootEntry() {
    return root;
  }

  public K getKey(N node) {
    if (node == null) return null;
    K key = keyFunction.apply(node);
    if (key != null) return key;
    LOG.warn("MapBasedTree: key function provides null");
    return null;
  }

  public boolean updateRoot(Pair<? extends N, Boolean> pair) {
    N node = Pair.getFirst(pair);
    if (root == null ? node == null : root.node == node) return false;

    if (root != null) {
      remove(root, keyFunction.apply(root.node));
      root = null;
    }
    if (!map.isEmpty()) {
      map.clear();
      LOG.warn("MapBasedTree: clear lost entries");
    }
    if (node != null) {
      root = new Entry<>(path, null, node, pair.second);
      insert(root, keyFunction.apply(node));
    }
    return true;
  }

  public UpdateResult<N> update(@NotNull Entry<N> parent, List<? extends Pair<N, Boolean>> children) {
    List<Entry<N>> newChildren = new ArrayList<>(children == null ? 0 : children.size());
    List<Entry<N>> oldChildren = parent.children;
    Map<Entry<N>, K> mapInserted = new IdentityHashMap<>();
    Map<Entry<N>, K> mapContained = new IdentityHashMap<>();

    if (children != null && !children.isEmpty()) {
      children.forEach(pair -> {
        if (pair == null || pair.first == null) {
          LOG.warn("MapBasedTree: ignore null node");
          return;
        }
        K key = getKey(pair.first);
        if (key == null) return;

        Entry<N> entry = findEntry(key);
        if (entry == null) {
          entry = new Entry<>(parent, parent.node, pair.first, pair.second);
          mapInserted.put(entry, key);
        }
        else if (parent != entry.getParentPath()) {
          LOG.warn("MapBasedTree: ignore node that belongs to another parent");
          return;
        }
        else {
          mapContained.put(entry, key);
        }
        entry.index = newChildren.size();
        newChildren.add(entry);
      });
    }
    parent.leaf = children == null;
    parent.children = guard(newChildren);
    parent.valid = true;

    List<Entry<N>> removed = oldChildren;
    List<Entry<N>> inserted = newChildren;
    List<Entry<N>> contained = null;
    if (!mapContained.isEmpty()) {
      if (oldChildren == null) {
        oldChildren = emptyList();
        LOG.warn("MapBasedTree: unexpected state");
      }
      removed = ContainerUtil.filter(oldChildren, entry -> !mapContained.containsKey(entry));
      inserted = ContainerUtil.filter(newChildren, entry -> !mapContained.containsKey(entry));
      contained = ContainerUtil.filter(newChildren, entry -> mapContained.containsKey(entry));
    }
    removeChildren(parent, removed);
    mapInserted.forEach(this::insert);
    return new UpdateResult<>(removed, inserted, contained);
  }

  private void removeChildren(Entry<N> parent, List<Entry<N>> children) {
    if (children != null) {
      for (Entry<N> entry : children) {
        if (parent.loading == entry.node) {
          parent.loading = null;
        }
        else {
          remove(entry, getKey(entry.node));
        }
      }
    }
  }

  private void remove(Entry<N> entry, K key) {
    if (key != null) {
      Entry<N> removed = map.remove(key);
      if (removed == null) {
        LOG.warn("MapBasedTree: expected entry is not found");
      }
      else if (removed != entry) {
        LOG.warn("MapBasedTree: do not remove unexpected entry");
        map.put(key, removed);
        return;
      }
    }
    removeChildren(entry, entry.children);
    Consumer<? super N> consumer = nodeRemoved;
    if (consumer != null) consumer.accept(entry.node);
  }

  private void insert(Entry<N> entry, K key) {
    if (key != null) {
      Entry<N> removed = map.put(key, entry);
      if (removed != null) {
        LOG.warn("MapBasedTree: do not replace unexpected entry");
        map.put(key, removed);
        return;
      }
    }
    Consumer<? super N> consumer = nodeInserted;
    if (consumer != null) consumer.accept(entry.node);
  }

  private static <T> List<T> guard(List<? extends T> list) {
    return list == null || list.isEmpty() ? emptyList() : unmodifiableList(list);
  }

  public static final class Entry<N> extends TreePath {
    private final N node;
    private final N parent;
    private volatile int index;
    private volatile boolean leaf;
    private volatile List<Entry<N>> children;
    private volatile N loading;
    private volatile boolean valid;

    private Entry(TreePath path, N parent, N node, Boolean leaf) {
      super(path, node);
      this.node = node;
      this.parent = parent;
      this.leaf = Boolean.TRUE.equals(leaf);
      if (this.leaf) children = emptyList();
      invalidate();
    }

    public void invalidate() {
      valid = leaf;
    }

    public N getNode() {
      return node;
    }

    public N getParent() {
      return parent;
    }

    public boolean isLeaf() {
      return leaf;
    }

    public boolean isLoadingRequired() {
      return !valid || children == null;
    }

    public int getChildCount() {
      return children == null ? 0 : children.size();
    }

    public Entry<N> getChildEntry(int index) {
      if (children != null && 0 <= index && index < children.size()) {
        return children.get(index);
      }
      return null;
    }

    public N getChild(int index) {
      Entry<N> entry = getChildEntry(index);
      return entry == null ? null : entry.getNode();
    }

    public int getIndexOf(N child) {
      if (children != null) {
        for (int i = 0; i < children.size(); i++) {
          if (child == children.get(i).getNode()) return i;
        }
      }
      return -1;
    }

    void setLoadingChildren(N loading) {
      if (children != null) LOG.warn("MapBasedTree: rewrite loaded nodes");
      this.loading = loading;
      children = loading == null ? emptyList() : singletonList(new Entry<>(this, node, loading, true));
      valid = true;
    }
  }

  public static final class UpdateResult<N> {
    private final List<Entry<N>> removed;
    private final List<Entry<N>> inserted;
    private final List<Entry<N>> contained;

    private UpdateResult(List<Entry<N>> removed, List<Entry<N>> inserted, List<Entry<N>> contained) {
      this.removed = guard(removed);
      this.inserted = guard(inserted);
      this.contained = guard(contained);
    }

    public TreeModelEvent getEvent(@NotNull Object source, TreePath path, @NotNull List<Entry<N>> list) {
      int size = list.size();
      int[] indices = new int[size];
      Object[] nodes = new Object[size];
      int index = 0;
      for (Entry<N> entry : list) {
        indices[index] = entry.index;
        nodes[index++] = entry.node;
      }
      return new TreeModelEvent(source, path, indices, nodes);
    }

    public List<Entry<N>> getRemoved() {
      return removed;
    }

    public List<Entry<N>> getInserted() {
      return inserted;
    }

    public List<Entry<N>> getContained() {
      return contained;
    }
  }
}
