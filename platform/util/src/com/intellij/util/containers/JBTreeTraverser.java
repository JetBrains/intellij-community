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

import com.intellij.util.Function;
import com.intellij.util.Functions;
import org.jetbrains.annotations.NotNull;

public class JBTreeTraverser<T> extends FilteredTraverserBase<T, JBTreeTraverser<T>> {

  @NotNull
  public static <T> JBTreeTraverser<T> from(@NotNull Function<? super T, ? extends Iterable<? extends T>> treeStructure) {
    return new JBTreeTraverser<>(treeStructure);
  }

  @NotNull
  public static <T> JBTreeTraverser<T> of(@NotNull Function<? super T, T[]> treeStructure) {
    return new JBTreeTraverser<>(Functions.compose(treeStructure, Functions.wrapArray()));
  }

  public JBTreeTraverser(Function<? super T, ? extends Iterable<? extends T>> treeStructure) {
    super(Meta.create(treeStructure));
  }

  protected JBTreeTraverser(Meta<T> meta) {
    super(meta);
  }

  @NotNull
  @Override
  protected JBTreeTraverser<T> newInstance(@NotNull Meta<T> meta) {
    return meta == myMeta ? this : new JBTreeTraverser<>(meta);
  }

  /**
   * Returns a {@code JBTreeTraverser} that applies {@code function} to each element of this traverser.
   * A reverse transform is required if available, otherwise use {@link #map(Function)}.
   */
  @NotNull
  public final <S> JBTreeTraverser<S> map(@NotNull Function<? super T, ? extends S> function,
                                    @NotNull Function<? super S, ? extends T> reverse) {
    return super.mapImpl(function, reverse);
  }

  /**
   * Returns a {@code JBTreeTraverser} that applies {@code function} to each element of this traverser.
   * The required reverse transform, a hash map, is built internally while traversing.
   * Prefer {@link #map(Function, Function)} if a cheap reverse transform is available.
   */
  @NotNull
  public final <S> JBTreeTraverser<S> map(@NotNull Function<? super T, ? extends S> function) {
    return super.mapImpl(function);
  }
}