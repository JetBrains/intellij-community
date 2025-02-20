// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.attach;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessInfo;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.process.impl.ProcessListUtil;
import com.intellij.execution.wsl.WSLCommandLineOptions;
import com.intellij.execution.wsl.WSLDistribution;
import com.intellij.util.system.OS;
import com.intellij.xdebugger.XDebuggerBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

import static com.intellij.execution.process.impl.ProcessListUtil.COMMAND_LIST_COMMAND;
import static com.intellij.execution.process.impl.ProcessListUtil.COMM_LIST_COMMAND;
import static com.intellij.execution.process.impl.ProcessListUtil.PROCESS_OWNER_COMMAND;

public class WslAttachHost implements XAttachHost {
  private final WSLDistribution myWsl;

  public WslAttachHost(@NotNull WSLDistribution wsl) {
    myWsl = wsl;
  }

  public @NotNull WSLDistribution getWsl() {
    return myWsl;
  }

  @Override
  public @NotNull List<ProcessInfo> getProcessList() throws ExecutionException {
    String commListOutput = execAndCheckExitCode(COMM_LIST_COMMAND);
    String commandListOutput = execAndCheckExitCode(COMMAND_LIST_COMMAND);
    var processOwnerOutput = execAndCheckExitCode(PROCESS_OWNER_COMMAND);
    String user = getUser();
    List<ProcessInfo> processInfos = ProcessListUtil.parseLinuxOutputMacStyle(commListOutput, commandListOutput, processOwnerOutput, user);
    if (processInfos == null) {
      throw new ExecutionException(XDebuggerBundle.message("dialog.message.error.parsing.ps.output"));
    }
    return processInfos;
  }

  private @NotNull String execAndCheckExitCode(@NotNull List<String> command) throws ExecutionException {
    WSLCommandLineOptions options = new WSLCommandLineOptions().setSleepTimeoutSec(0.1);
    ProcessOutput output = myWsl.executeOnWsl(command, options, 5_000, null);
    int exitCode = output.getExitCode();
    if (exitCode != 0) {
      String exitCodeString = ProcessTerminatedListener.stringifyExitCode(OS.Linux, exitCode);
      throw new ExecutionException(XDebuggerBundle.message("dialog.message.error.executing.ps", exitCodeString));
    }
    return output.getStdout();
  }


  private @Nullable String getUser() {
    try {
      return execAndCheckExitCode(List.of("whoami")).trim();
    }
    catch (ExecutionException e) {
      // ignore errors: we can list processes without information about the current user
      return null;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    WslAttachHost host = (WslAttachHost)o;
    return Objects.equals(myWsl, host.myWsl);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myWsl);
  }
}
