// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfoRt;
import org.cef.CefApp;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.awt.Component;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@ApiStatus.Internal
public final class TestUtils {
  private static final Pattern ourProcessesPatternWin = Pattern.compile("--type=[^ ]+");
  private static final Logger LOG = Logger.getInstance(TestUtils.class);

  public static Color getColorAt(Component cefComponent, int x, int y) {
    if (cefComponent instanceof JBCefOsrComponent) {
      return ((JBCefOsrComponent)cefComponent).getColorAt(x, y);
    }

    return null;
  }

  public static Double getPixelDensity(Component cefComponent) {
    if (cefComponent instanceof JBCefOsrComponent) {
      return ((JBCefOsrComponent)cefComponent).getPixelDensity();
    }

    return null;
  }

  private static void killProcess(int pid) {
    String executable;
    String[] params;
    if (SystemInfoRt.isWindows) {
      executable = "taskkill";
      params = new String[]{"/pid", String.valueOf(pid), "/f"};
    } else {
      executable = "kill";
      params = new String[]{"-9", String.valueOf(pid)};
    }

    GeneralCommandLine commandLine = new GeneralCommandLine()
      .withExePath(executable)
      .withParameters(params);

    ProcessOutput output = null;
    try {
      output = ExecUtil.execAndGetOutput(commandLine);
    } catch (ExecutionException ex) {
      LOG.error("Can't kill process. Exception: ", ex);
    }
    if (output != null && output.getExitCode() != 0)
      LOG.error("The command 'kill [PID]' returns not zero exit code: " + output.getExitCode());

  }

  static void registerJCEFTestActions() {
    //noinspection UnresolvedPluginConfigReference
    ActionManagerEx.getInstanceEx()
      .registerAction("TestJCEF_KillCefServer_ActionID", new AnAction(JcefBundle.message("action.TestJCEF_KillCefServer_ActionID.text")) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          ApplicationManager.getApplication().executeOnPooledThread(() -> {
            final ArrayList<RunningServerInfo> runningInstances = new ArrayList<>();
            final ArrayList<RunningServerInfo.SubprocessInfo> subprocesses = new ArrayList<>();
            listRunningInstances(runningInstances, subprocesses);

            final long currentPid = ProcessHandle.current().pid();
            for (RunningServerInfo info : runningInstances) {
              if (currentPid == info.ppid) {
                killProcess(info.pid);
                break;
              }
            }
          });
        }
      });

    //noinspection UnresolvedPluginConfigReference
    ActionManagerEx.getInstanceEx()
      .registerAction("TestJCEF_CrashCefServer_ActionID", new AnAction(JcefBundle.message("action.TestJCEF_CrashCefServer_ActionID.text")) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          ApplicationManager.getApplication().executeOnPooledThread(() -> {
            final CefApp cefApp = CefApp.getInstance();
            cefApp.getServer().exec(r -> r.getServerInfo("doCrash"));
          });
        }
      });

    //noinspection UnresolvedPluginConfigReference
    ActionManagerEx.getInstanceEx()
      .registerAction("TestJCEF_KillGPU_ActionID", new AnAction(JcefBundle.message("action.TestJCEF_KillGPU_ActionID.text")) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          ApplicationManager.getApplication().executeOnPooledThread(() -> {
            final ArrayList<RunningServerInfo> runningInstances = new ArrayList<>();
            final ArrayList<RunningServerInfo.SubprocessInfo> subprocesses = new ArrayList<>();
            listRunningInstances(runningInstances, subprocesses);

            final long currentPid = ProcessHandle.current().pid();
            for (RunningServerInfo serverInfo : runningInstances) {
              if (currentPid == serverInfo.ppid || CefApp.getInstance().getServer().getThriftServer().getPort() == serverInfo.port) {
                for (RunningServerInfo.SubprocessInfo subprocessInfo : subprocesses) {
                  if (subprocessInfo.ppid == serverInfo.pid && subprocessInfo.type.equals("gpu-process")) {
                    killProcess(subprocessInfo.pid);
                  }
                }
                break;
              }
            }
          });
        }
      });
  }

  private static List<RunningServerInfo> listRunningInstances(ArrayList<RunningServerInfo> serverProcesses, ArrayList<RunningServerInfo.SubprocessInfo> subprocesses) {
    if (SystemInfoRt.isLinux || SystemInfoRt.isMac) {
      final String cmd = "ps -Af | grep -E 'cef_server .*'";

      try {
        Process process = new ProcessBuilder("bash", "-c", cmd).redirectErrorStream(true).start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
          //   UID PID   PPID ....
          //   501 80516 80489   0 Fri07AM ??         1:52.31 /Users/..../Contents/Frameworks/cef_server.app/Contents/MacOS/cef_server --port=6188 --logfile=/Users/.../jcef_80489.log --loglevel=5 --params=/var/folders/1k/hmmg06wx2bn4dwfq53wy6c_c0000gn/T/cef_server_params.txt
          String line;
          while ((line = reader.readLine()) != null) {
            if (line.contains("grep -E")) {
              continue;
            }

            line = line.trim();
            final int posSp0 = line.indexOf(" ");
            int posSp0End = posSp0 + 1;
            while (posSp0End < line.length() && line.charAt(posSp0End) == ' ') ++posSp0End;
            final int posSp1 = line.indexOf(" ", posSp0End);
            int posSp1End = posSp1 + 1;
            while (posSp1End < line.length() && line.charAt(posSp1End) == ' ') ++posSp1End;
            final int posSp2 = line.indexOf(" ", posSp1End);
            int posSp2End = posSp2 + 1;
            while (posSp2End < line.length() && line.charAt(posSp2End) == ' ') ++posSp2End;

            final int pid = parseIntSafe(line.substring(posSp0End, posSp1), -1);
            final int ppid = parseIntSafe(line.substring(posSp1End, posSp2), -1);

            final String cmdPrefix = SystemInfoRt.isMac ? "MacOS/cef_server" : "bin/cef_server";
            final int prefixPos = line.indexOf(cmdPrefix);
            final String cmdLine = prefixPos >= 0 ? line.substring(prefixPos + cmdPrefix.length()) : "";

            final int pos0 = line.indexOf("--port=");
            final int posType = line.indexOf("--type=");
            if (pos0 >= 0) {
              final int pos1 = line.indexOf(" ", pos0 + 7);
              String sport = line.substring(pos0 + 7, pos1 == -1 ? line.length() : pos1);
              try {
                serverProcesses.add(new RunningServerInfo(Integer.parseInt(sport), pid, ppid, cmdLine));
              } catch (NumberFormatException ignored) {
              }
            } else if (posType >= 0) {
              final int pos1 = line.indexOf(" ", posType + 7);
              String stype = line.substring(posType + 7, pos1);
              subprocesses.add(new RunningServerInfo.SubprocessInfo(pid, ppid, stype));
            }
          }
        }

        process.waitFor();
      } catch (IOException | InterruptedException ignored) {
      }

      return serverProcesses;
    }

    // Windows
    List<WindowsProcessInfo> processes = null;
    try {
      processes = listWindowsProcesses(".*cef_server.exe", s -> !ourProcessesPatternWin.matcher(s).find());
      for (WindowsProcessInfo pi : processes) {
        final int pos0 = pi.commandLine.indexOf("--port=");
        if (pos0 >= 0) {
          final int pos1 = pi.commandLine.indexOf(" ", pos0 + 7);
          String sport = pi.commandLine.substring(pos0 + 7, pos1);
          try {
            serverProcesses.add(new RunningServerInfo(Integer.parseInt(sport), pi.pid, pi.parentPid == null ? -1 : pi.parentPid, pi.commandLine));
          } catch (NumberFormatException ignored) {
          }
        }
      }

      processes = listWindowsProcesses(".*cef_server.exe", s -> ourProcessesPatternWin.matcher(s).find());
      for (WindowsProcessInfo pi : processes) {
        final int pos0 = pi.commandLine.indexOf("--type=");
        if (pos0 >= 0) {
          final int pos1 = pi.commandLine.indexOf(" ", pos0 + 7);
          String stype = pi.commandLine.substring(pos0 + 7, pos1);
          try {
            subprocesses.add(new RunningServerInfo.SubprocessInfo(pi.pid, pi.parentPid == null ? -1 : pi.parentPid, stype));
          } catch (NumberFormatException ignored) {
          }
        }
      }
    } catch (IOException | InterruptedException ignored) {
    }

    return serverProcesses;
  }

  private static class WindowsProcessInfo {
      final int pid;
      final String name;
      final String commandLine;
      final Integer parentPid;
      final String parentName;
      final String parentCommandLine;

      WindowsProcessInfo(int pid, String name, String commandLine,
                                Integer parentPid, String parentName, String parentCommandLine) {
          this.pid = pid;
          this.name = name;
          this.commandLine = commandLine;
          this.parentPid = parentPid;
          this.parentName = parentName;
          this.parentCommandLine = parentCommandLine;
      }

      @Override
      public String toString() {
          return String.format(
                  "[%d] %s — %s | parent[%s]: %s — %s",
                  pid, name, commandLine,
                  parentPid != null ? parentPid.toString() : "–",
                  parentName != null ? parentName : "–",
                  parentCommandLine != null ? parentCommandLine : "–"
          );
      }
  }

  private static List<WindowsProcessInfo> listWindowsProcesses(String regexNameFilter, Predicate<String> cmdFilter)
    throws IOException, InterruptedException {
    List<WindowsProcessInfo> result = new ArrayList<>();
    Pattern patternName = regexNameFilter == null || regexNameFilter.isEmpty() ? null : Pattern.compile(regexNameFilter);

    // Optimized PowerShell: fetch all once, join in memory, output CSV
    String psScript =
      "$procs = @{}; " +
      "Get-CimInstance Win32_Process | ForEach-Object { $procs[$_.ProcessId] = $_ }; " +
      "$output = foreach ($p in $procs.Values) { " +
      "  $parent = $null; " +
      "  if ($p.ParentProcessId -and $procs.ContainsKey($p.ParentProcessId)) { " +
      "    $parent = $procs[$p.ParentProcessId] " +
      "  } " +
      "  [PSCustomObject]@{ " +
      "    PID=$p.ProcessId; " +
      "    Name=$p.Name; " +
      "    CmdLine=$p.CommandLine; " +
      "    ParentPID=($p.ParentProcessId -as [string]); " +
      "    ParentName=($parent.Name -as [string]); " +
      "    ParentCmdLine=($parent.CommandLine -as [string]) " +
      "  } " +
      "}; " +
      "$output | ConvertTo-Csv -NoTypeInformation";

    ProcessBuilder pb = new ProcessBuilder(
      "powershell", "-NoProfile", "-Command", psScript
    );
    pb.redirectErrorStream(true); // merge stderr into stdout for easier handling

    Process process = pb.start();
    // NOTE: don't call process.waitFor() before reading stdout of the process (otherwise deadlock occurred).
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
      String line;
      boolean firstLine = true; // Skip CSV header
      while ((line = reader.readLine()) != null) {
        if (firstLine) {
          firstLine = false;
          continue;
        }

        // CSV: "1234","java.exe","\"C:\\...\\java.exe\" -Xmx512m ..."
        line = line.trim();
        if (line.isEmpty() || line.equals("\"\"")) continue;

        // Expect: PID, Name, CmdLine, ParentPID, ParentName, ParentCmdLine
        List<String> fields = parseCsvLine(line);
        if (fields.size() < 6) continue;

        final String name = unquote(fields.get(1));
        if (patternName != null && !patternName.matcher(name).find()) {
          continue;
        }

        final String cmdLine = unquote(fields.get(2));
        if (cmdFilter != null && !cmdFilter.test(cmdLine)) {
          continue;
        }

        final int pid = parseIntSafe(fields.get(0), -1);
        Integer parentPid = parseIntSafe(fields.get(3), null);

        result.add(new WindowsProcessInfo(pid, name, cmdLine, parentPid,
                                          unquote(fields.get(4)),
                                          unquote(fields.get(5))));
      }
    } catch (IOException ignored) {
    }

    process.waitFor();
    return result;
  }

  private static String unquote(String s) {
    if (s == null) return "";
    s = s.replaceAll("^\"|\"$", "").replace("\"\"", "\"");
    return s;
  }

  private static Integer parseIntSafe(String s, Integer def) {
    s = unquote(s).trim();
    if (s.isEmpty() || s.equalsIgnoreCase("null")) return def;
    try {
      return Integer.valueOf(s);
    }
    catch (NumberFormatException e) {
      return def;
    }
  }

  // Simple CSV parser (handles quoted fields, escaped quotes)
  private static List<String> parseCsvLine(String line) {
    List<String> result = new ArrayList<>();
    boolean inQuotes = false;
    StringBuilder field = new StringBuilder();

    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (c == '"') {
        if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
          field.append('"');
          i++; // skip next quote
        }
        else {
          inQuotes = !inQuotes;
        }
      }
      else if (c == ',' && !inQuotes) {
        result.add(field.toString());
        field.setLength(0); // clear
      }
      else {
        field.append(c);
      }
    }
    result.add(field.toString());
    return result;
  }

  private static class RunningServerInfo {
    final int port;
    final int pid;
    final int ppid; // parent process id
    final String commandLine;

    static class SubprocessInfo {
      final int pid;
      final int ppid;
      final String type;

      SubprocessInfo(int pid, int ppid, String type) {
        this.pid = pid;
        this.ppid = ppid;
        this.type = type;
      }
    }

    RunningServerInfo(int port, int pid, int ppid, String commandLine) {
      this.port = port;
      this.pid = pid;
      this.ppid = ppid;
      this.commandLine = commandLine;
    }
  }
}
