// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;


/**
 * Cs -- command started, Cf -- command finished, Ts -- transparent started, Tf -- transparent finished.
 * <p>
 * [Cs, (Ts, Tf), Cf] -- Ts and Tf are ignored
 * <p>
 * (Ts, [Cs, Cf], Tf) -- Cs and Cf are ignored
 * <p>
 * (Ts, [Cs, Tf), Cf] -- Cf and Tf are ignored, Cf is actual Tf
 * <p>
 * [Cs, (Ts, Cf], Tf) -- Ts is ignored, Cf is pair Cf Ts
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
    notifyCommandStarted(createCmdEvent(event));
  }

  private void notifyCommandFinished(@NotNull CommandEvent event) {
    notifyCommandFinished(createCmdEvent(event));
  }

  private void notifyTransparentStarted() {
    notifyCommandStarted(createTransparentCmdEvent());
  }

  private void notifyTransparentFinished() {
    notifyCommandFinished(createTransparentCmdEvent());
  }

  private void notifyCommandStarted(@NotNull CmdEvent cmdEvent) {
    publisher.onCommandStarted(cmdEvent);
    UndoSpy undoSpy = UndoSpy.getInstance();
    if (undoSpy != null) {
      undoSpy.commandStarted(cmdEvent);
    }
  }

  private void notifyCommandFinished(@NotNull CmdEvent cmdEvent) {
    publisher.onCommandFinished(cmdEvent);
    UndoSpy undoSpy = UndoSpy.getInstance();
    if (undoSpy != null) {
      undoSpy.commandFinished(cmdEvent);
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

  private static @NotNull CmdEvent createCmdEvent(@NotNull CommandEvent event) {
    return CmdEvent.create(
      CommandIdService.currCommandId(),
      event.getProject(),
      event.getCommandName(),
      event.getCommandGroupId(),
      event.getUndoConfirmationPolicy(),
      event.shouldRecordActionForOriginalDocument(),
      false
    );
  }

  private static @NotNull CmdEvent createTransparentCmdEvent() {
    return CmdEvent.create(
      CommandIdService.currCommandId(),
      null,
      "",
      null,
      UndoConfirmationPolicy.DEFAULT,
      false,
      true
    );
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
