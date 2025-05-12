// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process.impl;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessInfo;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.NullableFunction;
import com.intellij.util.PathUtil;
import com.intellij.util.ThreeState;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

public final class ProcessListUtil {
  private static final Logger LOG = Logger.getInstance(ProcessListUtil.class);
  private static final String WIN_PROCESS_LIST_HELPER_FILENAME = "WinProcessListHelper.exe";
  @ApiStatus.Internal
  public static final List<@NlsSafe String> COMM_LIST_COMMAND = List.of("/bin/ps", "-a", "-x", "-o", "pid,ppid,state,comm");
  @ApiStatus.Internal
  public static final List<@NlsSafe String> COMMAND_LIST_COMMAND = List.of("/bin/ps", "-a", "-x", "-o", "pid,ppid,state,command");
  @ApiStatus.Internal
  public static final List<@NlsSafe String> PROCESS_OWNER_COMMAND = List.of("/bin/ps", "-a", "-x", "-o", "pid,user");

  private static final String PARENT_PID_PREFIX = "PPid:";

  private ProcessListUtil() {
    // Nothing.
  }

  public static ProcessInfo @NotNull [] getProcessList() {
    List<ProcessInfo> result = doGetProcessList();
    return result.toArray(ProcessInfo.EMPTY_ARRAY);
  }

  private static @NotNull List<ProcessInfo> doGetProcessList() {
    List<ProcessInfo> result;
    if (SystemInfo.isWindows) {
      result = getProcessListUsingWinProcessListHelper();
      if (result != null) return result;
      LOG.info("Cannot get process list via " + WIN_PROCESS_LIST_HELPER_FILENAME + ", fallback to wmic");

      result = getProcessListUsingWindowsWMIC();
      if (result != null) return result;

      LOG.info("Cannot get process list via wmic, fallback to tasklist");
      result = getProcessListUsingWindowsTaskList();
      if (result != null) return result;

      LOG.error("Cannot get process list via wmic and tasklist");
    }
    else if (SystemInfo.isUnix) {
      if (SystemInfo.isMac) {
        result = getProcessListOnMac();
      }
      else {
        result = getProcessListOnUnix();
      }
      if (result != null) return result;

      LOG.error("Cannot get process list");
    }
    else {
      LOG.error("Cannot get process list, unexpected platform: " + SystemInfo.OS_NAME);
    }
    return Collections.emptyList();
  }

  private static @Nullable List<ProcessInfo> parseCommandOutput(@NotNull List<@NlsSafe String> command,
                                                                @NotNull NullableFunction<? super String, ? extends List<ProcessInfo>> parser) {
    return parseCommandOutput(command, parser, null);
  }

  private static @Nullable List<ProcessInfo> parseCommandOutput(@NotNull List<@NlsSafe String> command,
                                                                @NotNull NullableFunction<? super String, ? extends List<ProcessInfo>> parser,
                                                                @Nullable Charset charset) {
    String output = getCommandStdout(command, charset);
    if (output == null) {
      return null;
    }
    return parser.fun(output);
  }

  private static @Nullable String getCommandStdout(@NotNull List<@NlsSafe String> command, @Nullable Charset charset) {
    try {
      GeneralCommandLine commandLine = new GeneralCommandLine(command);
      if (charset != null)
        commandLine.withCharset(charset);
      ProcessOutput processOutput = ExecUtil.execAndGetOutput(commandLine);
      int exitCode = processOutput.getExitCode();
      if (exitCode != 0) {
        LOG.error("Cannot get process list, command '" + StringUtil.join(command, " ") +"' exited with code " + exitCode + ", stdout:\n"
                  + processOutput.getStdout()
                  + "\nstderr:\n"
                  + processOutput.getStderr());
      }
      return processOutput.getStdout();
    }
    catch (ExecutionException e) {
      LOG.error("Cannot get process list", e);
    }
    return null;
  }

  private static @Nullable List<ProcessInfo> getProcessListOnUnix() {
    File proc = new File("/proc");

    File[] processes = proc.listFiles();
    if (processes == null) {
      LOG.error("Cannot read /proc, not mounted?");
      return null;
    }

    String currentUser = getCurrentUser();
    Map<Long, String> owners = getProcessOwners();
    List<ProcessInfo> result = new ArrayList<>();

    for (File each : processes) {
      int pid = StringUtil.parseInt(each.getName(), -1);
      if (pid == -1) continue;

      List<String> cmdline;
      try (FileInputStream stream = new FileInputStream(new File(each, "cmdline"))) {
        String cmdlineString = new String(FileUtil.loadBytes(stream), StandardCharsets.UTF_8);
        cmdline = StringUtil.split(cmdlineString, "\0");
      }
      catch (IOException e) {
        continue;
      }
      if (cmdline.isEmpty()) continue;

      String executablePath = null;

      try {
        File exe = new File(each, "exe");
        if (!exe.getAbsolutePath().equals(exe.getCanonicalPath())) {
          executablePath = exe.getCanonicalPath();
        }
      }
      catch (IOException e) {
        // couldn't resolve symlink
      }

      int parentPid = -1;
      try (FileInputStream stream = new FileInputStream(new File(each, "status"))) {
        String statusString = new String(FileUtil.loadBytes(stream), StandardCharsets.UTF_8);
        for (String line : StringUtil.splitByLines(statusString)) {
          if (line.startsWith(PARENT_PID_PREFIX)) {
            try {
              parentPid = Integer.parseInt(line.substring(PARENT_PID_PREFIX.length()).trim());
            }
            catch (NumberFormatException numberFormatException) {
              LOG.error("Failed to parse parent pid from line " + line);
            }
            break;
          }
        }
      }
      catch (IOException ignored) {
      }

      String processUser = owners.get((long)pid);
      result.add(new ProcessInfo(pid, StringUtil.join(cmdline, " "),
                                 PathUtil.getFileName(cmdline.get(0)),
                                 StringUtil.join(cmdline.subList(1, cmdline.size()), " "),
                                 executablePath,
                                 parentPid,
                                 processUser,
                                 currentUser != null ? ThreeState.fromBoolean(currentUser.equals(processUser)) : ThreeState.UNSURE
      ));
    }
    return result;
  }

  private static @Nullable List<ProcessInfo> getProcessListOnMac() {
    // In order to correctly determine executable file name and retrieve arguments from the command line
    // we need first to get the executable from 'comm' parameter, and then subtract it from the 'command' parameter.
    // Example:
    // 12  S ./command
    // 12  S ./command argument list

    String commandOnly = getCommandStdout(COMM_LIST_COMMAND, null);
    if (commandOnly == null) {
      return null;
    }
    String full = getCommandStdout(COMMAND_LIST_COMMAND, null);
    if (full == null) {
      return null;
    }
    String processOwners = getCommandStdout(PROCESS_OWNER_COMMAND, null);
    if (processOwners == null) {
      return null;
    }
    return parseMacOutput(commandOnly, full, processOwners, getCurrentUser());
  }

  @ApiStatus.Internal
  public static @Nullable List<ProcessInfo> parseMacOutput(@NotNull String commandOnly,
                                                           @NotNull String full,
                                                           @NotNull String processOwners) {
    return parseMacOutput(commandOnly, full, processOwners, null);
  }

  @ApiStatus.Internal
  public static @Nullable List<ProcessInfo> parseMacOutput(@NotNull String commandOnly,
                                                           @NotNull String full,
                                                           @NotNull String processOwners,
                                                           @Nullable String currentUser) {
    List<MacProcessInfo> commands = doParseMacOutput(commandOnly);
    List<MacProcessInfo> fulls = doParseMacOutput(full);
    Int2ObjectMap<String> owners = doParseProcessOwners(processOwners);
    if (commands == null || fulls == null || owners == null) return null;

    Int2ObjectMap<String> idToCommand = new Int2ObjectOpenHashMap<>();
    for (MacProcessInfo each : commands) {
      idToCommand.put(each.pid, each.commandLine);
    }

    List<ProcessInfo> result = new ArrayList<>();
    for (MacProcessInfo each : fulls) {
      if (!idToCommand.containsKey(each.pid)) continue;
      String user = owners.get(each.pid);
      if (user == null) continue;

      String command = idToCommand.get(each.pid);
      if (!(each.commandLine.equals(command) || each.commandLine.startsWith(command + " "))) continue;

      String name = PathUtil.getFileName(command);
      String args = each.commandLine.substring(command.length()).trim();

      ThreeState isOwnedByCurrentUser = currentUser != null ? ThreeState.fromBoolean(currentUser.equals(user)) : ThreeState.UNSURE;
      result.add(new ProcessInfo(each.pid, each.commandLine, name, args, command, each.parentPid, user, isOwnedByCurrentUser));
    }
    return result;
  }

  @ApiStatus.Internal
  public static @Nullable List<ProcessInfo> parseLinuxOutputMacStyle(@NotNull String commandOnly,
                                                                     @NotNull String full,
                                                                     @NotNull String processOwners,
                                                                     @Nullable String currentUser) {
    List<MacProcessInfo> commands = doParseMacOutput(commandOnly);
    if (commands == null) {
      LOG.debug("Failed to parse commands output: ", commandOnly);
      return null;
    }
    List<MacProcessInfo> fulls = doParseMacOutput(full);
    if (fulls == null) {
      LOG.debug("Failed to parse comm output: ", full);
      return null;
    }
    Int2ObjectMap<String> owners = doParseProcessOwners(processOwners);
    if (owners == null) {
      LOG.debug("Failed to parse process owners output: ", processOwners);
      return null;
    }

    Int2ObjectMap<String> idToCommand = new Int2ObjectOpenHashMap<>();
    for (MacProcessInfo each : commands) {
      idToCommand.put(each.pid, each.commandLine);
    }

    List<ProcessInfo> result = new ArrayList<>();
    for (MacProcessInfo each : fulls) {
      if (!idToCommand.containsKey(each.pid)) continue;
      String user = owners.get(each.pid);
      if (user == null) continue;

      String command = idToCommand.get(each.pid);
      String name = PathUtil.getFileName(command);
      String args = each.commandLine.startsWith(command) ? each.commandLine.substring(command.length()).trim()
                                                         : each.commandLine;
      ThreeState isOwnedByCurrentUser = currentUser != null ? ThreeState.fromBoolean(currentUser.equals(user)) : ThreeState.UNSURE;
      result.add(new ProcessInfo(each.pid, each.commandLine, name, args, command, each.parentPid, user, isOwnedByCurrentUser));
    }
    return result;
  }

  private static @Nullable List<MacProcessInfo> doParseMacOutput(@NlsSafe String output) {
    List<MacProcessInfo> result = new ArrayList<>();
    String[] lines = StringUtil.splitByLinesDontTrim(output);
    if (lines.length == 0) return null;

    @NlsSafe String header = lines[0];
    String pidString = "PID";
    int pidStart = header.indexOf(pidString);
    if (pidStart == -1) return null;

    String parentPidString = "PPID";
    int parentPidStart = header.indexOf(parentPidString, pidStart);
    if (parentPidStart == -1) return null;

    int parentPidSectionStart = pidStart + pidString.length();

    int statStart = header.indexOf("S", parentPidStart);
    if (statStart == -1) return null;

    int commandStart = header.indexOf("COMM", statStart);
    if (commandStart == -1) return null;

    for (int i = 1; i < lines.length; i++) {
      String line = lines[i];

      try {
        int pid = StringUtil.parseInt(line.substring(0, parentPidSectionStart).trim(), -1);
        if (pid == -1) continue;

        int parentPid = StringUtil.parseInt(line.substring(parentPidSectionStart, statStart).trim(), -1);

        @NlsSafe String state = line.substring(statStart, commandStart).trim();
        if (state.contains("Z")) continue; // zombie

        String commandLine = line.substring(commandStart).trim();

        result.add(new MacProcessInfo(pid, commandLine, state, parentPid));
      }
      catch (Exception e) {
        LOG.error("Can't parse line '" + line + "'", e);
      }
    }
    return result;
  }

  private record MacProcessInfo(int pid, String commandLine, String state, int parentPid) {
  }

  private static @Nullable List<ProcessInfo> getProcessListUsingWinProcessListHelper() {
    Path exeFile = findWinProcessListHelperFile();
    if (exeFile == null) {
      return null;
    }
    return parseCommandOutput(Collections.singletonList(exeFile.toAbsolutePath().toString()),
                              output -> parseWinProcessListHelperOutput(output, getProcessOwners(), getCurrentUser()),
                              StandardCharsets.UTF_8);
  }

  private static void logErrorTestSafe(@NonNls String message) {
    Application application = ApplicationManager.getApplication();
    if (application == null || application.isUnitTestMode()) {
      LOG.warn(message);
    } else {
      LOG.error(message);
    }
  }

  private static @Nullable String unescapeString(@Nullable String str) {
    if (str == null) return null;
    StringBuilder builder = new StringBuilder();
    for (int index = 0; index < str.length(); index++) {
      if (str.charAt(index) == '\\') {
        if (index == str.length() - 1) {
          logErrorTestSafe("Invalid escaped string: backslash at the last position");
          LOG.debug(str);
          return null;
        }
        switch (str.charAt(index + 1)) {
          case '\\' -> builder.append('\\');
          case 'n' -> builder.append('\n');
          case 'r' -> builder.append('\r');
          default -> {
            logErrorTestSafe("Invalid character after an escape symbol: " + str.charAt(index + 1));
            LOG.debug(str);
            return null;
          }
        }
        index++;
        continue;
      }
      builder.append(str.charAt(index));
    }
    return builder.toString();
  }

  private static @Nullable String removePrefix(String str, @NonNls String prefix) {
    if (str.startsWith(prefix)) {
      return str.substring(prefix.length());
    }
    logErrorTestSafe("Can't remove prefix \"" + prefix + "\"");
    LOG.debug(str);
    return null;
  }

  @VisibleForTesting
  @ApiStatus.Internal
  public static @Nullable List<ProcessInfo> parseWinProcessListHelperOutput(@NotNull String output,
                                                                     @NotNull Map<Long, String> processOwners,
                                                                     @Nullable String currentUser) {
    String[] lines = StringUtil.splitByLines(output, false);
    List<ProcessInfo> result = new ArrayList<>();
    if (lines.length % 4 != 0) {
      logErrorTestSafe("Broken output of " + WIN_PROCESS_LIST_HELPER_FILENAME + ": output line count is not a multiple of 4");
      LOG.debug(output);
      return null;
    }
    int processCount = lines.length / 4;
    for (int i = 0; i < processCount; i++) {
      int offset = i * 4;
      String idString = removePrefix(lines[offset], "pid:");
      int id = StringUtil.parseInt(idString, -1);
      if (id == -1) {
        logErrorTestSafe("Broken output of " + WIN_PROCESS_LIST_HELPER_FILENAME + ": process ID is not a number: " + lines[offset]);
        LOG.debug(output);
        return null;
      }
      if (id == 0) continue;

      String parentIdString = removePrefix(lines[offset + 1], "parentPid:");
      int parentId = StringUtil.parseInt(parentIdString, -1);
      if (parentId == -1) {
        logErrorTestSafe("Broken output of " + WIN_PROCESS_LIST_HELPER_FILENAME + ": parent process ID is not a number: " + lines[offset + 1]);
        LOG.debug(output);
        return null;
      }

      String name = unescapeString(removePrefix(lines[offset + 2], "name:"));
      if (name == null) {
        logErrorTestSafe("Failed to read a process name: " + lines[offset + 2]);
        LOG.debug(output);
        return null;
      }
      if (name.isEmpty()) continue;

      String commandLine = unescapeString(removePrefix(lines[offset + 3], "cmd:"));
      if (commandLine == null) {
        logErrorTestSafe("Failed to read a process command line: " + lines[offset + 3]);
        LOG.debug(output);
        return null;
      }
      String args;
      if (commandLine.isEmpty()) {
        commandLine = name;
        args = "";
      }
      else {
        args = extractCommandLineArgs(commandLine, name);
      }
      String processUser = processOwners.get((long)id);
      ThreeState isOwnedByCurrentUser = currentUser != null ? ThreeState.fromBoolean(currentUser.equals(processUser)) : ThreeState.UNSURE;
      result.add(new ProcessInfo(id, commandLine, name, args, null, parentId, processUser, isOwnedByCurrentUser));
    }
    return result;
  }

  private static @NotNull String extractCommandLineArgs(@NotNull String fullCommandLine, @NotNull String executableName) {
    List<String> commandLineList = StringUtil.splitHonorQuotes(fullCommandLine, ' ');
    if (commandLineList.isEmpty()) return "";

    String first = StringUtil.unquoteString(commandLineList.get(0));
    if (StringUtil.endsWithIgnoreCase(first, executableName)) {
      List<String> argsList = commandLineList.subList(1, commandLineList.size());
      return StringUtil.join(argsList, " ");
    }
    return "";
  }

  private static @Nullable Path findWinProcessListHelperFile() {
    try {
      return PathManager.findBinFileWithException(WIN_PROCESS_LIST_HELPER_FILENAME);
    }
    catch (RuntimeException e) {
      LOG.error(e);
      return null;
    }
  }

  @VisibleForTesting
  @ApiStatus.Internal
  public static @Nullable List<ProcessInfo> getProcessListUsingWindowsWMIC() {
    return parseCommandOutput(Arrays.asList("wmic.exe", "path", "win32_process", "get", "Caption,Processid,ParentProcessId,Commandline,ExecutablePath"),
                              output -> parseWMICOutput(output, getProcessOwners(), getCurrentUser()));
  }

  @VisibleForTesting
  @ApiStatus.Internal
  public static @Nullable List<ProcessInfo> parseWMICOutput(@NotNull String output,
                                                     @NotNull Map<Long, String> processOwners,
                                                     @Nullable String currentUser) {
    List<ProcessInfo> result = new ArrayList<>();
    String[] lines = StringUtil.splitByLinesDontTrim(output);
    if (lines.length == 0) return null;

    String header = lines[0];
    int commandLineStart = header.indexOf("CommandLine");
    if (commandLineStart == -1) return null;

    int pidStart = header.indexOf("ProcessId");
    if (pidStart == -1) return null;

    int parentPidStart = header.indexOf("ParentProcessId");
    if (parentPidStart == -1) return null;

    int executablePathStart = header.indexOf("ExecutablePath");
    if (executablePathStart == -1) return null;


    for (int i = 1; i < lines.length; i++) {
      String line = lines[i];

      int pid = StringUtil.parseInt(line.substring(pidStart).trim(), -1);
      if (pid == -1 || pid == 0) continue;

      int parentPid = StringUtil.parseInt(line.substring(parentPidStart, pidStart).trim(), -1);

      String executablePath = line.substring(executablePathStart, parentPidStart).trim();

      String name = line.substring(0, commandLineStart).trim();
      if (name.isEmpty()) continue;

      String commandLine = line.substring(commandLineStart, executablePathStart).trim();
      String args = "";

      if (commandLine.isEmpty()) {
        commandLine = name;
      }
      else {
        args = extractCommandLineArgs(commandLine, name);
      }

      String processUser = processOwners.get((long)pid);
      ThreeState isOwnedByCurrentUser = currentUser != null ? ThreeState.fromBoolean(currentUser.equals(processUser)) : ThreeState.UNSURE;
      result.add(new ProcessInfo(pid, commandLine, name, args, executablePath, parentPid, processUser, isOwnedByCurrentUser));
    }
    return result;
  }

  @VisibleForTesting
  @ApiStatus.Internal
  public static @Nullable List<ProcessInfo> getProcessListUsingWindowsTaskList() {
    return parseCommandOutput(Arrays.asList("tasklist.exe", "/fo", "csv", "/nh", "/v"),
                              output -> parseListTasksOutput(output, getCurrentUser()));
  }

  @VisibleForTesting
  @ApiStatus.Internal
  public static @Nullable List<ProcessInfo> parseListTasksOutput(@NotNull String output, @Nullable String currentUser) {
    List<ProcessInfo> result = new ArrayList<>();

    CSVReader reader = new CSVReader(new StringReader(output));
    try {
      String[] next;
      while ((next = reader.readNext()) != null) {
        if (next.length < 2) return null;

        int pid = StringUtil.parseInt(next[1], -1);
        if (pid == -1) continue;

        String name = next[0];
        if (name.isEmpty()) continue;

        String userName = next.length > 6 ? next[6] : null;
        if ("N/A".equals(userName)) {
          userName = null;
        }
        ThreeState isOwnedByCurrentUser = currentUser != null ? ThreeState.fromBoolean(currentUser.equals(userName)) : ThreeState.UNSURE;
        result.add(new ProcessInfo(pid, name, name, "", null, -1, userName, isOwnedByCurrentUser));
      }
    }
    catch (IOException e) {
      LOG.error("Cannot parse listtasks output", e);
      return null;
    }
    finally {
      try {
        reader.close();
      }
      catch (IOException ignore) {
      }
    }

    return result;
  }

  /**
   * Parses {@link ProcessListUtil#PROCESS_OWNER_COMMAND} output and returns mapping pid -> its user.
   */
  private static @Nullable Int2ObjectMap<String> doParseProcessOwners(@NlsSafe String output) {
    String[] lines = StringUtil.splitByLinesDontTrim(output);
    if (lines.length == 0) return null;

    Int2ObjectMap<String> result = new Int2ObjectOpenHashMap<>();
    String header = lines[0];
    String pidString = "PID";
    int pidStart = header.indexOf(pidString);
    if (pidStart == -1) return null;

    int userStart = header.indexOf("USER", pidStart + pidString.length());
    if (userStart == -1) return null;

    for (int i = 1; i < lines.length; i++) {
      String line = lines[i];

      try {
        int pid = StringUtil.parseInt(line.substring(0, userStart).trim(), -1);
        if (pid == -1) continue;

        String user = line.substring(userStart).trim();
        result.put(pid, user);
      }
      catch (Exception e) {
        LOG.error("Can't parse line '" + line + "'", e);
      }
    }
    return result;
  }

  /**
   * @return map from process id to process owner name
   */
  private static @NotNull Map<Long, String> getProcessOwners() {
    Map<Long, String> result = new HashMap<>();
    ProcessHandle.allProcesses().forEach(it -> {
      long pid = it.pid();
      ProcessHandle.Info info = it.info();
      String user = info.user().orElse(null);
      result.put(pid, user);
    });
    return result;
  }

  private static @Nullable String getCurrentUser() {
    return ProcessHandle.current().info().user().orElse(null);
  }
}