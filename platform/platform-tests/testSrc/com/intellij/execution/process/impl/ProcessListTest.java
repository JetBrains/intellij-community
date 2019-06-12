// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.impl;

import com.intellij.execution.process.OSProcessUtil;
import com.intellij.execution.process.ProcessInfo;
import com.intellij.openapi.util.SystemInfo;
import junit.framework.TestCase;

import java.util.Arrays;
import java.util.List;

import static com.intellij.testFramework.UsefulTestCase.*;

public class ProcessListTest extends TestCase {
  public void testWorksOnAllPlatforms() {
    assertNotEmpty(Arrays.asList(OSProcessUtil.getProcessList()));

    if (SystemInfo.isWindows) {
      assertNotEmpty(Arrays.asList(ProcessListUtil.getProcessListUsingWindowsTaskList()));
      assertNotEmpty(Arrays.asList(ProcessListUtil.getProcessListUsingWindowsWMIC()));
    }
  }

  public void testMac_Basic() {
    List<ProcessInfo> infos = ProcessListUtil.parseMacOutput(
      "   PID STAT USER    COMM\n\n" +
      "     1 S    user    /dir/file\n" +
      "     2 S    user    ./dir/dir/file\n" +
      "     3 S    user    ./dir/dir/file\n",
      "   PID STAT USER    COMMAND\n\n" +
      "     1 S    user    /dir/file\n" +
      "     2 S    user    ./dir/dir/file\n" +
      "     3 S    user    ./dir/dir/file param param"
    );
    assertOrderedEquals(infos,
                        new ProcessInfo(1, "/dir/file", "file", "", "/dir/file"),
                        new ProcessInfo(2, "./dir/dir/file", "file", "", "./dir/dir/file"),
                        new ProcessInfo(3, "./dir/dir/file param param", "file", "param param", "./dir/dir/file"));
  }

  public void testMac_DoNotIncludeProcessedMissingOnTheSecondPSRun() {
    List<ProcessInfo> infos = ProcessListUtil.parseMacOutput(
      "   PID STAT USER    COMM\n\n" +
      "     1 S    user    /dir/file\n" +
      "     2 S    user    /dir/file\n",
      "   PID STAT USER    COMMAND\n\n" +
      "     1 S    user    /dir/file\n" +
      "     5 S    user    /dir/file\n"
    );
    assertOrderedEquals(infos, new ProcessInfo(1, "/dir/file", "file", "", "/dir/file"));
  }

  public void testMac_DoNotIncludeProcessedChangedOnTheSecondPSRun() {
    List<ProcessInfo> infos = ProcessListUtil.parseMacOutput(
      "   PID STAT USER    COMM\n\n" +
      "     1 S    user    /dir/file\n" +
      "     2 S    user    /dir/file\n" +
      "     3 S    user    /dir/file\n" +
      "     4 S    user    /dir/file\n",
      "   PID STAT USER    COMMAND\n\n" +
      "     1 S    user    /dir/file param\n" +
      "     2 S    user    /dir/ffff\n" +
      "     3 S    user    /dir/file1\n" +
      "     4 S    user    /dir/file/1\n"
    );
    assertOrderedEquals(infos, new ProcessInfo(1, "/dir/file param", "file", "param", "/dir/file"));
  }

  public void testMac_DoNotIncludeZombies() {
    List<ProcessInfo> infos = ProcessListUtil.parseMacOutput(
      "   PID STAT USER    COMM\n\n" +
      "     1 S    user    /dir/file\n" +
      "     2 Z    user    /dir/file\n" +
      "     3 SZ   user    /dir/file\n",
      "   PID STAT USER    COMMAND\n\n" +
      "     1 S    user    /dir/file\n" +
      "     2 Z    user    /dir/file\n" +
      "     3 SZ   user    /dir/file\n"
    );
    assertOrderedEquals(infos, new ProcessInfo(1, "/dir/file", "file", "", "/dir/file"));
  }

  public void testMac_VariousFormsPidStatUser() {
    List<ProcessInfo> infos = ProcessListUtil.parseMacOutput(
      "   PID STAT USER      COMMAND\n\n" +
      "     1 S    user      /dir/file\n" +
      "   101 Ss   user_name /dir/file\n",
      "   PID STAT USER      COMM\n\n" +
      "     1 S    user      /dir/file\n" +
      "   101 Ss   user_name /dir/file\n"
    );
    assertOrderedEquals(infos,
                        new ProcessInfo(1, "/dir/file", "file", "", "/dir/file"),
                        new ProcessInfo(101, "/dir/file", "file", "", "/dir/file"));
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
      "   PID STAT USER    COMM\n\n" +
      "     1 S    user    /dir/file\n",
      "   PID S USER    COMMAND\n\n" +
      "                           \n"
    ));
    assertEmpty(ProcessListUtil.parseMacOutput(
      "   PID S USER    COMMAND\n\n" +
      "                           \n",
      "   PID STAT USER    COMMAND\n\n" +
      "     1 S    user    /dir/file\n"
    ));
  }

  public void testWindows_WMIC() {
    List<ProcessInfo> infos = ProcessListUtil.parseWMICOutput(
      "Caption                   CommandLine                                            ExecutablePath                          ProcessId  \n" +
      "smss.exe                                                                                                                 304        \n" +
      "sihost.exe                sihost.exe                                                                                     3052       \n" +
      "taskhostw.exe             taskhostw.exe {222A245B-E637-4AE9-A93F-A59CA119A75E}                                           3068       \n" +
      "explorer.exe              C:\\WINDOWS\\Explorer.EXE                                C:\\WINDOWS\\Explorer.EXE                                          3164       \n" +
      "TPAutoConnect.exe         TPAutoConnect.exe -q -i vmware -a COM1 -F 30                                                   3336       \n" +
      "conhost.exe               \\??\\C:\\WINDOWS\\system32\\conhost.exe 0x4                \\??\\C:\\WINDOWS\\system32\\conhost.exe     3348       \n");
    assertOrderedEquals(infos,
                        new ProcessInfo(304, "smss.exe", "smss.exe", ""),
                        new ProcessInfo(3052, "sihost.exe", "sihost.exe", ""),
                        new ProcessInfo(3068, "taskhostw.exe {222A245B-E637-4AE9-A93F-A59CA119A75E}", "taskhostw.exe", "{222A245B-E637-4AE9-A93F-A59CA119A75E}"),
                        new ProcessInfo(3164, "C:\\WINDOWS\\Explorer.EXE", "explorer.exe", "", "C:\\WINDOWS\\Explorer.EXE"),
                        new ProcessInfo(3336, "TPAutoConnect.exe -q -i vmware -a COM1 -F 30", "TPAutoConnect.exe", "-q -i vmware -a COM1 -F 30"),
                        new ProcessInfo(3348, "\\??\\C:\\WINDOWS\\system32\\conhost.exe 0x4", "conhost.exe", "0x4", "\\??\\C:\\WINDOWS\\system32\\conhost.exe"));
  }

  public void testOnWindows_WMIC_DoNotIncludeSystemIdleProcess() {
    List<ProcessInfo> infos = ProcessListUtil.parseWMICOutput(
      "Caption                   CommandLine                     ExecutablePath                       ProcessId  \n" +
      "System Idle Process                                                                            0          \n" +
      "System                                                                                         4          \n" +
      "smss.exe                                                                                       304        \n");
    assertOrderedEquals(infos,
                        new ProcessInfo(4, "System", "System", ""),
                        new ProcessInfo(304, "smss.exe", "smss.exe", ""));
  }

  public void testWindows_WMIC_WrongFormat() {
    assertNull(ProcessListUtil.parseWMICOutput(
      ""));
    assertNull(ProcessListUtil.parseWMICOutput(
      "wrong format"));
    assertEmpty(ProcessListUtil.parseWMICOutput(
      "Caption                   CommandLine                   ExecutablePath                         ProcessId  \n"));
    assertNull(ProcessListUtil.parseWMICOutput(
      "smss.exe                                                                         304        \n"));

    assertNull(ProcessListUtil.parseWMICOutput(
      "Caption                   XXX                ExecutablePath                                    ProcessId  \n" +
      "smss.exe                                                                                       304        \n"));
    assertNull(ProcessListUtil.parseWMICOutput(
      "Caption                   CommandLine               ExecutablePath                             XXX  \n" +
      "smss.exe                                                                                       304        \n"));
    assertEmpty(ProcessListUtil.parseWMICOutput(
      "Caption                   CommandLine               ExecutablePath                             ProcessId  \n" +
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
}
