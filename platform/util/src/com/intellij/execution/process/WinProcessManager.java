/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.process;

import com.intellij.util.ReflectionUtil;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;

/**
 * @author Alexey.Ushakov
 */
public class WinProcessManager {

  private WinProcessManager() {}

  /**
   * Returns {@code pid} for Windows process
   * @param process Windows process
   * @return pid of the {@code process}
   */
  public static int getProcessPid(Process process) {
    if (process.getClass().getName().equals("java.lang.Win32Process") ||
        process.getClass().getName().equals("java.lang.ProcessImpl")) {
      try {
        long handle = ReflectionUtil.getField(process.getClass(), process, long.class, "handle");

        Kernel32 kernel = Kernel32.INSTANCE;
        WinNT.HANDLE winHandle = new WinNT.HANDLE();
        winHandle.setPointer(Pointer.createConstant(handle));
        return kernel.GetProcessId(winHandle);
      } catch (Throwable e) {
        throw new IllegalStateException(e);
      }
    } else {
      throw new IllegalStateException("Unknown Process implementation");
    }
  }
}
