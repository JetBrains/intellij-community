// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.threadDumpParser;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public enum ThreadOperation {
  Socket("socket operation"), IO("I/O");

  private final String myName;

  ThreadOperation(final String name) {
    myName = name;
  }

  public String toString() {
    return myName;
  }
}
