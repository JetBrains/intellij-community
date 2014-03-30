/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressWarnings({"UseOfSystemOutOrSystemErr", "CallToPrintStackTrace"})
public class Restarter {
  private Restarter() {
  }

  private static int getRestartCode() {
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
    return getRestartCode() != 0 || SystemInfo.isWindows || SystemInfo.isMac;
  }

  public static int scheduleRestart(@NotNull String... beforeRestart) throws IOException {
    try {
      int restartCode = getRestartCode();
      if (restartCode != 0) {
        runCommand(beforeRestart);
        return restartCode;
      }
      else if (SystemInfo.isWindows) {
        restartOnWindows(beforeRestart);
        return 0;
      }
      else if (SystemInfo.isMac) {
        restartOnMac(beforeRestart);
        return 0;
      }
    }
    catch (Throwable t) {
      throw new IOException("Cannot restart application: " + t.getMessage(), t);
    }

    runCommand(beforeRestart);
    throw new IOException("Cannot restart application: not supported.");
  }

  private static void runCommand(String... beforeRestart) throws IOException {
    if (beforeRestart.length == 0) return;

    try {
      Process process = Runtime.getRuntime().exec(beforeRestart);

      Thread outThread = new Thread(new StreamRedirector(process.getInputStream(), System.out));
      Thread errThread = new Thread(new StreamRedirector(process.getErrorStream(), System.err));
      outThread.start();
      errThread.start();

      try {
        process.waitFor();
      }
      finally {
        outThread.join();
        errThread.join();
      }
    }
    catch (InterruptedException ignore) { }
  }

  private static void restartOnWindows(@NotNull final String... beforeRestart) throws IOException {
    Kernel32 kernel32 = (Kernel32)Native.loadLibrary("kernel32", Kernel32.class);
    Shell32 shell32 = (Shell32)Native.loadLibrary("shell32", Shell32.class);

    final int pid = kernel32.GetCurrentProcessId();
    final IntByReference argc = new IntByReference();
    Pointer argv_ptr = shell32.CommandLineToArgvW(kernel32.GetCommandLineW(), argc);
    final String[] argv = argv_ptr.getStringArray(0, argc.getValue(), true);
    kernel32.LocalFree(argv_ptr);

    doScheduleRestart(new File(PathManager.getBinPath(), "restarter.exe"), new Consumer<List<String>>() {
      @Override
      public void consume(List<String> commands) {
        Collections.addAll(commands, String.valueOf(pid), String.valueOf(beforeRestart.length));
        Collections.addAll(commands, beforeRestart);
        Collections.addAll(commands, String.valueOf(argc.getValue()));
        Collections.addAll(commands, argv);
      }
    });

    // Since the process ID is passed through the command line, we want to make sure that we don't exit before the "restarter"
    // process has a chance to open the handle to our process, and that it doesn't wait for the termination of an unrelated
    // process which happened to have the same process ID.
    try {
      Thread.sleep(500);
    }
    catch (InterruptedException ignore) {
    }
  }

  private static void restartOnMac(@NotNull final String... beforeRestart) throws IOException {
    final String homePath = PathManager.getHomePath();
    if (!StringUtil.endsWithIgnoreCase(homePath, ".app")) throw new IOException("Application bundle not found: " + homePath);

    doScheduleRestart(new File(PathManager.getBinPath(), "restarter"), new Consumer<List<String>>() {
      @Override
      public void consume(List<String> commands) {
        Collections.addAll(commands, homePath);
        Collections.addAll(commands, beforeRestart);
      }
    });
  }

  private static void doScheduleRestart(File restarterFile, Consumer<List<String>> argumentsBuilder) throws IOException {
    List<String> commands = new ArrayList<String>();
    commands.add(createTempExecutable(restarterFile).getPath());
    argumentsBuilder.consume(commands);
    Runtime.getRuntime().exec(commands.toArray(new String[commands.size()]));
  }

  public static File createTempExecutable(File executable) throws IOException {
    String ext = FileUtilRt.getExtension(executable.getName());
    File copy = FileUtilRt.createTempFile(FileUtilRt.getNameWithoutExtension(executable.getName()),
                                          StringUtil.isEmptyOrSpaces(ext) ? ".tmp" : ("." + ext),
                                          false);
    FileUtilRt.copy(executable, copy);
    if (!copy.setExecutable(executable.canExecute())) throw new IOException("Cannot make file executable: " + copy);
    return copy;
  }

  private interface Kernel32 extends StdCallLibrary {
    int GetCurrentProcessId();

    WString GetCommandLineW();

    Pointer LocalFree(Pointer pointer);
  }

  private interface Shell32 extends StdCallLibrary {
    Pointer CommandLineToArgvW(WString command_line, IntByReference argc);
  }

  private static class StreamRedirector implements Runnable {
    private final InputStream myIn;
    private final OutputStream myOut;

    private StreamRedirector(InputStream in, OutputStream out) {
      myIn = in;
      myOut = out;
    }

    @Override
    public void run() {
      try {
        StreamUtil.copyStreamContent(myIn, myOut);
      }
      catch (IOException ignore) {
      }
    }
  }
}