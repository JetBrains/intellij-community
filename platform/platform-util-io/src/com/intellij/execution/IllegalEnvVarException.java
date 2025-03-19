// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution;

import com.intellij.openapi.util.NlsContexts.DialogMessage;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class IllegalEnvVarException extends ExecutionException {
  public IllegalEnvVarException(@DialogMessage String message) {
    super(message);
  }
}