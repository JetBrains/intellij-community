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

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.CalledInBackground;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public abstract class ChangeListManagerEx extends ChangeListManager {
  private static final Logger LOG = Logger.getInstance(ChangeListManagerEx.class);

  public abstract boolean isInUpdate();

  @NotNull
  public abstract Collection<LocalChangeList> getAffectedLists(@NotNull Collection<? extends Change> changes);

  @NotNull
  public abstract LocalChangeList addChangeList(@NotNull String name, @Nullable String comment, @Nullable ChangeListData data);

  public abstract boolean editChangeListData(@NotNull String name, @Nullable ChangeListData newData);

  /**
   * @param automatic true is changelist switch operation was not triggered by user (and, for example, will be reverted soon)
   *                  4ex: This flag disables automatic empty changelist deletion.
   */
  public abstract void setDefaultChangeList(@NotNull LocalChangeList list, boolean automatic);

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

  /**
   * Simulate synchronous task execution.
   * Do not execute such methods from EDT - cause CLM update can trigger synchronous VFS refresh,
   * that is waiting for EDT.
   */
  @CalledInBackground
  public abstract void waitForUpdate(@Nullable String operationName);
}