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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public abstract class ChangeListManagerEx extends ChangeListManager {
  @NotNull
  public static ChangeListManagerEx getInstanceEx(@NotNull Project project) {
    return (ChangeListManagerEx)getInstance(project);
  }

  public abstract boolean isInUpdate();

  @NotNull
  public abstract Collection<LocalChangeList> getAffectedLists(@NotNull Collection<? extends Change> changes);

  @NotNull
  public abstract LocalChangeList addChangeList(@NotNull @NonNls String name,
                                                @Nullable @NonNls String comment,
                                                @Nullable ChangeListData data);

  public abstract boolean editChangeListData(@NotNull @NonNls String name, @Nullable ChangeListData newData);

  /**
   * @param automatic true is changelist switch operation was not triggered by user (and, for example, will be reverted soon)
   *                  4ex: This flag disables automatic empty changelist deletion.
   */
  public abstract void setDefaultChangeList(@NotNull LocalChangeList list, boolean automatic);

  /**
   * Add unversioned files into VCS under modal progress dialog
   *
   * @see com.intellij.openapi.vcs.changes.actions.ScheduleForAdditionAction
   */
  public abstract void addUnversionedFiles(@Nullable LocalChangeList list, @NotNull List<? extends VirtualFile> unversionedFiles);

  /**
   * Blocks modal dialogs that we don't want to popup during some process, for example, above the commit dialog.
   * They will be shown when notifications are unblocked.
   */
  @RequiresEdt
  public abstract void blockModalNotifications();

  @RequiresEdt
  public abstract void unblockModalNotifications();

  /**
   * Temporarily disable CLM update.
   * For example, to preserve FilePath->ChangeList mapping during "stash-do_smth-unstash" routine.
   */
  public abstract void freeze(@NotNull @Nls String reason);

  public abstract void unfreeze();

  /**
   * Wait until all current pending tasks are finished.
   * <p>
   * Do not execute this method while holding the read lock - it might be a long operation,
   * and CLM update can trigger synchronous VFS refresh that needs an EDT callback (causing a deadlock).
   *
   * @see #invokeAfterUpdate(boolean, Runnable)
   */
  @RequiresBackgroundThread
  public abstract void waitForUpdate();
}