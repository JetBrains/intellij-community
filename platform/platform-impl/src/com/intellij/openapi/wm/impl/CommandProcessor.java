// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.wm.impl.commands.FinalizableCommand;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class CommandProcessor implements Runnable {
  private static final Logger LOG = Logger.getInstance(CommandProcessor.class);
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
  public final void execute(@NotNull List<? extends FinalizableCommand> commandList, @NotNull BooleanSupplier expired) {
    synchronized (myLock) {
      boolean isBusy = myCommandCount > 0 || !myFlushed;

      CommandGroup commandGroup = new CommandGroup(commandList, expired);
      myCommandGroupList.add(commandGroup);
      myCommandCount += commandList.size();

      if (!isBusy) {
        run();
      }
    }
  }

  @Override
  public final void run() {
    synchronized (myLock) {
      //noinspection StatementWithEmptyBody
      while (runNext()) ;
    }
  }

  private boolean runNext() {
    CommandGroup commandGroup = getNextCommandGroup();
    if (commandGroup == null || commandGroup.isEmpty()) {
      return false;
    }

    BooleanSupplier conditionForGroup = commandGroup.getExpireCondition();

    FinalizableCommand command = commandGroup.takeNextCommand();
    myCommandCount--;

    BooleanSupplier expire = command.getExpireCondition() == null ? conditionForGroup : command.getExpireCondition();
    if (expire == null ? ApplicationManager.getApplication().isDisposed() : expire.getAsBoolean()) {
      return true;
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("CommandProcessor.run " + command);
    }

    command.run();
    return true;
  }

  @Nullable
  private CommandGroup getNextCommandGroup() {
    while (!myCommandGroupList.isEmpty()) {
      CommandGroup candidate = myCommandGroupList.get(0);
      if (!candidate.isEmpty()) {
        return candidate;
      }
      myCommandGroupList.remove(candidate);
    }
    return null;
  }

  private static final class CommandGroup {
    private List<? extends FinalizableCommand> myList;
    private BooleanSupplier myExpireCondition;

    private CommandGroup(@NotNull List<? extends FinalizableCommand> list, @NotNull BooleanSupplier expireCondition) {
      myList = list;
      myExpireCondition = expireCondition;
    }

    @NotNull
    BooleanSupplier getExpireCondition() {
      return myExpireCondition;
    }

    public boolean isEmpty() {
      return myList == null || myList.isEmpty();
    }

    @NotNull
    FinalizableCommand takeNextCommand() {
      FinalizableCommand command;
      // if singleton list, do not mutate, just get first element and set to null
      if (myList.size() == 1) {
        command = myList.get(0);
        myList = null;
      }
      else {
        command = myList.remove(0);
      }

      if (isEmpty()) {
        // memory leak otherwise
        myExpireCondition = () -> true;
      }
      return command;
    }
  }
}