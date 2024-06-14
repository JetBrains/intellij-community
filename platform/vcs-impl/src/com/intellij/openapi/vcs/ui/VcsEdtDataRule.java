// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.ui;

import com.intellij.openapi.ListSelection;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.DataSnapshot;
import com.intellij.openapi.actionSystem.EdtDataRule;
import com.intellij.openapi.vcs.changes.Change;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

import static com.intellij.openapi.vcs.VcsDataKeys.*;

@ApiStatus.Internal
public class VcsEdtDataRule implements EdtDataRule {
  @Override
  public void uiDataSnapshot(@NotNull DataSink sink, @NotNull DataSnapshot snapshot) {
    Change[] selectedChanges = snapshot.get(SELECTED_CHANGES);
    ListSelection<Change> selection =
      selectedChanges == null ? null :
      ListSelection.createAt(Arrays.asList(selectedChanges), 0).asExplicitSelection();
    if (selection == null) {
      Change[] changes = snapshot.get(CHANGES);
      selection = changes == null ? null : ListSelection.createAt(Arrays.asList(changes), 0);
    }
    if (selection != null) {
      sink.set(CHANGES_SELECTION, selection);
    }
  }
}
