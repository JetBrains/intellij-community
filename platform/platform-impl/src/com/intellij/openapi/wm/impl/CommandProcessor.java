// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.BooleanSupplier;

final class CommandProcessor implements Runnable {
  private static final Logger LOG = Logger.getInstance(CommandProcessor.class);

  private final Deque<List<Runnable>> commandGroups = new ArrayDeque<>();
  @NotNull
  private final BooleanSupplier isDisposedCondition;

  private int myCommandCount;
  private boolean isActive;

  CommandProcessor(@NotNull BooleanSupplier isDisposedCondition) {
    this.isDisposedCondition = isDisposedCondition;
  }

  public final int getCommandCount() {
    return myCommandCount;
  }

  public void activate() {
    isActive = true;
    run();
  }

  public final void execute(@NotNull List<Runnable> commands) {
    if (commands.isEmpty()) {
      return;
    }

    boolean isBusy = myCommandCount > 0 || !isActive;

    commandGroups.add(commands);
    myCommandCount += commands.size();

    if (!isBusy) {
      run();
    }
  }

  @Override
  public final void run() {
    while (true) {
      List<Runnable> commands = commandGroups.pollFirst();
      if (commands == null) {
        return;
      }

      for (Runnable command : commands) {
        myCommandCount--;

        if (isDisposedCondition.getAsBoolean()) {
          continue;
        }

        if (LOG.isDebugEnabled()) {
          LOG.debug("CommandProcessor.run " + command);
        }

        try {
          command.run();
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
    }
  }
}