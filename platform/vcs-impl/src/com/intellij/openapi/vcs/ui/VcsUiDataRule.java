// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.ui;

import com.intellij.openapi.ListSelection;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.DataSnapshot;
import com.intellij.openapi.actionSystem.UiDataRule;
import com.intellij.openapi.vcs.VcsVirtualFilesRule;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.history.VcsRevisionNumberArrayRule;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

import static com.intellij.openapi.vcs.VcsDataKeys.*;

@ApiStatus.Internal
public class VcsUiDataRule implements UiDataRule {
  @Override
  public void uiDataSnapshot(@NotNull DataSink sink, @NotNull DataSnapshot snapshot) {
    sink.set(CHANGES_SELECTION, getListSelection(snapshot));
    sink.lazyValue(VIRTUAL_FILES, dataProvider -> VcsVirtualFilesRule.getData(dataProvider));
    sink.lazyValue(VCS_REVISION_NUMBERS, dataProvider -> VcsRevisionNumberArrayRule.getData(dataProvider));
  }

  private static @Nullable ListSelection<Change> getListSelection(@NotNull DataSnapshot snapshot) {
    Change[] selectedChanges = snapshot.get(SELECTED_CHANGES);
    if (selectedChanges != null) {
      return ListSelection.createAt(Arrays.asList(selectedChanges), 0).asExplicitSelection();
    }

    Change[] changes = snapshot.get(CHANGES);
    if (changes != null) {
      return ListSelection.createAt(Arrays.asList(changes), 0);
    }

    return null;
  }
}
