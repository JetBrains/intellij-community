// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeListener;
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

  @Nls
  String toString();
}
