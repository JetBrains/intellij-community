// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.impl;

import com.intellij.execution.process.OSProcessUtil;
import com.intellij.execution.process.ProcessInfo;
import com.intellij.openapi.util.SystemInfo;
import junit.framework.TestCase;

import java.util.Arrays;
import java.util.List;

import static com.intellij.testFramework.UsefulTestCase.*;

public class ProcessListUtilTest extends TestCase {
  public void testWorksOnAllPlatforms() {
    assertNotEmpty(Arrays.asList(OSProcessUtil.getProcessList()));

    if (SystemInfo.isWindows) {
      assertNotEmpty(Arrays.asList(ProcessListUtil.getProcessListUsingWindowsTaskList()));
      assertNotEmpty(Arrays.asList(ProcessListUtil.getProcessListUsingWindowsWMIC()));
    }
  }

  public void testMac_Basic() {
    List<ProcessInfo> infos = ProcessListUtil.parseMacOutput(
      "  PID  PPID STAT USER    COMM\n\n" +
      "    1     0 S    user    /dir/file\n" +
      "    2     1 S    user    ./dir/dir/file\n" +
      "    3     2 S    user    ./dir/dir/file\n" +
      "10000     3 S    user    ./dir/dir/file",
      "  PID  PPID STAT USER    COMMAND\n\n" +
      "    1     0 S    user    /dir/file\n" +
      "    2     1 S    user    ./dir/dir/file\n" +
      "    3     2 S    user    ./dir/dir/file param param\n" +
      "10000     3 S    user    ./dir/dir/file param2 param2"
    );
    assertOrderedEquals(infos,
                        new ProcessInfo(1, "/dir/file", "file", "", "/dir/file", 0),
                        new ProcessInfo(2, "./dir/dir/file", "file", "", "./dir/dir/file", 1),
                        new ProcessInfo(3, "./dir/dir/file param param", "file", "param param", "./dir/dir/file", 2),
                        new ProcessInfo(10000, "./dir/dir/file param2 param2", "file", "param2 param2", "./dir/dir/file", 3));
  }

  public void testMac_DoNotIncludeProcessedMissingOnTheSecondPSRun() {
    List<ProcessInfo> infos = ProcessListUtil.parseMacOutput(
      "  PID  PPID STAT USER    COMM\n\n" +
      "    1     0 S    user    /dir/file\n" +
      "    2     1 S    user    /dir/file\n",
      "  PID  PPID STAT USER    COMMAND\n\n" +
      "    1     0 S    user    /dir/file\n" +
      "    5     1 S    user    /dir/file\n"
    );
    assertOrderedEquals(infos, new ProcessInfo(1, "/dir/file", "file", "", "/dir/file", 0));
  }

  public void testMac_DoNotIncludeProcessedChangedOnTheSecondPSRun() {
    List<ProcessInfo> infos = ProcessListUtil.parseMacOutput(
      "  PID  PPID STAT USER    COMM\n\n" +
      "    1     0 S    user    /dir/file\n" +
      "    2     1 S    user    /dir/file\n" +
      "    3     2 S    user    /dir/file\n" +
      "    4     3 S    user    /dir/file\n",
      "  PID  PPID STAT USER    COMMAND\n\n" +
      "    1     0 S    user    /dir/file param\n" +
      "    2     1 S    user    /dir/ffff\n" +
      "    3     2 S    user    /dir/file1\n" +
      "    4     3 S    user    /dir/file/1\n"
    );
    assertOrderedEquals(infos, new ProcessInfo(1, "/dir/file param", "file", "param", "/dir/file", 0));
  }

  public void testMac_DoNotIncludeZombies() {
    List<ProcessInfo> infos = ProcessListUtil.parseMacOutput(
      "  PID  PPID STAT USER    COMM\n\n" +
      "    1     0 S    user    /dir/file\n" +
      "    2     1 Z    user    /dir/file\n" +
      "    3     1 SZ   user    /dir/file\n",
      "  PID  PPID STAT USER    COMM\n\n" +
      "    1     0 S    user    /dir/file\n" +
      "    2     1 Z    user    /dir/file\n" +
      "    3     1 SZ   user    /dir/file\n"
    );
    assertOrderedEquals(infos, new ProcessInfo(1, "/dir/file", "file", "", "/dir/file", 0));
  }

  public void testMac_VariousFormsPidStatUser() {
    List<ProcessInfo> infos = ProcessListUtil.parseMacOutput(
      "  PID  PPID STAT USER      COMMAND\n\n" +
      "    1     0 S    user      /dir/file\n" +
      "  101     1 Ss   user_name /dir/file\n",
      "  PID  PPID STAT USER      COMM\n\n" +
      "    1     0 S    user      /dir/file\n" +
      "  101     1 Ss   user_name /dir/file\n"
    );
    assertOrderedEquals(infos,
                        new ProcessInfo(1, "/dir/file", "file", "", "/dir/file", 0),
                        new ProcessInfo(101, "/dir/file", "file", "", "/dir/file", 1));
  }

  public void testMac_WrongFormat() {
    assertNull(ProcessListUtil.parseMacOutput(
      "   PID STAT USER    COMM\n\n" +
      "     1 S    user    /dir/file\n",
      ""
    ));
    assertNull(ProcessListUtil.parseMacOutput(
      "",
      "   PID STAT USER    COMMAND\n\n" +
      "     1 S    user    /dir/file\n"
    ));


    assertNull(ProcessListUtil.parseMacOutput(
      "   PID STAT USER    COMM\n\n" +
      "     1 S    user    /dir/file\n",
      "wrong format"
    ));
    assertNull(ProcessListUtil.parseMacOutput(
      "wrong format",
      "   PID STAT USER    COMMAND\n\n" +
      "     1 S    user    /dir/file\n"
    ));

    assertEmpty(ProcessListUtil.parseMacOutput(
      "   PID PPID STAT USER    COMM\n\n" +
      "     1    0 S    user    /dir/file\n",
      "   PID PPID S USER    COMMAND\n\n" +
      "                           \n"
    ));
    assertEmpty(ProcessListUtil.parseMacOutput(
      "   PID PPID S USER    COMMAND\n\n" +
      "                           \n",
      "   PID PPID STAT USER    COMMAND\n\n" +
      "     1    0 S    user    /dir/file\n"
    ));
  }

  public void testWindows_WMIC() {
    List<ProcessInfo> infos = ProcessListUtil.parseWMICOutput(
      "Caption                   CommandLine                                            ExecutablePath                                ParentProcessId                          ProcessId  \n" +
      "smss.exe                                                                                                                       0                                        304        \n" +
      "sihost.exe                sihost.exe                                                                                           304                                      3052       \n" +
      "taskhostw.exe             taskhostw.exe {222A245B-E637-4AE9-A93F-A59CA119A75E}                                                 0                                        3068       \n" +
      "explorer.exe              C:\\WINDOWS\\Explorer.EXE                                C:\\WINDOWS\\Explorer.EXE                       3068                                     3164       \n" +
      "TPAutoConnect.exe         TPAutoConnect.exe -q -i vmware -a COM1 -F 30                                                         3164                                     3336       \n" +
      "conhost.exe               \\??\\C:\\WINDOWS\\system32\\conhost.exe 0x4                \\??\\C:\\WINDOWS\\system32\\conhost.exe           0                                        3348       \n");
    assertOrderedEquals(infos,
                        new ProcessInfo(304, "smss.exe", "smss.exe", "", null, 0),
                        new ProcessInfo(3052, "sihost.exe", "sihost.exe", "", null, 304),
                        new ProcessInfo(3068, "taskhostw.exe {222A245B-E637-4AE9-A93F-A59CA119A75E}", "taskhostw.exe", "{222A245B-E637-4AE9-A93F-A59CA119A75E}", null, 0),
                        new ProcessInfo(3164, "C:\\WINDOWS\\Explorer.EXE", "explorer.exe", "", "C:\\WINDOWS\\Explorer.EXE", 3068),
                        new ProcessInfo(3336, "TPAutoConnect.exe -q -i vmware -a COM1 -F 30", "TPAutoConnect.exe", "-q -i vmware -a COM1 -F 30", null, 3164),
                        new ProcessInfo(3348, "\\??\\C:\\WINDOWS\\system32\\conhost.exe 0x4", "conhost.exe", "0x4", "\\??\\C:\\WINDOWS\\system32\\conhost.exe", 0));
  }

  public void testOnWindows_WMIC_DoNotIncludeSystemIdleProcess() {
    List<ProcessInfo> infos = ProcessListUtil.parseWMICOutput(
      "Caption                   CommandLine                     ExecutablePath                       ParentProcessId                     ProcessId  \n" +
      "System Idle Process                                                                            -1                                  0          \n" +
      "System                                                                                         0                                   4          \n" +
      "smss.exe                                                                                       0                                   304        \n");
    assertOrderedEquals(infos,
                        new ProcessInfo(4, "System", "System", "", null, 0),
                        new ProcessInfo(304, "smss.exe", "smss.exe", "", null, 0));
  }

  public void testWindows_WMIC_WrongFormat() {
    assertNull(ProcessListUtil.parseWMICOutput(
      ""));
    assertNull(ProcessListUtil.parseWMICOutput(
      "wrong format"));
    assertEmpty(ProcessListUtil.parseWMICOutput(
      "Caption                   CommandLine                   ExecutablePath                         ParentProcessId                         ProcessId  \n"));
    assertNull(ProcessListUtil.parseWMICOutput(
      "smss.exe                                                                         304        \n"));

    assertNull(ProcessListUtil.parseWMICOutput(
      "Caption                   XXX                ExecutablePath                                    ProcessId  \n" +
      "smss.exe                                                                                       304        \n"));
    assertNull(ProcessListUtil.parseWMICOutput(
      "Caption                   CommandLine               ExecutablePath                             XXX  \n" +
      "smss.exe                                                                                       304        \n"));
    assertEmpty(ProcessListUtil.parseWMICOutput(
      "Caption                   CommandLine               ExecutablePath                         ParentProcessId                         ProcessId  \n" +
      "                                                                                                          \n"));
  }

  public void testWindows_TaskList() {
    List<ProcessInfo> infos = ProcessListUtil.parseListTasksOutput(
      "\"smss.exe\",\"304\",\"Services\",\"0\",\"224 K\",\"Unknown\",\"N/A\",\"0:00:00\",\"N/A\"\n" +
      "\"sihost.exe\",\"3052\",\"Console\",\"1\",\"10,924 K\",\"Running\",\"VM-WINDOWS\\Anton Makeev\",\"0:00:02\",\"N/A\"\n" +
      "\"taskhostw.exe\",\"3068\",\"Console\",\"1\",\"5,860 K\",\"Running\",\"VM-WINDOWS\\Anton Makeev\",\"0:00:00\",\"Task Host Window\"\n" +
      "\"explorer.exe\",\"3164\",\"Console\",\"1\",\"30,964 K\",\"Running\",\"VM-WINDOWS\\Anton Makeev\",\"0:00:04\",\"N/A\"\n" +
      "\"TPAutoConnect.exe\",\"3336\",\"Console\",\"1\",\"5,508 K\",\"Running\",\"VM-WINDOWS\\Anton Makeev\",\"0:00:04\",\"HiddenTPAutoConnectWindow\"\n" +
      "\"conhost.exe\",\"3348\",\"Console\",\"1\",\"1,172 K\",\"Unknown\",\"VM-WINDOWS\\Anton Makeev\",\"0:00:00\",\"N/A\"\n");
    assertOrderedEquals(infos,
                        new ProcessInfo(304, "smss.exe", "smss.exe", ""),
                        new ProcessInfo(3052, "sihost.exe", "sihost.exe", ""),
                        new ProcessInfo(3068, "taskhostw.exe", "taskhostw.exe", ""),
                        new ProcessInfo(3164, "explorer.exe", "explorer.exe", ""),
                        new ProcessInfo(3336, "TPAutoConnect.exe", "TPAutoConnect.exe", ""),
                        new ProcessInfo(3348, "conhost.exe", "conhost.exe", ""));
  }

  public void testWindows_TaskList_WrongFormat() {
    assertEmpty(ProcessListUtil.parseListTasksOutput(""));
    assertNull(ProcessListUtil.parseListTasksOutput("wrong format"));
    assertNull(ProcessListUtil.parseListTasksOutput("\"\""));
    assertEmpty(ProcessListUtil.parseListTasksOutput("\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\"\n"));
  }

  public void testWinProcessListHelperOutputParsing() {
    List<ProcessInfo> infos = ProcessListUtil.parseWinProcessListHelperOutput(
      "pid:19608\n" +
      "parentPid:0\n" +
      "name:SourceTree.exe\n" +
      "cmd:\"C:\\\\Users\\\\grahams\\\\AppData\\\\Local\\\\SourceTree\\\\app-3.1.3\\\\SourceTree.exe\"\n" +
      "pid:12300\n" +
      "parentPid:0\n" +
      "name:conhost.exe\n" +
      "cmd:\\\\??\\\\C:\\\\Windows\\\\system32\\\\conhost.exe 0x4\n" +
      "pid:26284\n" +
      "parentPid:0\n" +
      "name:Unity Hub.exe\n" +
      "cmd:\"C:\\\\Program Files\\\\Unity Hub\\\\Unity Hub.exe\" --no-sandbox --lang=en-US --node-integration=true /prefetch:1\n" +
      "pid:25064\n" +
      "parentPid:12300\n" +
      "name:cmd.exe\n" +
      "cmd:\"C:\\\\WINDOWS\\\\system32\\\\cmd.exe\" /c \"pause\\necho 123\"\n"
    );
    assertOrderedEquals(
      infos,
      new ProcessInfo(19608, "\"C:\\Users\\grahams\\AppData\\Local\\SourceTree\\app-3.1.3\\SourceTree.exe\"", "SourceTree.exe", "", null, 0),
      new ProcessInfo(12300, "\\??\\C:\\Windows\\system32\\conhost.exe 0x4", "conhost.exe", "0x4", null, 0),
      new ProcessInfo(26284,
                      "\"C:\\Program Files\\Unity Hub\\Unity Hub.exe\" --no-sandbox --lang=en-US --node-integration=true /prefetch:1",
                      "Unity Hub.exe",
                      "--no-sandbox --lang=en-US --node-integration=true /prefetch:1", null, 0),
      new ProcessInfo(25064,
                      "\"C:\\WINDOWS\\system32\\cmd.exe\" /c \"pause\necho 123\"",
                      "cmd.exe",
                      "/c \"pause\necho 123\"", null, 12300)
    );

    assertNull(ProcessListUtil.parseWinProcessListHelperOutput(""));
    assertNull(ProcessListUtil.parseWinProcessListHelperOutput("Hello"));
    assertNull(ProcessListUtil.parseWinProcessListHelperOutput("pid:12345\n" +
                                                               "name:git.exe\n" +
                                                               "cmd:git.exe fetch\n" +
                                                               "pid:1x\n" +
                                                               "name:node.exe\n" +
                                                               "cmd:node.exe qq\n"
    ));
  }
}
