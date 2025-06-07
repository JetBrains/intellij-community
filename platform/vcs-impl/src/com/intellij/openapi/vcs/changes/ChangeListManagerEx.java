// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

import java.util.Collection;
import java.util.List;

public abstract class ChangeListManagerEx extends ChangeListManager {
  public static @NotNull ChangeListManagerEx getInstanceEx(@NotNull Project project) {
    return (ChangeListManagerEx)getInstance(project);
  }

  public abstract boolean isInUpdate();

  public abstract @NotNull Collection<LocalChangeList> getAffectedLists(@NotNull Collection<? extends Change> changes);

  public abstract @NotNull LocalChangeList addChangeList(@NotNull @NonNls String name,
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

  /**
   * Wait until all current pending tasks are finished.
   *
   * @see #waitForUpdate()
   */
  public abstract @NotNull Promise<?> promiseWaitForUpdate();
}