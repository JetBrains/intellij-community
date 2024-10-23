// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.changes.local.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * synchronization aspect is external for this class; only logic here
 * have internal command queue; applies commands to another copy of change lists (ChangeListWorker) and sends notifications
 * (after update is done)
 */
@ApiStatus.Internal
public class Modifier {
  private static final Logger LOG = Logger.getInstance(Modifier.class);

  private final ChangeListWorker myWorker;
  private volatile boolean myInsideUpdate;
  private final List<ChangeListCommand> myCommandQueue;
  private final DelayedNotificator myNotificator;

  public Modifier(@NotNull ChangeListWorker worker, @NotNull DelayedNotificator notificator) {
    myWorker = worker;
    myNotificator = notificator;
    myCommandQueue = new ArrayList<>();
  }

  @NotNull
  public LocalChangeList addChangeList(@NotNull String name, @Nullable String comment, @Nullable ChangeListData data) {
    AddList command = new AddList(name, comment, data);
    impl(command);
    LocalChangeList newList = command.getNewListCopy();
    return newList != null ? newList : myWorker.getDefaultList();
  }

  public void setDefault(@NotNull String name, boolean automatic) {
    SetDefault command = new SetDefault(name, automatic);
    impl(command);
  }

  public void removeChangeList(@NotNull String name) {
    RemoveList command = new RemoveList(name);
    impl(command);
  }

  public void moveChangesTo(@NotNull String name, @NotNull List<? extends Change> changes) {
    MoveChanges command = new MoveChanges(name, changes);
    impl(command);
  }

  public boolean setReadOnly(@NotNull String name, boolean value) {
    SetReadOnly command = new SetReadOnly(name, value);
    impl(command);
    return command.isResult();
  }

  public boolean editName(@NotNull String fromName, @NotNull String toName) {
    EditName command = new EditName(fromName, toName);
    impl(command);
    return command.isResult();
  }

  @Nullable
  public String editComment(@NotNull String fromName, @NotNull String newComment) {
    EditComment command = new EditComment(fromName, newComment);
    impl(command);
    return command.getOldComment();
  }

  public boolean editData(@NotNull String fromName, @Nullable ChangeListData newData) {
    EditData command = new EditData(fromName, newData);
    impl(command);
    return command.isResult();
  }


  private void impl(@NotNull ChangeListCommand command) {
    if (!myWorker.areChangeListsEnabled()) {
      LOG.warn("Changelists are disabled, command ignored", new Throwable());
      return;
    }
    if (myInsideUpdate) {
      // apply command and store it to be applied again when update is finished
      // notification about this invocation might be sent later if the update is cancelled
      command.apply(myWorker);
      myCommandQueue.add(command);
    }
    else {
      // apply and notify immediately
      command.apply(myWorker);
      myNotificator.callNotify(command);
    }
  }


  public boolean isInsideUpdate() {
    return myInsideUpdate;
  }

  public void enterUpdate() {
    myInsideUpdate = true;
  }

  public void finishUpdate(@Nullable ChangeListWorker updatedWorker) {
    myInsideUpdate = false;

    if (!myWorker.areChangeListsEnabled()) {
      if (!myCommandQueue.isEmpty()) LOG.warn("Changelists are disabled, commands ignored: " + myCommandQueue);
      myCommandQueue.clear();
      return;
    }

    if (updatedWorker != null) {
      for (ChangeListCommand command : myCommandQueue) {
        command.apply(updatedWorker);
      }
    }

    for (ChangeListCommand command : myCommandQueue) {
      myNotificator.callNotify(command);
    }
    myCommandQueue.clear();
  }
}
