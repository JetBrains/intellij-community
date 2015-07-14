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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Iterator;

public abstract class FilteredTraverser<T, Self extends FilteredTraverser<T, Self>> extends TreeTraverser<T> implements Iterable<T> {

  protected final Meta<T> myMeta;

  protected FilteredTraverser(@Nullable Meta<T> meta) {
    myMeta = meta == null ? FilteredTraverser.<T>emptyMeta() : meta;
  }

  @NotNull
  public T getRoot() {
    return myMeta.roots.iterator().next();
  }

  @NotNull
  public Iterable<? extends T> getRoots() {
    return myMeta.roots;
  }

  @Override
  public Iterator<T> iterator() {
    return rawIterable().iterator();
  }

  protected abstract Self newInstance(Meta<T> meta);

  @NotNull
  public JBIterable<T> rawIterable() {
    return preOrderTraversal();
  }

  @NotNull
  public final JBIterable<T> preOrderTraversal() {
    return preOrderTraversal(getRoots()).filter(newResultFilter());
  }

  @NotNull
  public final JBIterable<T> postOrderTraversal() {
    return postOrderTraversal(getRoots()).filter(newResultFilter());
  }

  @NotNull
  public final JBIterable<T> breadthFirstTraversal() {
    return breadthFirstTraversal(getRoots()).filter(newResultFilter());
  }

  @NotNull
  public final JBIterable<T> tracingBreadthFirstTraversal() {
    return tracingBreadthFirstTraversal(getRoots()).filter(newResultFilter());
  }

  @NotNull
  private Condition<? super T> newResultFilter() {
    if (!myMeta.skipExpanded) return myMeta.resultFilter;
    return Conditions.and2(Conditions.not(Conditions.or2(Conditions.oneOf(myMeta.roots), myMeta.expandFilter)), myMeta.resultFilter);
  }

  @NotNull
  public Self reset() {
    return newInstance(FilteredTraverser.<T>emptyMeta().exclude(myMeta.excludeFilter).withRoots(myMeta.roots));
  }

  @NotNull
  public Self withRoot(@NotNull T root) {
    return newInstance(myMeta.withRoots(Collections.singleton(root)));
  }

  @NotNull
  public Self withRoots(@NotNull Iterable<? extends T> roots) {
    return newInstance(myMeta.withRoots(roots));
  }

  @NotNull
  public Self skipExpanded(boolean skip) {
    return newInstance(myMeta.skipExpanded(skip));
  }

  @NotNull
  public Self expand(@NotNull Condition<? super T> filter) {
    return newInstance(myMeta.expand(filter));
  }

  @NotNull
  public Self expandOnly(@NotNull Condition<? super T> filter) {
    return newInstance(myMeta.expandOnly(filter));
  }

  @NotNull
  public Self filter(@NotNull Condition<? super T> filter) {
    return newInstance(myMeta.filter(filter));
  }

  @NotNull
  public <C> JBIterable<C> filter(@NotNull Class<C> type) {
    return rawIterable().filter(type);
  }

  public Self exclude(@NotNull Condition<? super T> filter) {
    return newInstance(myMeta.exclude(filter));
  }

  @NotNull
  @Override
  public JBIterable<T> children(@NotNull T node) {
    if (!myMeta.expandFilter.value(node) && (myMeta.expandOnly || !Conditions.oneOf(myMeta.roots).value(node))) {
      return JBIterable.empty();
    }
    return JBIterable.from(childrenImpl(node)).filter(Conditions.not(myMeta.excludeFilter));
  }

  protected abstract Iterable<? extends T> childrenImpl(T node);

  @Override
  public String toString() {
    return rawIterable().toString();
  }

  protected static <T> Meta<T> emptyMeta() {
    return new Meta<T>(EmptyIterable.<T>getInstance(), false, false,
                       Conditions.alwaysTrue(),
                       Conditions.alwaysTrue(),
                       Conditions.alwaysFalse());
  }

  protected static class Meta<T> {
    final Iterable<? extends T> roots;
    final boolean skipExpanded;
    final boolean expandOnly;
    final Condition<? super T> expandFilter;
    final Condition<? super T> resultFilter;
    final Condition<? super T> excludeFilter;

    public Meta(@NotNull Iterable<? extends T> roots,
                boolean skipExpanded,
                boolean expandOnly,
                @NotNull Condition<? super T> expandFilter,
                @NotNull Condition<? super T> resultFilter,
                @NotNull Condition<? super T> excludeFilter) {
      this.roots = roots;
      this.skipExpanded = skipExpanded;
      this.expandOnly = expandOnly;
      this.expandFilter = expandFilter;
      this.resultFilter = resultFilter;
      this.excludeFilter = excludeFilter;
    }

    public Meta<T> withRoots(@NotNull Iterable<? extends T> roots) {
      return new Meta<T>(roots, skipExpanded, expandOnly, expandFilter, resultFilter, excludeFilter);
    }

    public Meta<T> skipExpanded(boolean skip) {
      return new Meta<T>(roots, skip, expandOnly, expandFilter, resultFilter, excludeFilter);
    }

    public Meta<T> expand(@NotNull Condition<? super T> filter) {
      return new Meta<T>(roots, skipExpanded, expandOnly, Conditions.and2(expandFilter, filter), resultFilter, excludeFilter);
    }

    public Meta<T> expandOnly(@NotNull Condition<? super T> filter) {
      return new Meta<T>(roots, skipExpanded, true, Conditions.and2(expandFilter, filter), resultFilter, excludeFilter);
    }

    public Meta<T> filter(@NotNull Condition<? super T> filter) {
      return new Meta<T>(roots, skipExpanded, expandOnly,  expandFilter, Conditions.and2(resultFilter, filter), excludeFilter);
    }

    public Meta<T> exclude(Condition<? super T> filter) {
      // exclude filter is always accumulated
      return new Meta<T>(roots, skipExpanded, expandOnly, expandFilter, resultFilter, Conditions.or2(excludeFilter, filter));
    }
  }
}
