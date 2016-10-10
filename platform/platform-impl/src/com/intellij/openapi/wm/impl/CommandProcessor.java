/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.wm.impl.commands.FinalizableCommand;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class CommandProcessor implements Runnable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.wm.impl.CommandProcessor");
  private final Object myLock = new Object();

  private final List<CommandGroup> myCommandGroupList = new ArrayList<>();
  private int myCommandCount;
  private boolean myFlushed;

  public final int getCommandCount() {
    synchronized (myLock) {
      return myCommandCount;
    }
  }

  public void flush() {
    synchronized (myLock) {
      myFlushed = true;
      run();
    }
  }

  /**
   * Executes passed batch of commands. Note, that the processor surround the
   * commands with BlockFocusEventsCmd - UnblockFocusEventsCmd. It's required to
   * prevent focus handling of events which is caused by the commands to be executed.
   */
  public final void execute(@NotNull List<FinalizableCommand> commandList, @NotNull Condition expired) {
    synchronized (myLock) {
      final boolean isBusy = myCommandCount > 0 || !myFlushed;

      final CommandGroup commandGroup = new CommandGroup(commandList, expired);
      myCommandGroupList.add(commandGroup);
      myCommandCount += commandList.size();

      if (!isBusy) {
        run();
      }
    }
  }

  public final void run() {
    synchronized (myLock) {
      //noinspection StatementWithEmptyBody
      while (runNext()) {
      }
    }
  }

  private boolean runNext() {
    final CommandGroup commandGroup = getNextCommandGroup();
    if (commandGroup == null || commandGroup.isEmpty()) return false;
    final Condition conditionForGroup = commandGroup.getExpireCondition();

    final FinalizableCommand command = commandGroup.takeNextCommand();
    myCommandCount--;

    Condition expire = command.getExpireCondition() != null ? command.getExpireCondition() : conditionForGroup;
    if (expire == null) expire = ApplicationManager.getApplication().getDisposed();
    if (expire.value(null)) return true;
    if (LOG.isDebugEnabled()) {
      LOG.debug("CommandProcessor.run " + command);
    }

    command.run();
    return true;
  }

  @Nullable
  private CommandGroup getNextCommandGroup() {
    while (!myCommandGroupList.isEmpty()) {
      final CommandGroup candidate = myCommandGroupList.get(0);
      if (!candidate.isEmpty()) {
        return candidate;
      }
      myCommandGroupList.remove(candidate);
    }

    return null;
  }

  private static class CommandGroup {
    private final List<FinalizableCommand> myList;
    private Condition myExpireCondition;

    private CommandGroup(@NotNull List<FinalizableCommand> list, @NotNull Condition expireCondition) {
      myList = list;
      myExpireCondition = expireCondition;
    }

    public Condition getExpireCondition() {
      return myExpireCondition;
    }

    public boolean isEmpty() {
      return myList.isEmpty();
    }

    public FinalizableCommand takeNextCommand() {
      FinalizableCommand command = myList.remove(0);
      if (isEmpty()) {
        // memory leak otherwise
        myExpireCondition = Conditions.alwaysTrue();
      }
      return command;
    }
  }
}
