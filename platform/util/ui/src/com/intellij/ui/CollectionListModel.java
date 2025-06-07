// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.util.ui.EditableModel;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.*;

public class CollectionListModel<T> extends AbstractListModel<T> implements EditableModel {
  private final List<T> myItems;

  public CollectionListModel(final @NotNull Collection<? extends T> items) {
    myItems = new ArrayList<>(items);
  }

  @Contract(mutates = "param1")
  public CollectionListModel(@NotNull List<T> items, @SuppressWarnings("unused") boolean useListAsIs) {
    myItems = items;
  }

  public CollectionListModel(@NotNull @Unmodifiable List<? extends T> items) {
    myItems = new ArrayList<>(items);
  }

  @SafeVarargs
  public CollectionListModel(T @NotNull ... items) {
    myItems = new ArrayList<>(Arrays.asList(items));
  }

  protected final @NotNull List<T> getInternalList() {
    return myItems;
  }

  @Override
  public int getSize() {
    return myItems.size();
  }

  @Override
  public T getElementAt(final int index) {
    return myItems.get(index);
  }

  public void add(final T element) {
    int i = myItems.size();
    myItems.add(element);
    fireIntervalAdded(this, i, i);
  }

  public void add(int i,final T element) {
    myItems.add(i, element);
    fireIntervalAdded(this, i, i);
  }

  public void add(final @NotNull List<? extends T> elements) {
    addAll(myItems.size(), elements);
  }

  public void addAll(int index, final @NotNull List<? extends T> elements) {
    if (elements.isEmpty()) return;

    myItems.addAll(index, elements);
    fireIntervalAdded(this, index, index + elements.size() - 1);
  }

  public void remove(@NotNull T element) {
    int index = getElementIndex(element);
    if (index != -1) {
      remove(index);
    }
  }

  public void setElementAt(final @NotNull T item, final int index) {
    itemReplaced(myItems.set(index, item), item);
    fireContentsChanged(this, index, index);
  }

  @SuppressWarnings("UnusedParameters")
  protected void itemReplaced(@NotNull T existingItem, @Nullable T newItem) { }

  public void remove(final int index) {
    T item = myItems.remove(index);
    if (item != null) {
      itemReplaced(item, null);
    }
    fireIntervalRemoved(this, index, index);
  }

  public void removeAll() {
    int size = myItems.size();
    if (size > 0) {
      myItems.clear();
      fireIntervalRemoved(this, 0, size - 1);
    }
  }

  public void contentsChanged(final @NotNull T element) {
    int i = myItems.indexOf(element);
    fireContentsChanged(this, i, i);
  }

  public void allContentsChanged() {
    fireContentsChanged(this, 0, myItems.size() - 1);
  }

  public void sort(final Comparator<? super T> comparator) {
    myItems.sort(comparator);
  }

  public @NotNull List<T> getItems() {
    return Collections.unmodifiableList(myItems);
  }

  public void replaceAll(final @NotNull List<? extends T> elements) {
    removeAll();
    add(elements);
  }

  @Override
  public void addRow() {
  }

  @Override
  public void removeRow(int index) {
    remove(index);
  }

  @Override
  public void exchangeRows(int oldIndex, int newIndex) {
    Collections.swap(myItems, oldIndex, newIndex);
    fireContentsChanged(this, oldIndex, oldIndex);
    fireContentsChanged(this, newIndex, newIndex);
  }

  @Override
  public boolean canExchangeRows(int oldIndex, int newIndex) {
    return true;
  }

  @Override
  public @NonNls String toString() {
    return getClass().getName() + " (" + getSize() + " elements)";
  }

  public List<T> toList() {
    return new ArrayList<>(myItems);
  }

  public int getElementIndex(T item) {
    return myItems.indexOf(item);
  }

  public boolean isEmpty() {
    return myItems.isEmpty();
  }

  public boolean contains(T item) {
    return getElementIndex(item) >= 0;
  }

  public void removeRange(int fromIndex, int toIndex) {
    if (fromIndex > toIndex) {
      throw new IllegalArgumentException("fromIndex must be <= toIndex");
    }
    for(int i = toIndex; i >= fromIndex; i--) {
      itemReplaced(myItems.remove(i), null);
    }
    fireIntervalRemoved(this, fromIndex, toIndex);
  }
}
