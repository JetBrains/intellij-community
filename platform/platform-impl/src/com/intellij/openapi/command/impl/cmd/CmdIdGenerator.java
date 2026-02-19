// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl.cmd;

import com.intellij.openapi.command.impl.CommandId;
import org.jetbrains.annotations.NotNull;
import java.util.concurrent.atomic.AtomicLong;


abstract class CmdIdGenerator {

  // for debug purpose: command - even number, transparent - odd number
  private final AtomicLong generator = new AtomicLong(2);

  final @NotNull CommandId nextCommandId() {
    long commandId = generator.updateAndGet(id -> (id % 2 == 0) ? (id + 2) : (id + 1));
    return createId(commandId);
  }

  final @NotNull CommandId nextTransparentId() {
    long commandId = generator.updateAndGet(id -> (id % 2 != 0) ? (id + 2) : (id + 1));
    return createId(commandId);
  }

  protected abstract @NotNull CommandId createId(long commandId);
}
