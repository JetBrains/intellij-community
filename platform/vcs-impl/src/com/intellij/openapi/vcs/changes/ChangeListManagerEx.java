/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.changes;

import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public abstract class ChangeListManagerEx extends ChangeListManager {
  public abstract boolean isInUpdate();

  @NotNull
  public abstract Collection<LocalChangeList> getAffectedLists(@NotNull Collection<Change> changes);

  @NotNull
  public abstract LocalChangeList addChangeList(@NotNull String name, @Nullable String comment, @Nullable ChangeListData data);

  /**
   * Blocks modal dialogs that we don't want to popup during some process, for example, above the commit dialog.
   * They will be shown when notifications are unblocked.
   */
  @CalledInAwt
  public abstract void blockModalNotifications();
  @CalledInAwt
  public abstract void unblockModalNotifications();

  /**
   * Temporarily disable CLM update
   * For example, to preserve FilePath->ChangeList mapping during "stash-do_smth-unstash" routine.
   */
  public abstract void freeze(@NotNull String reason);
  public abstract void unfreeze();
}