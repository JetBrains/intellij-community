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

import com.intellij.openapi.vcs.changes.local.*;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;

/** synchronization aspect is external for this class; only logic here
 * have internal command queue; applies commands to another copy of change lists (ChangeListWorker) and sends notifications
 * (after update is done)
 */
public class Modifier implements ChangeListsWriteOperations {
  private ChangeListWorker myWorker;
  private boolean myInsideUpdate;
  private final List<ChangeListCommand> myCommandQueue;
  private final DelayedNotificator myNotificator;

  public Modifier(ChangeListWorker worker, DelayedNotificator notificator) {
    myWorker = worker;
    myNotificator = notificator;
    myCommandQueue = new LinkedList<>();
  }

  @NotNull
  @Override
  public LocalChangeList addChangeList(@NotNull String name, @Nullable String comment, @Nullable Object data) {
    AddList command = new AddList(name, comment, data);
    impl(command);
    return command.getNewListCopy();
  }

  @Nullable
  @Override
  public String setDefault(String name) {
    SetDefault command = new SetDefault(name);
    impl(command);
    return command.getPrevious();
  }

  @Override
  public boolean removeChangeList(@NotNull String name) {
    RemoveList command = new RemoveList(name);
    impl(command);
    return command.isRemoved();
  }

  @Nullable
  @Override
  public MultiMap<LocalChangeList, Change> moveChangesTo(String name, @NotNull Change[] changes) {
    MoveChanges command = new MoveChanges(name, changes);
    impl(command);
    return command.getMovedFrom();
  }

  private void impl(ChangeListCommand command) {
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

  @Override
  public boolean setReadOnly(String name, boolean value) {
    SetReadOnly command = new SetReadOnly(name, value);
    impl(command);
    return command.isResult();
  }

  @Override
  public boolean editName(@NotNull String fromName, @NotNull String toName) {
    EditName command = new EditName(fromName, toName);
    impl(command);
    return command.isResult();
  }

  @Nullable
  @Override
  public String editComment(@NotNull String fromName, String newComment) {
    EditComment command = new EditComment(fromName, newComment);
    impl(command);
    return command.getOldComment();
  }

  public boolean isInsideUpdate() {
    return myInsideUpdate;
  }

  public void enterUpdate() {
    myInsideUpdate = true;
  }

  public void finishUpdate(@Nullable ChangeListWorker worker) {
    myInsideUpdate = false;

    if (worker != null) {
      // re-apply commands to the new worker
      for (ChangeListCommand command : myCommandQueue) {
        command.apply(worker);
      }
      myWorker = worker;
    }

    for (ChangeListCommand command : myCommandQueue) {
      myNotificator.callNotify(command);
    }
    myCommandQueue.clear();
  }
}
