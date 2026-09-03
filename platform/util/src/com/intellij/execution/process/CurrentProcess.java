// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** The id of this process through {@code ProcessHandle.current().pid()}, reached by method handles because the module compiles for Java 8. */
final class CurrentProcess {
  private CurrentProcess() { }

  /** {@code 0} until the first successful read; a process id is never {@code 0}. */
  private static volatile int ourPid;

  /** @throws IllegalStateException on a Java runtime older than 9 */
  static int pid() {
    int pid = ourPid;
    if (pid == 0) {
      pid = read();
      ourPid = pid;
    }
    return pid;
  }

  private static int read() {
    try {
      Class<?> processHandle = Class.forName("java.lang.ProcessHandle");
      MethodHandles.Lookup lookup = MethodHandles.publicLookup();
      MethodHandle current = lookup.findStatic(processHandle, "current", MethodType.methodType(processHandle));
      MethodHandle pid = lookup.findVirtual(processHandle, "pid", MethodType.methodType(long.class));
      return (int)(long)pid.invoke(current.invoke());
    }
    catch (Throwable t) {
      throw new IllegalStateException("Cannot read the process id, Java: " + System.getProperty("java.version"), t);
    }
  }
}
