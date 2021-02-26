// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.table;

import com.intellij.ui.SpeedSearchBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractList;
import java.util.ListIterator;

public class VcsLogSpeedSearch extends SpeedSearchBase<VcsLogGraphTable> {
  public VcsLogSpeedSearch(@NotNull VcsLogGraphTable component) {
    super(component);
  }

  @Override
  protected int getElementCount() {
    return myComponent.getRowCount();
  }

  @NotNull
  @Override
  protected ListIterator<Object> getElementIterator(int startingViewIndex) {
    return new MyRowsList().listIterator(startingViewIndex);
  }

  @Override
  protected int getSelectedIndex() {
    return myComponent.getSelectedRow();
  }

  @Nullable
  @Override
  protected String getElementText(@NotNull Object row) {
    return myComponent.getModel().getCommitMetadata((Integer)row).getSubject();
  }

  @Override
  protected void selectElement(@Nullable Object row, @NotNull String selectedText) {
    if (row == null) return;
    myComponent.jumpToRow((Integer)row, true);
  }

  private class MyRowsList extends AbstractList<Object> {
    @Override
    public int size() {
      return myComponent.getRowCount();
    }

    @Override
    public Object get(int index) {
      return index;
    }
  }
}
