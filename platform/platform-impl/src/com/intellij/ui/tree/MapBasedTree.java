/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui.tree;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.TreeModelEvent;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableList;
import static java.util.stream.Collectors.toList;

/**
 * @author Sergey.Malenkov
 */
public final class MapBasedTree<K, N> {
  private static final Logger LOG = Logger.getInstance(MapBasedTree.class);

  private final Map<K, Entry<N>> map;
  private final Function<N, K> keyFunction;
  private volatile Entry<N> root;

  public MapBasedTree(boolean identity, @NotNull Function<N, K> keyFunction) {
    map = identity ? new IdentityHashMap<>() : new HashMap<>();
    this.keyFunction = keyFunction;
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
    if (entry == null || entry.node == node) return entry;
    LOG.warn("MapBasedTree: found another node with the key: " + key);
    return null;
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

  public boolean updateRoot(N node) {
    if (node == (root == null ? null : root.node)) return false;

    map.clear();
    if (node == null) {
      root = null;
    }
    else {
      root = new Entry<>(null, node);
      K key = keyFunction.apply(node);
      if (key != null) map.put(key, root);
    }
    return true;
  }

  public UpdateResult<N> update(@NotNull Entry<N> parent, List<N> children) {
    List<Entry<N>> newChildren = new ArrayList<>(children == null ? 0 : children.size());
    List<Entry<N>> oldChildren = parent.children;
    Map<Entry<N>, K> mapInserted = new IdentityHashMap<>();
    Map<Entry<N>, K> mapContained = new IdentityHashMap<>();

    if (children != null && !children.isEmpty()) {
      children.forEach(node -> {
        if (node == null) {
          LOG.warn("MapBasedTree: ignore null node");
          return;
        }
        K key = getKey(node);
        if (key == null) return;

        Entry<N> entry = findEntry(key);
        if (entry == null) {
          entry = new Entry<>(parent, node);
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

    List<Entry<N>> removed = oldChildren;
    List<Entry<N>> inserted = newChildren;
    List<Entry<N>> contained = null;
    if (!mapContained.isEmpty()) {
      if (oldChildren == null) {
        oldChildren = emptyList();
        LOG.warn("MapBasedTree: unexpected state");
      }
      removed = oldChildren.stream().filter(entry -> !mapContained.containsKey(entry)).collect(toList());
      inserted = newChildren.stream().filter(entry -> !mapContained.containsKey(entry)).collect(toList());
      contained = newChildren.stream().filter(entry -> mapContained.containsKey(entry)).collect(toList());
    }
    removeChildren(parent, removed);
    mapInserted.forEach((entry, key) -> map.put(key, entry));
    return new UpdateResult<>(removed, inserted, contained);
  }

  private void removeChildren(Entry<N> parent, List<Entry<N>> children) {
    if (children != null) {
      for (Entry<N> entry : children) {
        if (parent.loading == entry.node) {
          parent.loading = null;
        }
        else {
          K key = getKey(entry.node);
          if (key != null) {
            Entry<N> removed = map.remove(key);
            if (removed == null) {
              LOG.warn("MapBasedTree: expected entry is not found");
            }
            else if (removed != entry) {
              LOG.warn("MapBasedTree: do not remove unexpected entry");
              map.put(key, removed);
            }
            else {
              removeChildren(removed, removed.children);
            }
          }
        }
      }
    }
  }

  private static <T> List<T> guard(List<T> list) {
    return list == null || list.isEmpty() ? emptyList() : unmodifiableList(list);
  }

  public static final class Entry<N> extends TreePath {
    private final N node;
    private final Entry<N> parent;
    private volatile int index;
    private volatile boolean leaf;
    private volatile List<Entry<N>> children;
    private volatile N loading;

    private Entry(Entry<N> parent, N node) {
      super(parent, node);
      this.node = node;
      this.parent = parent;
    }

    public N getNode() {
      return node;
    }

    public N getParent() {
      return parent == null ? null : parent.node;
    }

    public boolean isLeaf() {
      return leaf;
    }

    public List<N> getChildren() {
      return children == null ? null : children.stream().map(entry -> entry.node).collect(toList());
    }

    public boolean isLoadingRequired() {
      return children == null;
    }

    void setLoadingChildren(N loading) {
      if (children != null) LOG.warn("MapBasedTree: rewrite loaded nodes");
      this.loading = loading;
      children = loading == null ? emptyList() : singletonList(new Entry<>(this, loading));
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
