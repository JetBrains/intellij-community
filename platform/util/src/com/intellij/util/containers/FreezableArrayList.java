// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * A (mutable) {@link List} which, after calling {@link #freeze()}, becomes unmodifiable.
 * Useful when you want to create and fill an {@link ArrayList} and then return it as-is with a "no mutations" guarantee.
 */
@ApiStatus.Internal
public class FreezableArrayList<T> extends ArrayList<T> {
  public FreezableArrayList(int initialCapacity) {
    super(initialCapacity);
  }

  public FreezableArrayList() {
  }

  FreezableArrayList(@NotNull Collection<? extends T> c) {
    super(c);
  }

  @NotNull
  @Unmodifiable List<T> freeze() {
    modCount = -1;
    return this;
  }
  private void checkForFrozen() {
    if (modCount == -1) {
      throw new UnsupportedOperationException("This list is unmodifiable. For a temporary workaround, please run IDE in non-internal mode.");
    }
  }

  @Override
  public T set(int index, T element) {
    checkForFrozen();
    return super.set(index, element);
  }

  @Override
  public boolean add(T t) {
    checkForFrozen();
    return super.add(t);
  }

  @Override
  public void add(int index, T element) {
    checkForFrozen();
    super.add(index, element);
  }

  @Override
  public T remove(int index) {
    checkForFrozen();
    return super.remove(index);
  }

  @Override
  public boolean remove(Object o) {
    checkForFrozen();
    return super.remove(o);
  }

  @Override
  public void clear() {
    checkForFrozen();
    super.clear();
  }

  @Override
  public boolean addAll(Collection<? extends T> c) {
    checkForFrozen();
    return super.addAll(c);
  }

  @Override
  public boolean addAll(int index, Collection<? extends T> c) {
    checkForFrozen();
    return super.addAll(index, c);
  }

  @Override
  protected void removeRange(int fromIndex, int toIndex) {
    checkForFrozen();
    super.removeRange(fromIndex, toIndex);
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    checkForFrozen();
    return super.removeAll(c);
  }

  @Override
  public boolean retainAll(Collection<?> c) {
    checkForFrozen();
    return super.retainAll(c);
  }

  @Override
  public boolean removeIf(Predicate<? super T> filter) {
    checkForFrozen();
    return super.removeIf(filter);
  }

  @Override
  public void replaceAll(UnaryOperator<T> operator) {
    checkForFrozen();
    super.replaceAll(operator);
  }

  @Override
  public void sort(Comparator<? super T> c) {
    checkForFrozen();
    super.sort(c);
  }

  public @Unmodifiable List<T> emptyOrFrozen() {
    return isEmpty() ? ContainerUtil.emptyList() :
           ContainerUtil.Options.RETURN_REALLY_UNMODIFIABLE_COLLECTION_FROM_METHODS_MARKED_UNMODIFIABLE ? freeze() : this;
  }
}
