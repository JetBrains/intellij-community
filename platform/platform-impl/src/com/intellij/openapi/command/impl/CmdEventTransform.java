// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.CommandEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class CmdEventTransform {

  private static final @NotNull CmdEventTransform INSTANCE = new CmdEventTransform();

  static @NotNull CmdEventTransform getInstance() {
    return INSTANCE;
  }

  private final @NotNull CommandIdGenerator idGenerator = new CommandIdGenerator();

  @NotNull CmdEvent create(@Nullable CommandEvent event, boolean isStart) {
    CmdEvent foreignCommand = ForeignCommandProcessor.getInstance().currentCommand();
    if (foreignCommand != null) {
      return foreignCommand;
    }
    boolean isTransparent = event == null;
    CommandId commandId = getCommandId(isTransparent, isStart);
    var meta = isStart
               ? new MutableCommandMetaImpl(commandId)
               : new NoCommandMeta(commandId);
    return isTransparent
           ? CmdEvent.createTransparent(null, meta)
           : CmdEvent.create(event, meta);
  }

  @NotNull CmdEvent createNonUndoable() {
    CommandMeta meta = new NoCommandMeta(idGenerator.nextCommandId());
    return CmdEvent.createNonUndoable(meta);
  }

  private @NotNull CommandId getCommandId(boolean isTransparent, boolean isStart) {
    if (isTransparent) {
      return isStart ? idGenerator.nextTransparentId() : idGenerator.currentCommandId();
    }
    return isStart ? idGenerator.nextCommandId() : idGenerator.currentCommandId();
  }
}
