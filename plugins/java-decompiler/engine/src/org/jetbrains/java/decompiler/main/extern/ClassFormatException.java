// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.main.extern;

public class ClassFormatException extends RuntimeException {
  public ClassFormatException(String message) {
    super(message);
  }
}
