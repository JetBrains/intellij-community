// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl.cmd;

import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.impl.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CmdEventTransform {
  private static final @NotNull CmdEventTransform INSTANCE = new CmdEventTransform();

  public static @NotNull CmdEventTransform getInstance() {
    return INSTANCE;
  }

  private final @NotNull CmdIdGenerator idGenerator = new CmdIdGenerator();

  public @NotNull CmdEvent create(@Nullable CommandEvent event, boolean isStart) {
    CmdEvent foreignCommand = ForeignCommandProcessor.getInstance().currentCommand();
    if (foreignCommand != null) {
      return foreignCommand;
    }
    boolean isTransparent = event == null;
    CommandId commandId = getCommandId(isTransparent, isStart);
    var meta = isStart
               ? CmdMeta.createMutable()
               : CmdMeta.createEmpty();
    return isTransparent
           ? CmdEvent.createTransparent(commandId, meta)
           : CmdEvent.create(event, commandId, meta);
  }

  public @NotNull CmdEvent createNonUndoable() {
    return CmdEvent.createNonUndoable(idGenerator.nextCommandId(), NoCmdMeta.INSTANCE);
  }

  private @NotNull CommandId getCommandId(boolean isTransparent, boolean isStart) {
    if (isTransparent) {
      return isStart ? idGenerator.nextTransparentId() : idGenerator.currentId();
    }
    return isStart ? idGenerator.nextCommandId() : idGenerator.currentId();
  }
}
