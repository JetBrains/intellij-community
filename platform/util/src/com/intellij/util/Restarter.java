/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.util;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.SystemInfo;
import com.sun.jna.Native;
import com.sun.jna.WString;
import com.sun.jna.win32.StdCallLibrary;

import java.io.File;
import java.io.IOException;

public class Restarter {
  private Restarter() {
  }

  public static int getRestartCode() {
    String s = System.getProperty("jb.restart.code");
    if (s != null) {
      try {
        return Integer.parseInt(s);
      }
      catch (NumberFormatException ignore) {
      }
    }
    return 0;
  }

  public static boolean isSupported() {
    return SystemInfo.isWindows || SystemInfo.isMac;
  }

  public static boolean restart() throws CannotRestartException {
    try {
      if (SystemInfo.isWindows) {
        return restartOnWindows();
      }
      else if (SystemInfo.isMac) {
        return restartOnMac();
      }
    }
    catch (CannotRestartException e) {
      throw e;
    }
    catch (Throwable e) {
      throw new CannotRestartException(e);
    }
    return false;
  }

  private static boolean restartOnWindows() throws CannotRestartException {
    Kernel32 kernel32 = (Kernel32)Native.loadLibrary("kernel32", Kernel32.class);
    WString cline = kernel32.GetCommandLineW();
    int pid = kernel32.GetCurrentProcessId();

    try {
      String command = "restarter " + Integer.toString(pid) + " " + cline;
      Runtime.getRuntime().exec(command, null, new File(PathManager.getBinPath()));
    }
    catch (IOException ex) {
      throw new CannotRestartException(ex);
    }

    // Since the process ID is passed through the command line, we want to make sure that we don't exit before the "restarter"
    // process has a chance to open the handle to our process, and that it doesn't wait for the termination of an unrelated
    // process which happened to have the same process ID.
    try {
      Thread.sleep(500);
    }
    catch (InterruptedException e1) {
      // ignore
    }
    return true;
  }

  private interface Kernel32 extends StdCallLibrary {
    WString GetCommandLineW();

    int GetCurrentProcessId();
  }

  private static boolean restartOnMac() throws CannotRestartException {
    String binPath = PathManager.getBinPath();

    if (!binPath.contains(".app")) return false;

    int appIndex = binPath.indexOf(".app");
    String appPath = binPath.substring(0, appIndex + 4);

    try {
      Runtime.getRuntime().exec(new String[]{new File(PathManager.getBinPath(), "relaunch").getPath(), appPath});
    }
    catch (IOException e) {
      throw new CannotRestartException(e);
    }

    return true;
  }

  public static class CannotRestartException extends Exception {
    public CannotRestartException(Throwable cause) {
      super(cause);
    }
  }
}
