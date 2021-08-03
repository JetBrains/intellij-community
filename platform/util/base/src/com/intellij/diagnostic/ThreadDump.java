// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.management.ThreadInfo;

/**
 * Represents thread dump of the IDE captured by its performance diagnostic tool.
 */
public class ThreadDump {
  private final String myRawDump;
  private final StackTraceElement[] myEdtStack;
  private final ThreadInfo[] myThreadInfos;

  ThreadDump(@NotNull String rawDump, StackTraceElement @Nullable [] edtStack, ThreadInfo @NotNull [] threadInfos) {
    myRawDump = rawDump;
    myEdtStack = edtStack;
    myThreadInfos = threadInfos;
  }

  /**
   * @return full thread dump as a string
   */
  @NotNull
  public String getRawDump() {
    return myRawDump;
  }

  /**
   * @return state of the AWT thread from the dump
   */
  public StackTraceElement @Nullable [] getEDTStackTrace() {
    return myEdtStack;
  }

  public ThreadInfo @NotNull [] getThreadInfos() {
    return myThreadInfos;
  }
}
