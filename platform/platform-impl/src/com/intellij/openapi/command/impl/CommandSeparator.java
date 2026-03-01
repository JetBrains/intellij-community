// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.command.impl.cmd.CmdEvent;
import com.intellij.openapi.command.impl.cmd.CmdEventTransform;
import com.intellij.openapi.progress.ProgressManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;


/**
 * CommandSeparator prevents overlapping of commands and transparent actions by the following rules:
 * <p>
 * [Cs, (Ts, Tf), Cf] -- Ts and Tf are ignored
 * <p>
 * (Ts, [Cs, Cf], Tf) -- Cs and Cf are ignored
 * <p>
 * (Ts, [Cs, Tf), Cf] -- Cf and Tf are ignored, Cf is actual Tf
 * <p>
 * [Cs, (Ts, Cf], Tf) -- Ts is ignored, Cf is pair Cf Ts
 * <p>
 * where Cs -- command started, Cf -- command finished, Ts -- transparent started, Tf -- transparent finished
 * <p>
 */
@ApiStatus.Internal
public final class CommandSeparator implements CommandListener {
  private final @NotNull SeparatedCommandListener publisher;
  private boolean commandStarted;
  private boolean transparentStarted;
  private boolean commandInsideTransparent;
  private boolean transparentInsideCommand;

  @SuppressWarnings("unused")
  CommandSeparator() {
    this(getPublisher());
  }

  public CommandSeparator(@NotNull SeparatedCommandListener publisher) {
    this.publisher = publisher;
  }

  @Override
  public void commandStarted(@NotNull CommandEvent event) {
    assertOutsideCommand();
    commandStarted = true;
    if (transparentStarted) {
      commandInsideTransparent = true;
    } else {
      notifyCommandStarted(event);
    }
  }

  @Override
  public void commandFinished(@NotNull CommandEvent event) {
    assertInsideCommand();
    commandStarted = false;
    if (commandInsideTransparent) {
      commandInsideTransparent = false;
    } else {
      notifyCommandFinished(event);
    }
    if (transparentInsideCommand) {
      transparentInsideCommand = false;
      notifyTransparentStarted();
    }
  }

  @Override
  public void undoTransparentActionStarted() {
    assertOutsideTransparent();
    transparentStarted = true;
    if (commandStarted) {
      transparentInsideCommand = true;
    } else {
      notifyTransparentStarted();
    }
  }

  @Override
  public void undoTransparentActionFinished() {
    assertInsideTransparent();
    transparentStarted = false;
    if (transparentInsideCommand) {
      transparentInsideCommand = false;
    } else {
      notifyTransparentFinished();
    }
  }

  @TestOnly
  public boolean isInitialState() {
    return !commandStarted &&
           !transparentStarted &&
           !commandInsideTransparent &&
           !transparentInsideCommand;
  }

  private void notifyCommandStarted(@NotNull CommandEvent event) {
    notifyCommand(event, true);
  }

  private void notifyCommandFinished(@NotNull CommandEvent event) {
    notifyCommand(event, false);
  }

  private void notifyTransparentStarted() {
    notifyCommand(null, true);
  }

  private void notifyTransparentFinished() {
    notifyCommand(null, false);
  }

  private void notifyCommand(@Nullable CommandEvent event, boolean isStart) {
    CmdEvent cmdEvent = ProgressManager.getInstance().computeInNonCancelableSection(
      () -> CmdEventTransform.getInstance().create(event, isStart)
    );
    if (isStart) {
      publisher.onCommandStarted(cmdEvent);
    } else {
      publisher.onCommandFinished(cmdEvent);
    }
    UndoSpy undoSpy = UndoSpy.getInstance();
    if (undoSpy != null) {
      if (isStart) {
        undoSpy.commandStarted(cmdEvent);
      } else {
        undoSpy.commandFinished(cmdEvent);
      }
    }
  }

  private void assertInsideCommand() {
    if (!commandStarted) {
      throw new IllegalStateException("Command not started");
    }
  }

  private void assertOutsideCommand() {
    if (commandStarted) {
      throw new IllegalStateException("Command already started");
    }
  }

  private void assertInsideTransparent() {
    if (!transparentStarted) {
      throw new IllegalStateException("Transparent action not started");
    }
  }

  private void assertOutsideTransparent() {
    if (transparentStarted) {
      throw new IllegalStateException("Transparent action already started");
    }
  }

  private static @NotNull SeparatedCommandListener getPublisher() {
    return ApplicationManager.getApplication()
      .getMessageBus()
      .syncPublisher(SeparatedCommandListener.TOPIC);
  }

  @Override
  public String toString() {
    return "CommandSeparator{" +
           "commandStarted=" + commandStarted +
           ", transparentStarted=" + transparentStarted +
           ", commandInsideTransparent=" + commandInsideTransparent +
           ", transparentInsideCommand=" + transparentInsideCommand +
           '}';
  }
}
