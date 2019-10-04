// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.treeWithCheckedNodes;

import com.intellij.util.Processor;
import com.intellij.util.containers.SLRUMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
* @author irengrig
 *
 * We can have only limited number of nodes selected in a tree;
 * and children of selected nodes (any level) cannot change their state
 * used together with {@link SelectionManager}
*/
public class SelectedState<T> {
  private final Set<T> mySelected;
  private final SLRUMap<T, TreeNodeState> myCache;
  private final int mySelectedSize;

  public SelectedState(final int selectedSize, final int queueSize) {
    mySelectedSize = selectedSize;
    assert queueSize > 0;
    mySelected = new HashSet<>();
    myCache = new SLRUMap<>(queueSize, queueSize);
  }

  @Nullable
  public TreeNodeState get(final T node) {
    if (mySelected.contains(node)) return TreeNodeState.SELECTED;
    return myCache.get(node);
  }

  public void clear(final T node) {
    myCache.remove(node);
    mySelected.remove(node);
  }

  public void clearAllCachedMatching(final Processor<? super T> processor) {
    final Set<Map.Entry<T, TreeNodeState>> entries = myCache.entrySet();
    for (Map.Entry<T, TreeNodeState> entry: entries){
      if(processor.process(entry.getKey())) {
        myCache.remove(entry.getKey());
      }
    }
  }

  public void remove(final T node) {
    mySelected.remove(node);
    myCache.remove(node);
  }

  @NotNull
  public TreeNodeState putAndPass(final T node, @NotNull final TreeNodeState state) {
    if (TreeNodeState.SELECTED.equals(state)) {
      mySelected.add(node);
      myCache.remove(node);
    } else {
      mySelected.remove(node);
      myCache.put(node, state);
    }
    return state;
  }

  public boolean canAddSelection() {
    return mySelected.size() < mySelectedSize;
  }

  public Set<T> getSelected() {
    return Collections.unmodifiableSet(new HashSet<>(mySelected));
  }

  public void setSelection(Collection<? extends T> files) {
    mySelected.clear();
    mySelected.addAll(files);
  }
}
