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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** synchronization aspect is external for this class; only logic here
 * have internal command queue; applies commands to another copy of change lists (ChangeListWorker) and sends notifications
 * (after update is done)
 */
public class Modifier {
  private final ChangeListWorker myWorker;
  private boolean myInsideUpdate;
  private final List<ChangeListCommand> myCommandQueue;
  private final DelayedNotificator myNotificator;

  public Modifier(ChangeListWorker worker, DelayedNotificator notificator) {
    myWorker = worker;
    myNotificator = notificator;
    myCommandQueue = new ArrayList<>();
  }

  @NotNull
  public LocalChangeList addChangeList(@NotNull String name, @Nullable String comment, @Nullable ChangeListData data) {
    AddList command = new AddList(name, comment, data);
    impl(command);
    return command.getNewListCopy();
  }

  public void setDefault(String name) {
    SetDefault command = new SetDefault(name);
    impl(command);
  }

  public void removeChangeList(@NotNull String name) {
    RemoveList command = new RemoveList(name);
    impl(command);
  }

  public void moveChangesTo(String name, @NotNull Change[] changes) {
    MoveChanges command = new MoveChanges(name, changes);
    impl(command);
  }

  public boolean setReadOnly(String name, boolean value) {
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


  public boolean isInsideUpdate() {
    return myInsideUpdate;
  }

  public void enterUpdate() {
    myInsideUpdate = true;
  }

  public void finishUpdate(@Nullable ChangeListWorker updatedWorker) {
    myInsideUpdate = false;

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
