// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.table;

import com.intellij.ui.SpeedSearchBase;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsCommitMetadata;
import com.intellij.vcs.log.data.LoadingDetails;
import com.intellij.vcs.log.ui.table.column.VcsLogMetadataColumn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractList;
import java.util.List;
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
    throw new UnsupportedOperationException("Getting row text in a Log is unsupported since we match columns separately.");
  }

  @Override
  protected boolean isMatchingElement(Object row, String pattern) {
    return isMatchingMetadata(pattern, getCommitMetadata((Integer)row));
  }

  protected boolean isMatchingMetadata(String pattern, @Nullable VcsCommitMetadata metadata) {
    return isMatchingMetadata(pattern, metadata, getColumnsForSpeedSearch());
  }

  protected boolean isMatchingMetadata(String pattern, @Nullable VcsCommitMetadata metadata, @NotNull List<VcsLogMetadataColumn> columns) {
    if (metadata == null) return false;
    return columns.stream().anyMatch(column -> compare(column.getValue(myComponent.getModel(), metadata), pattern));
  }

  @Nullable
  protected VcsCommitMetadata getCommitMetadata(int row) {
    VcsCommitMetadata metadata = myComponent.getModel().getCommitMetadata(row);
    if (metadata instanceof LoadingDetails) return null;
    return metadata;
  }

  @NotNull
  protected List<VcsLogMetadataColumn> getColumnsForSpeedSearch() {
    return ContainerUtil.filterIsInstance(myComponent.getVisibleColumns(), VcsLogMetadataColumn.class);
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
