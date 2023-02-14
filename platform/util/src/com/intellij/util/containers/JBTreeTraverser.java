// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import com.intellij.util.Function;
import com.intellij.util.Functions;
import org.jetbrains.annotations.NotNull;

public class JBTreeTraverser<T> extends FilteredTraverserBase<T, JBTreeTraverser<T>> {
  public static @NotNull <T> JBTreeTraverser<T> from(@NotNull Function<? super T, ? extends Iterable<? extends T>> treeStructure) {
    return new JBTreeTraverser<>(treeStructure);
  }

  public static @NotNull <T> JBTreeTraverser<T> of(@NotNull Function<? super T, T[]> treeStructure) {
    return new JBTreeTraverser<>(Functions.compose(treeStructure, Functions.wrapArray()));
  }

  public JBTreeTraverser(Function<? super T, ? extends Iterable<? extends T>> treeStructure) {
    super(Meta.create(treeStructure));
  }

  protected JBTreeTraverser(Meta<T> meta) {
    super(meta);
  }

  @Override
  protected @NotNull JBTreeTraverser<T> newInstance(@NotNull Meta<T> meta) {
    return meta == myMeta ? this : new JBTreeTraverser<>(meta);
  }

  /**
   * Returns a {@code JBTreeTraverser} that applies {@code function} to each element of this traverser.
   * A reverse transform is required if available, otherwise use {@link #map(Function)}.
   */
  public final @NotNull <S> JBTreeTraverser<S> map(@NotNull Function<? super T, ? extends S> function,
                                    @NotNull Function<? super S, ? extends T> reverse) {
    return super.mapImpl(function, reverse);
  }

  /**
   * Returns a {@code JBTreeTraverser} that applies {@code function} to each element of this traverser.
   * The required reverse transform, a hash map, is built internally while traversing.
   * Prefer {@link #map(Function, Function)} if a cheap reverse transform is available.
   */
  public final @NotNull <S> JBTreeTraverser<S> map(@NotNull Function<? super T, ? extends S> function) {
    return super.mapImpl(function);
  }
}