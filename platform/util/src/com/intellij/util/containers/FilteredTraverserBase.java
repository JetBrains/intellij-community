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
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static com.intellij.openapi.util.Conditions.*;

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
    return traverse(meta.traversal);
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
    return newInstance(Meta.<T>empty().forceExclude(meta.forceExclude).forceExpandAndSkip(meta.forceExpandAndSkip).withRoots(meta.roots));
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
  public Self withTraversal(TreeTraversal type) {
    return newInstance(meta.withTraversal(type));
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
    return newInstance(meta.expand(filter).filter(not(filter)));
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
  public Self forceExclude(@NotNull Condition<? super T> filter) {
    return newInstance(meta.forceExclude(filter));
  }

  @NotNull
  public Self forceExpandAndSkip(@NotNull Condition<? super T> filter) {
    return newInstance(meta.forceExpandAndSkip(filter));
  }

  @NotNull
  public JBIterable<T> children(@NotNull T node) {
    if (isAlwaysLeaf(node)) return JBIterable.empty();
    JBIterable<T> children = JBIterable.from(tree.fun(node));
    if (meta.childFilter == TRUE && meta.forceExpandAndSkip == Condition.FALSE) {
      return children.filter(not(meta.forceExclude));
    }
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
    final TreeTraversal traversal;
    final Condition<? super T> expandFilter;
    final Condition<? super T> childFilter;
    final Condition<? super T> resultFilter;

    final Condition<? super T> forceExclude;
    final Condition<? super T> forceExpandAndSkip;

    public Meta(@NotNull Iterable<? extends T> roots,
                @NotNull TreeTraversal traversal,
                @NotNull Condition<? super T> expandFilter,
                @NotNull Condition<? super T> childFilter,
                @NotNull Condition<? super T> resultFilter,
                @NotNull Condition<? super T> forceExclude,
                @NotNull Condition<? super T> forceExpandAndSkip) {
      this.roots = roots;
      this.traversal = traversal;
      this.expandFilter = expandFilter;
      this.childFilter = childFilter;
      this.resultFilter = resultFilter;
      this.forceExclude = forceExclude;
      this.forceExpandAndSkip = forceExpandAndSkip;
    }

    public Meta<T> withRoots(@NotNull Iterable<? extends T> roots) {
      return new Meta<T>(roots, traversal, expandFilter, childFilter, resultFilter, forceExclude, forceExpandAndSkip);
    }

    public Meta<T> withTraversal(TreeTraversal traversal) {
      return new Meta<T>(roots, traversal, expandFilter, childFilter, resultFilter, forceExclude, forceExpandAndSkip);
    }

    public Meta<T> expand(@NotNull Condition<? super T> filter) {
      return new Meta<T>(roots, traversal, and2(expandFilter, filter), childFilter, resultFilter, forceExclude,
                         forceExpandAndSkip);
    }

    public Meta<T> children(@NotNull Condition<? super T> filter) {
      return new Meta<T>(roots, traversal, expandFilter, and2(childFilter, filter), resultFilter, forceExclude,
                         forceExpandAndSkip);
    }

    public Meta<T> filter(@NotNull Condition<? super T> filter) {
      return new Meta<T>(roots, traversal, expandFilter, childFilter, and2(resultFilter, filter), forceExclude,
                         forceExpandAndSkip);
    }

    // forceExclude and forceSkip filter is always accumulated
    public Meta<T> forceExclude(Condition<? super T> filter) {
      return new Meta<T>(roots, traversal, expandFilter, childFilter, resultFilter, or2(forceExclude, filter),
                         forceExpandAndSkip);
    }

    public Meta<T> forceExpandAndSkip(Condition<? super T> filter) {
      return new Meta<T>(roots, traversal, expandFilter, childFilter, resultFilter, forceExclude, or2(forceExpandAndSkip, filter));
    }

    public Meta<T> forChildren(JBIterable<T> children) {
      Condition<T> expand = or2(forceExpandAndSkip, not(childFilter));
      return new Meta<T>(children, TreeTraversal.LEAVES_ONLY_DFS, expand, TRUE, not(or2(expand, forceExclude)), FALSE, FALSE);
    }

    private static final Meta<?> EMPTY = new Meta<Object>(
      JBIterable.empty(), TreeTraversal.PRE_ORDER_DFS,
      TRUE, TRUE, TRUE,
      FALSE, FALSE);

    public static <T> Meta<T> empty() {
      return (Meta<T>)EMPTY;
    }
  }
}
