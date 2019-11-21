// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.util.ArrayList;
import java.util.List;

public interface ChangeListFilteringStrategy {
  @Nullable
  JComponent getFilterUI();

  void addChangeListener(@NotNull ChangeListener listener);

  void removeChangeListener(@NotNull ChangeListener listener);

  @NotNull
  CommittedChangesFilterKey getKey();

  void setFilterBase(@NotNull List<? extends CommittedChangeList> changeLists);

  void resetFilterBase();

  void appendFilterBase(@NotNull List<? extends CommittedChangeList> changeLists);

  @NotNull
  List<CommittedChangeList> filterChangeLists(@NotNull List<? extends CommittedChangeList> changeLists);

  ChangeListFilteringStrategy NONE = new ChangeListFilteringStrategy() {
    private final CommittedChangesFilterKey myKey = new CommittedChangesFilterKey("None", CommittedChangesFilterPriority.NONE);

    public String toString() {
      return "None";
    }

    @Override
    @Nullable
    public JComponent getFilterUI() {
      return null;
    }

    @Override
    public void setFilterBase(@NotNull List<? extends CommittedChangeList> changeLists) {
    }

    @Override
    public void addChangeListener(@NotNull ChangeListener listener) {
    }

    @Override
    public void removeChangeListener(@NotNull ChangeListener listener) {
    }

    @Override
    public void resetFilterBase() {
    }

    @Override
    public void appendFilterBase(@NotNull List<? extends CommittedChangeList> changeLists) {
    }

    @Override
    @NotNull
    public List<CommittedChangeList> filterChangeLists(@NotNull List<? extends CommittedChangeList> changeLists) {
      return new ArrayList<>(changeLists);
    }

    @NotNull
    @Override
    public CommittedChangesFilterKey getKey() {
      return myKey;
    }
  };
}
