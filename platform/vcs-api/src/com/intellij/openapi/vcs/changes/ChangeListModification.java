// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @see ChangeListManager
 */
public interface ChangeListModification {
  LocalChangeList addChangeList(@NotNull @NlsSafe String name, @Nullable @NlsSafe final String comment);

  void setDefaultChangeList(@NotNull @NlsSafe String name);

  void setDefaultChangeList(@NotNull LocalChangeList list);

  void removeChangeList(@NotNull @NlsSafe String name);

  void removeChangeList(@NotNull LocalChangeList list);

  void moveChangesTo(@NotNull LocalChangeList list, Change @NotNull ... changes);

  /**
   * Prohibit changelist deletion or rename until Project is closed
   */
  boolean setReadOnly(@NotNull @NlsSafe String name, final boolean value);

  boolean editName(@NotNull @NlsSafe String fromName, @NotNull @NlsSafe String toName);

  @Nullable
  String editComment(@NotNull @NlsSafe String name, @NlsSafe final String newComment);
}
