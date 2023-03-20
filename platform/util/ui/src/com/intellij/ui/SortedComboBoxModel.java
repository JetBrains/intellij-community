// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;
import java.util.Comparator;

public class SortedComboBoxModel<T> extends SortedListModel<T> implements ComboBoxModel<T> {

  private T mySelection;

  public SortedComboBoxModel(@NotNull Comparator<? super T> comparator) {
    super(comparator);
  }

  public SortedComboBoxModel(@NotNull Collection<? extends T> items,
                             @NotNull Comparator<? super T> comparator) {
    super(items, comparator);
  }

  @Override
  public T getSelectedItem() {
    return mySelection;
  }

  @Override
  public void setSelectedItem(Object anItem) {
    if (Comparing.equal(mySelection, anItem)) return;
    mySelection = (T)anItem;
    fireSelectionChanged();
  }

  private void fireSelectionChanged() {
    fireContentsChanged(this, -1, -1);
  }
}
