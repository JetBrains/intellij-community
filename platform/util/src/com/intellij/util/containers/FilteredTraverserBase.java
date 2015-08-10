/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.util.containers;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public abstract class FilteredTraverserBase<T, Self extends FilteredTraverserBase<T, Self>> implements Iterable<T> {

  protected final Meta<T> meta;
  protected final Function<T, ? extends Iterable<? extends T>> tree;

  protected FilteredTraverserBase(@Nullable Meta<T> meta, Function<T, ? extends Iterable<? extends T>> tree) {
    this.tree = tree;
    this.meta = meta == null ? Meta.<T>empty() : meta;
  }

  @NotNull
  public T getRoot() {
    return meta.roots.iterator().next();
  }

  @NotNull
  public Iterable<? extends T> getRoots() {
    return meta.roots;
  }

  @Override
  public Iterator<T> iterator() {
    return traverse().iterator();
  }

  @NotNull
  protected abstract Self newInstance(Meta<T> meta);

  @NotNull
  public JBIterable<T> traverse(TreeTraversal traversal) {
    Function<T, Iterable<? extends T>> adjusted = new Function<T, Iterable<? extends T>>() {
      @Override
      public Iterable<? extends T> fun(T t) {
        return children(t);
      }
    };
    return traversal.traversal(getRoots(), adjusted).filter(meta.resultFilter);
  }

  @NotNull
  public JBIterable<T> traverse() {
    return meta.skipExpanded ? leavesOnlyDfsTraversal() : preOrderDfsTraversal();
  }

  @NotNull
  public final JBIterable<T> preOrderDfsTraversal() {
    return traverse(TreeTraversal.PRE_ORDER_DFS);
  }

  @NotNull
  public final JBIterable<T> postOrderDfsTraversal() {
    return traverse(TreeTraversal.POST_ORDER_DFS);
  }

  @NotNull
  public final JBIterable<T> leavesOnlyDfsTraversal() {
    return traverse(TreeTraversal.LEAVES_ONLY_DFS);
  }

  @NotNull
  public final JBIterable<T> bfsTraversal() {
    return traverse(TreeTraversal.PLAIN_BFS);
  }

  @NotNull
  public final JBIterable<T> tracingBfsTraversal() {
    return traverse(TreeTraversal.TRACING_BFS);
  }

  @NotNull
  public final JBIterable<T> leavesOnlyBfsTraversal() {
    return traverse(TreeTraversal.LEAVES_ONLY_BFS);
  }

  @NotNull
  public Self reset() {
    return newInstance(Meta.<T>empty().exclude(meta.excludeFilter).withRoots(meta.roots));
  }

  @NotNull
  public Self withRoot(@NotNull T root) {
    return newInstance(meta.withRoots(Collections.singleton(root)));
  }

  @NotNull
  public Self withRoots(@NotNull Iterable<? extends T> roots) {
    return newInstance(meta.withRoots(roots));
  }

  @NotNull
  public Self leavesOnly(boolean flag) {
    return newInstance(meta.skipExpanded(flag));
  }

  @NotNull
  public Self expand(@NotNull Condition<? super T> filter) {
    return newInstance(meta.expand(filter));
  }

  @NotNull
  public Self expandAndFilter(Condition<? super T> filter) {
    return newInstance(meta.expand(filter).filter(filter));
  }

  @NotNull
  public Self expandAndSkip(Condition<? super T> filter) {
    return newInstance(meta.expand(filter).filter(Conditions.not(filter)));
  }

  @NotNull
  public Self children(@NotNull Condition<? super T> filter) {
    return newInstance(meta.children(filter));
  }

  @NotNull
  public Self filter(@NotNull Condition<? super T> filter) {
    return newInstance(meta.filter(filter));
  }

  @NotNull
  public <C> JBIterable<C> filter(@NotNull Class<C> type) {
    return traverse().filter(type);
  }

  @NotNull
  public Self exclude(@NotNull Condition<? super T> filter) {
    return newInstance(meta.exclude(filter));
  }

  @NotNull
  public JBIterable<T> children(@NotNull T node) {
    if (isAlwaysLeaf(node)) return JBIterable.empty();
    JBIterable<T> children = JBIterable.from(tree.fun(node));
    if (meta.childFilter == Conditions.TRUE) return children.filter(Conditions.not(meta.excludeFilter));
    // traverse subtree to select accepted children
    return newInstance(meta.forChildren(children)).traverse();
  }

  protected boolean isAlwaysLeaf(@NotNull T node) {
    return !meta.expandFilter.value(node);
  }

  @NotNull
  public List<T> toList() {
    return traverse().toList();
  }

  @Override
  public String toString() {
    return traverse().toString();
  }


  protected static class Meta<T> {
    final Iterable<? extends T> roots;
    final boolean skipExpanded;
    final Condition<? super T> expandFilter;
    final Condition<? super T> childFilter;
    final Condition<? super T> resultFilter;
    final Condition<? super T> excludeFilter;

    public Meta(@NotNull Iterable<? extends T> roots,
                boolean skipExpanded,
                @NotNull Condition<? super T> expandFilter,
                @NotNull Condition<? super T> childFilter,
                @NotNull Condition<? super T> resultFilter,
                @NotNull Condition<? super T> excludeFilter) {
      this.roots = roots;
      this.skipExpanded = skipExpanded;
      this.expandFilter = expandFilter;
      this.childFilter = childFilter;
      this.resultFilter = resultFilter;
      this.excludeFilter = excludeFilter;
    }

    public Meta<T> withRoots(@NotNull Iterable<? extends T> roots) {
      return new Meta<T>(roots, skipExpanded, expandFilter, childFilter, resultFilter, excludeFilter);
    }

    public Meta<T> skipExpanded(boolean flag) {
      return new Meta<T>(roots, flag, expandFilter, childFilter, resultFilter, excludeFilter);
    }

    public Meta<T> expand(@NotNull Condition<? super T> filter) {
      return new Meta<T>(roots, skipExpanded, Conditions.and2(expandFilter, filter), childFilter, resultFilter, excludeFilter);
    }

    public Meta<T> children(@NotNull Condition<? super T> filter) {
      return new Meta<T>(roots, skipExpanded, expandFilter, Conditions.and2(childFilter, filter), resultFilter, excludeFilter);
    }

    public Meta<T> filter(@NotNull Condition<? super T> filter) {
      return new Meta<T>(roots, skipExpanded, expandFilter, childFilter, Conditions.and2(resultFilter, filter), excludeFilter);
    }

    public Meta<T> exclude(Condition<? super T> filter) {
      // exclude filter is always accumulated
      return new Meta<T>(roots, skipExpanded, expandFilter, childFilter, resultFilter, Conditions.or2(excludeFilter, filter));
    }

    private static final Meta<?> EMPTY = new Meta<Object>(
      JBIterable.empty(), false, Conditions.TRUE, Conditions.TRUE, Conditions.TRUE, Conditions.FALSE);

    public static <T> Meta<T> empty() {
      return (Meta<T>)EMPTY;
    }

    public Meta<T> forChildren(Iterable<? extends T> children) {
      return new Meta<T>(children, false, Conditions.not(childFilter), Conditions.TRUE, childFilter, excludeFilter);
    }
  }
}
