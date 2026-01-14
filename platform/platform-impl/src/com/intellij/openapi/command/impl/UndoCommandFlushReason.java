// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;


import com.intellij.reference.SoftReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;


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
    return new UndoCommandFlushReason(
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
