// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl.cmd;

import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.impl.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;


public final class CmdEventTransform {

  private static final @NotNull CmdEventTransform INSTANCE = new CmdEventTransform();

  public static @NotNull CmdEventTransform getInstance() {
    return INSTANCE;
  }

  public @NotNull CmdEvent create(@Nullable CommandEvent event, boolean isStart) {
    CmdEvent foreignCommand = ForeignCommandProcessor.getInstance().currentCommand();
    if (foreignCommand != null) {
      return foreignCommand;
    }
    boolean isTransparent = event == null;
    CommandId commandId = getCommandId(isTransparent, isStart);
    CmdMeta meta = CmdMeta.createMutable();
    return isTransparent
           ? CmdEvent.createTransparent(commandId, false, meta)
           : CmdEvent.create(event, commandId, meta);
  }

  public @NotNull CmdEvent createNonUndoable() {
    CommandId commandId = CmdIdService.getInstance().nextCommandId(false);
    return CmdEvent.createNonUndoable(commandId, CmdMeta.createEmpty());
  }

  private static @NotNull CommandId getCommandId(boolean isTransparent, boolean isStart) {
    CmdIdService idService = CmdIdService.getInstance();
    return isStart
      ? idService.nextCommandId(isTransparent)
      : Objects.requireNonNull(idService.currentCommandId(), "Command finished without start");
  }
}
