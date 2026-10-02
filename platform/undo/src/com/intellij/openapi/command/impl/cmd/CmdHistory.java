// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl.cmd;

import com.intellij.openapi.command.impl.CommandId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.stream.Collectors;


final class CmdHistory {
  private static final int HISTORY_SIZE = 10;

  private final @NotNull ArrayDeque<@NotNull CommandId> history = new ArrayDeque<>(HISTORY_SIZE);

  void add(@NotNull CommandId commandId) {
    history.add(commandId);
    if (history.size() > HISTORY_SIZE) {
      history.removeFirst();
    }
  }

  @Nullable CommandId getCurrentCommandId() {
    return history.peekLast();
  }

  @Nullable CommandId getPreviousCommandId() {
    if (history.size() > 1) {
      var iterator = history.descendingIterator();
      iterator.next();
      return iterator.next();
    }
    return null;
  }

  @Override
  public String toString() {
    String historyStr = history.stream()
      .map(Objects::toString)
      .collect(Collectors.joining(", "));
    return "CommandHistory{" + historyStr + '}';
  }
}
