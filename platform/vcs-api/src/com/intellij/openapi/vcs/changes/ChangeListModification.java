// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @see ChangeListManager
 */
public interface ChangeListModification {
  @NotNull LocalChangeList addChangeList(@NotNull @NlsSafe String name, @Nullable @NlsSafe String comment);

  void setDefaultChangeList(@NotNull @NlsSafe String name);

  void setDefaultChangeList(@NotNull LocalChangeList list);

  void removeChangeList(@NotNull @NlsSafe String name);

  void removeChangeList(@NotNull LocalChangeList list);

  void moveChangesTo(@NotNull LocalChangeList list, Change @NotNull ... changes);

  void moveChangesTo(@NotNull LocalChangeList list, @NotNull List<? extends @NotNull Change> changes);

  /**
   * Prohibit changelist deletion or rename until the project is closed
   */
  boolean setReadOnly(@NotNull @NlsSafe String name, final boolean value);

  boolean editName(@NotNull @NlsSafe String fromName, @NotNull @NlsSafe String toName);

  @Nullable
  String editComment(@NotNull @NlsSafe String name, @NlsSafe final String newComment);
}
