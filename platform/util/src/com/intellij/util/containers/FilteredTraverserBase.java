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

public abstract class FilteredTraverserBase<T, Self extends FilteredTraverserBase<T, Self>> extends TreeTraverser<T> implements Iterable<T> {

  protected final Meta<T> meta;

  protected FilteredTraverserBase(@Nullable Meta<T> meta, Function<T, ? extends Iterable<? extends T>> provider) {
    super(provider);
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
    if (!meta.skipExpanded) return meta.resultFilter;
    return Conditions.and2(Conditions.not(Conditions.or2(Conditions.oneOf(meta.roots), meta.expandFilter)), meta.resultFilter);
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
  public Self skipExpanded(boolean skip) {
    return newInstance(meta.skipExpanded(skip));
  }

  @NotNull
  public Self expand(@NotNull Condition<? super T> filter) {
    return newInstance(meta.expand(filter));
  }

  @NotNull
  public Self expandOnly(@NotNull Condition<? super T> filter) {
    return newInstance(meta.expandOnly(filter));
  }

  @NotNull
  public Self filter(@NotNull Condition<? super T> filter) {
    return newInstance(meta.filter(filter));
  }

  @NotNull
  public <C> JBIterable<C> filter(@NotNull Class<C> type) {
    return rawIterable().filter(type);
  }

  public Self exclude(@NotNull Condition<? super T> filter) {
    return newInstance(meta.exclude(filter));
  }

  @NotNull
  @Override
  public JBIterable<T> children(@NotNull T node) {
    if (!meta.expandFilter.value(node) && (meta.expandOnly || !Conditions.oneOf(meta.roots).value(node))) {
      return JBIterable.empty();
    }
    return JBIterable.from(super.children(node)).filter(Conditions.not(meta.excludeFilter));
  }

  @NotNull
  public List<T> toList() {
    return rawIterable().toList();
  }

  @Override
  public String toString() {
    return rawIterable().toString();
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

    private static final Meta<?> EMPTY = new Meta<Object>(
      JBIterable.empty(), false, false, Conditions.TRUE, Conditions.TRUE, Conditions.FALSE);

    public static <T> Meta<T> empty() {
      return (Meta<T>)EMPTY;
    }
  }
}
