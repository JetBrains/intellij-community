// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import org.jetbrains.annotations.NotNull;


final class IllegalUndoStateException extends RuntimeException {

  IllegalUndoStateException(@NotNull String message) {
    super(message);
  }
}
