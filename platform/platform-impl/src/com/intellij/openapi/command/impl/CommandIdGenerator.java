// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;


final class CommandIdGenerator {

  // for debug purpose: command - even number, transparent - odd number
  private final AtomicLong idGenerator = new AtomicLong();
  private final AtomicReference<CommandId> currentId = new AtomicReference<>();

  @NotNull CommandId nextCommandId() {
    long commandId = idGenerator.updateAndGet(id -> (id % 2 == 0) ? (id + 2) : (id + 1));
    return createId(commandId);
  }

  @NotNull CommandId nextTransparentId() {
    long commandId = idGenerator.updateAndGet(id -> (id % 2 != 0) ? (id + 2) : (id + 1));
    return createId(commandId);
  }

  @NotNull CommandId currentCommandId() {
    return currentId.get();
  }

  private @NotNull CommandId createId(long commandId) {
    CommandId id = CommandId.fromLong(commandId);
    currentId.set(id);
    return id;
  }
}
