// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;


import com.intellij.reference.SoftReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.Objects;
import java.util.stream.Collectors;


record CommandMergerFlushReason(
  @NotNull String reason,
  @Nullable Command currentCommand,
  @Nullable Command nextCommand
) {
  static CommandMergerFlushReason CLEAR_STACKS = create("CLEAR_STACKS");
  static CommandMergerFlushReason CLEAR_QUEUE = create("CLEAR_QUEUE");
  static CommandMergerFlushReason GET_LAST_GROUP = create("GET_LAST_GROUP");
  static CommandMergerFlushReason MANAGER_FORCE = create("MANAGER_FORCE");
  static CommandMergerFlushReason UNDO = create("UNDO");
  static CommandMergerFlushReason REDO = create("REDO");

  static @NotNull CommandMergerFlushReason cannotMergeCommands(
    @NotNull String reason,
    @Nullable String currentCommandName,
    @Nullable Reference<Object> currentGroupId,
    boolean isCurrentTransparent,
    boolean isCurrentGlobal,
    @NotNull PerformedCommand nextCommand
  ) {
    CommandId nextCommandId = nextCommand.commandId();
    String nextCommandName = nextCommand.commandName();
    Object nextGroupId = nextCommand.groupId();
    boolean isNextTransparent = nextCommand.isTransparent();
    boolean isNextGlobal = nextCommand.isGlobal();
    return new CommandMergerFlushReason(
      reason,
      new Command(
        null,
        currentCommandName,
        currentGroupId,
        isCurrentTransparent,
        isCurrentGlobal
      ),
      new Command(
        nextCommandId,
        nextCommandName,
        nextGroupId,
        isNextTransparent,
        isNextGlobal
      )
    );
  }

  private static @NotNull CommandMergerFlushReason create(@NotNull String reason) {
    return new CommandMergerFlushReason(reason, null, null);
  }

  @Override
  public String toString() {
    if (currentCommand == null && nextCommand == null) {
      return "Reason{" + reason + '}';
    }
    return "Reason{" + reason +
           ", current=" + currentCommand +
           ", next=" + nextCommand +
           '}';
  }

  private record Command(
    @Nullable CommandId commandId,
    @Nullable String commandName,
    @Nullable Object groupId,
    boolean isTransparent,
    boolean isGlobal
  ) {
    @Override
    public String toString() {
      var str = new ArrayList<@Nullable Object>(3);
      str.add((commandName == null) ? null : ("'" + commandName + "''"));
      str.add((groupId instanceof Reference<?> ref) ? SoftReference.dereference(ref) : groupId);
      if (commandId != null) {
        str.add(commandId);
      }
      if (isTransparent) {
        str.add("transparent");
      }
      if (isGlobal) {
        str.add("global");
      }
      return str.stream()
        .map(Objects::toString)
        .collect(Collectors.joining(", ", "[", "]"));
    }
  }
}
