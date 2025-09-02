// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;


import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;


record UndoCommandFlushReason(
  @NotNull String reason,
  @Nullable Command currentCommand,
  @Nullable Command nextCommand
) {
  static UndoCommandFlushReason CLEAR_STACKS = new UndoCommandFlushReason("CLEAR_STACKS");
  static UndoCommandFlushReason CLEAR_QUEUE = new UndoCommandFlushReason("CLEAR_QUEUE");
  static UndoCommandFlushReason GET_LAST_GROUP = new UndoCommandFlushReason("GET_LAST_GROUP");
  static UndoCommandFlushReason MANAGER_FORCE = new UndoCommandFlushReason("MANAGER_FORCE");
  static UndoCommandFlushReason UNDO = new UndoCommandFlushReason("UNDO");
  static UndoCommandFlushReason REDO = new UndoCommandFlushReason("REDO");

  static @NotNull UndoCommandFlushReason cannotMergeCommands(
    @NotNull String reason,
    @Nullable String currentCommandName,
    @Nullable Object currentGroupId,
    boolean isCurrentTransparent,
    boolean isCurrentGlobal,
    @Nullable String nextCommandName,
    @Nullable Object nextGroupId,
    boolean isNextTransparent,
    boolean isNextGlobal
  ) {
    return new UndoCommandFlushReason(
      reason,
      new Command(
        currentCommandName,
        currentGroupId,
        isCurrentTransparent,
        isCurrentGlobal
      ),
      new Command(
        nextCommandName,
        nextGroupId,
        isNextTransparent,
        isNextGlobal
      )
    );
  }

  private UndoCommandFlushReason(@NotNull String reason) {
    this(reason, null, null);
  }

  @Override
  public String toString() {
    if (SHORT_NAMES.contains(this)) {
      return "Reason{" + reason + '}';
    }
    return "Reason{" + reason +
           ", current=" + currentCommand +
           ", next=" + nextCommand +
           '}';
  }

  private static final Set<UndoCommandFlushReason> SHORT_NAMES = Set.of(
    CLEAR_STACKS,
    CLEAR_QUEUE,
    GET_LAST_GROUP,
    MANAGER_FORCE,
    UNDO,
    REDO
  );

  private record Command(
    @Nullable String name,
    @Nullable String groupId,
    boolean isTransparent,
    boolean isGlobal
  ) {
    Command(
      @Nullable String commandName,
      @Nullable Object groupId,
      boolean isTransparent,
      boolean isGlobal
    ) {
      this(
        commandName,
        groupId == null ? null : groupId.toString(),
        isTransparent,
        isGlobal
      );
    }

    @Override
    public String toString() {
      return "Command['%s', %s%s%s]".formatted(
        name,
        groupId,
        isTransparent ? ", transparent" : "",
        isGlobal ? ", global" : ""
      );
    }
  }
}
