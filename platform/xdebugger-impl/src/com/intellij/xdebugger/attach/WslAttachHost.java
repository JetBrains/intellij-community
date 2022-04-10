package com.intellij.xdebugger.attach;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessInfo;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.wsl.WSLDistribution;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class WslAttachHost implements XAttachHost {
  private final WSLDistribution myWsl;

  public WslAttachHost(@NotNull WSLDistribution wsl) {
    myWsl = wsl;
  }

  @NotNull
  public WSLDistribution getWsl() {
    return myWsl;
  }

  @Override
  public @NotNull List<ProcessInfo> getProcessList() throws ExecutionException {
    ArrayList<ProcessInfo> result = new ArrayList<>();
    ProcessOutput ps = myWsl.executeOnWsl(10000, "ps", "--no-headers", "-e", "-w", "-w", "-o", "pid,comm,command");
    for (String line : ps.getStdoutLines()) {
      List<String> parts = StringUtil.split(StringUtil.trim(line), " ");
      if (parts.size() < 3) {
        continue;
      }
      try {
        int pid = Integer.parseInt(parts.get(0));
        String executableName = parts.get(1);
        List<String> command = parts.subList(2, parts.size());
        String commandLine = StringUtil.join(command, " ");
        String executablePath = ContainerUtil.getFirstItem(command);
        String args = command.size() > 1 ? StringUtil.join(command.subList(1, command.size()), " ") : "";
        result.add(new ProcessInfo(pid, commandLine, executableName, args, executablePath));
      }
      catch (NumberFormatException e) {
        // ignore
      }
    }
    return result;
  }
}
