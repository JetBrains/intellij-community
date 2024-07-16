// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remote.ext;

public final class CannotLoadCredentialsException extends Exception {
  public CannotLoadCredentialsException(String message) {
    super(message);
  }
}
