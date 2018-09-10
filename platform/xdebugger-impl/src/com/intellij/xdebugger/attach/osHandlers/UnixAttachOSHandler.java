// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.attach.osHandlers;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.xdebugger.attach.EnvironmentAwareHost;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public abstract class UnixAttachOSHandler extends AttachOSHandler {
  private static final String PTRACE_SCOPE_PATH = "/proc/sys/kernel/yama/ptrace_scope";
  private static final String GET_PROCESS_USER = "ps -a -x -o user,pid | grep %d | awk '{print $1}'";

  private static final Logger LOGGER = Logger.getInstance(UnixAttachOSHandler.class);

  public UnixAttachOSHandler(@NotNull EnvironmentAwareHost host,
                             @NotNull OSType osType) {
    super(host, osType);
  }

  /**
   * @return uid of user or -1 if error occurred
   */
  public int getUid() {
    //TODO should be reworked after merging according to jsch-nio
    try {
      GeneralCommandLine commandLine = new GeneralCommandLine(Arrays.asList("id", "-u"));
      String uid = myHost.getProcessOutput(commandLine).getStdout().trim();

      try {
        return Integer.valueOf(uid);
      }
      catch (NumberFormatException e) {
        LOGGER.warn("Error while parsing user id from " + uid, e);
        return -1;
      }
    }
    catch (ExecutionException ex) {
      LOGGER.warn("Error while getting user id", ex);
      return -1;
    }
  }

  public int getPtraceScope() {
    try {
      final InputStream fileStream = myHost.getFileContent(PTRACE_SCOPE_PATH);

      if (fileStream != null) {
        if (!myHost.canReadFile(PTRACE_SCOPE_PATH)) {
          LOGGER.warn(PTRACE_SCOPE_PATH + " file exists but you don't have permissions to read it.");
          return 3; // The strongest possible level
        }
        final BufferedReader buf = new BufferedReader(new InputStreamReader(fileStream));

        final String fileContent = buf.readLine();
        try (Scanner scanner = new Scanner(fileContent)) {
          return scanner.nextInt();
        }
        catch (Exception ex) {
          LOGGER.warn("Could not read security level from " + PTRACE_SCOPE_PATH, ex);
          return 3; // The strongest possible level
        }
      }
    }
    catch (IOException | ExecutionException e) {
      LOGGER.warn("Error while uploading file:" + PTRACE_SCOPE_PATH, e);
      return 1;
    }

    return 1; // default PTRACE_SCOPE value
  }

  public boolean isOurProcess(int pid) {
    List<String> commands = new ArrayList<>();
    commands.add("/bin/sh");
    commands.add("-c");
    commands.add(String.format(GET_PROCESS_USER, pid));
    try {
      return myHost.getProcessOutput(new GeneralCommandLine(commands)).getStdout().trim().equals(getenv("USER"));
    }
    catch (Exception e) {
      LOGGER.warn("Failed to compare users", e);
      return false;
    }
  }
}