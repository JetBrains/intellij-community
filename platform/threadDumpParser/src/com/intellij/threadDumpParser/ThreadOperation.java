// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.threadDumpParser;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public enum ThreadOperation {
  SOCKET("socket operation"),
  IO("I/O"),
  CARRYING_VTHREAD("carrying virtual thread"),
  ;

  private final String myName;

  ThreadOperation(final String name) {
    myName = name;
  }

  @Override
  public String toString() {
    return myName;
  }
}
