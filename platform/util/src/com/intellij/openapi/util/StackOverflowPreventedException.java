// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

public class StackOverflowPreventedException extends RuntimeException {
  public StackOverflowPreventedException(String message) {
    super(message);
  }
}
