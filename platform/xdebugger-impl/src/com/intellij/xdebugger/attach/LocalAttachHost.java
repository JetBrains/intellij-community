// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.attach;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessUtil;
import com.intellij.execution.process.ProcessInfo;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class LocalAttachHost extends EnvironmentAwareHost {
  public static final LocalAttachHost INSTANCE = new LocalAttachHost();

  @NotNull
  private static final String getProcessUser = "ps -a -x -o user,pid | grep %d | awk '{print $1}'";
  @NotNull
  private static final String PTRACE_SCOPE_PATH = "/proc/sys/kernel/yama/ptrace_scope";
  private static final Logger LOGGER = Logger.getInstance(LocalAttachHost.class.getName());

  @NotNull
  @Override
  public List<ProcessInfo> getProcessList(@NotNull Project project) {
    return Arrays.asList(OSProcessUtil.getProcessList());
  }

  @NotNull
  @Override
  public ProcessOutput execAndGetOutput(@Nullable Project project, @NotNull GeneralCommandLine command)
    throws ExecutionException {
    return ExecUtil.execAndGetOutput(command);
  }

  @Nullable
  @Override
  public InputStream getRemoteFile(@NotNull String remotePath) throws ExecutionException {
    VirtualFile file = LocalFileSystem.getInstance().findFileByPath(remotePath);
    if (file == null) {
      return null;
    }
    try {
      return file.getInputStream();
    }
    catch (IOException e) {
      throw new ExecutionException("Can't get input stream", e);
    }
  }

  @Override
  public boolean isUnix() {
    return SystemInfo.isUnix;
  }

  @Override
  public int getUid(@NotNull Project project) {
    return 0;
  }

  @Override
  public boolean isSudoNeeded(@NotNull ProcessInfo processInfo) {
    int pid = processInfo.getPid();

    if (SystemInfo.isMac) {
      return !isSameUser(pid);
    }

    if (SystemInfo.isUnix) {
      return isSudoNeededUnix(pid);
    }
    else {
      return false; // TODO
    }
  }

  private static boolean isSudoNeededUnix(int pid) {
    /* TODO actually, if security level is 1 then depending on CAP_SYS_PTRACE or some predefined relationship with
     * the target process we might can attach to it without sudo. Thats why we might need pid.
     */
    return (getPtraceScope() > 0 || !isSameUser(pid));
  }

  private static int getPtraceScope() {
    final File ptraceScope = new File(PTRACE_SCOPE_PATH);

    if (ptraceScope.exists()) {
      if (!ptraceScope.canRead()) {
        LOGGER.warn(PTRACE_SCOPE_PATH + " file exists but you don't have permissions to read it.");
        return 3; // The strongest possible level
      }
      try (Scanner scanner = new Scanner(ptraceScope)) {
        return scanner.nextInt();
      }
      catch (Exception ex) {
        LOGGER.warn("Could not read security level from " + PTRACE_SCOPE_PATH, ex);
        return 3; // The strongest possible level
      }
    }

    return 1; // default PTRACE_SCOPE value
  }

  private static boolean isSameUser(int pid) {
    List<String> commands = new ArrayList<>();
    commands.add("/bin/sh");
    commands.add("-c");
    commands.add(String.format(getProcessUser, pid));
    String processUser = ExecUtil.execAndReadLine(new GeneralCommandLine(commands));

    return processUser != null && processUser.equals(System.getenv("USER"));
  }
}
