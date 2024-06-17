// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.lang;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
class MissingIkvException extends RuntimeException {
  MissingIkvException(String message) {
    super(message);
  }
}
