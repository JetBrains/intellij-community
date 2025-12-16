// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandId;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.command.undo.CommandMeta;
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

  static final @NotNull CommandIdGenerator ID_GENERATOR = new CommandIdGenerator();

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
    start(event);
  }

  private void notifyCommandFinished(@NotNull CommandEvent event) {
    finish(event);
  }

  private void notifyTransparentStarted() {
    start(null);
  }

  private void notifyTransparentFinished() {
    finish(null);
  }

  private void start(@Nullable CommandEvent event) {
    UndoCommandMeta meta = getUndoCommandMeta(event, true);
    publisher.onCommandStarted(event, meta);
    UndoSpy undoSpy = UndoSpy.getInstance();
    if (undoSpy != null) {
      undoSpy.commandStarted(event, meta);
    }
  }

  private void finish(@Nullable CommandEvent event) {
    UndoCommandMeta meta = getUndoCommandMeta(event, false);
    publisher.onCommandFinished(event, meta);
    UndoSpy undoSpy = UndoSpy.getInstance();
    if (undoSpy != null) {
      undoSpy.commandFinished(event, meta);
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

  private static @NotNull UndoCommandMeta getUndoCommandMeta(@Nullable CommandEvent event, boolean isStart) {
    if (event == null) {
      // domestic transparent
      CommandId commandId = isStart ? ID_GENERATOR.nextTransparentId() : ID_GENERATOR.currentCommandId();
      return isStart ? new DomesticCommandMeta(commandId) : new NoCommandMeta(commandId);
    }
    CommandMeta meta = event.getCommandMeta();
    if (meta == null) {
      // domestic command
      CommandId commandId = isStart ? ID_GENERATOR.nextCommandId() : ID_GENERATOR.currentCommandId();
      return isStart ? new DomesticCommandMeta(commandId) : new NoCommandMeta(commandId);
    }
    // foreign command or foreign transparent
    return (UndoCommandMeta) meta;
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
