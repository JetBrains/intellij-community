// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class CollectionComboBoxModel<T> extends CollectionListModel<T> implements ComboBoxModel<T> {
  protected T mySelection;

  public CollectionComboBoxModel() {
    super();
    mySelection = null;
  }

  @Contract(mutates = "param1")
  public CollectionComboBoxModel(@NotNull List<T> items) {
    this(items, ContainerUtil.getFirstItem(items));
  }

  @Contract(mutates = "param1")
  public CollectionComboBoxModel(@NotNull List<T> items, @Nullable T selection) {
    super(items, true);
    mySelection = selection;
  }

  @Override
  public void setSelectedItem(@Nullable Object item) {
    if (mySelection != item) {
      @SuppressWarnings("unchecked") T t = (T)item;
      mySelection = t;
      update();
    }
  }

  @Override
  public @Nullable Object getSelectedItem() {
    return mySelection;
  }

  public @Nullable T getSelected() {
    return mySelection;
  }

  public void update() {
    super.fireContentsChanged(this, -1, -1);
  }

}