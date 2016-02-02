/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.execution.process.impl;

import com.intellij.execution.process.OSProcessUtil;
import com.intellij.execution.process.ProcessInfo;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.testFramework.UsefulTestCase;

import java.util.Arrays;
import java.util.List;

public class ProcessListTest extends UsefulTestCase {
  public void testWorksOnAllPlatforms() throws Exception {
    assertNotEmpty(Arrays.asList(OSProcessUtil.getProcessList()));

    if (SystemInfo.isWindows) {
      assertNotEmpty(Arrays.asList(ProcessListUtil.getProcessList_WindowsTaskList()));
      assertNotEmpty(Arrays.asList(ProcessListUtil.getProcessList_WindowsWMIC()));
    }
  }

  public void testMac_Basic() throws Exception {
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
                        new ProcessInfo(1, "/dir/file", "file", ""),
                        new ProcessInfo(2, "./dir/dir/file", "file", ""),
                        new ProcessInfo(3, "./dir/dir/file param param", "file", "param param"));
  }

  public void testMac_DoNotIncludeProcessedMissingOnTheSecondPSRun() throws Exception {
    List<ProcessInfo> infos = ProcessListUtil.parseMacOutput(
      "   PID STAT USER    COMM\n\n" +
      "     1 S    user    /dir/file\n" +
      "     2 S    user    /dir/file\n",
      "   PID STAT USER    COMMAND\n\n" +
      "     1 S    user    /dir/file\n" +
      "     5 S    user    /dir/file\n"
    );
    assertOrderedEquals(infos, new ProcessInfo(1, "/dir/file", "file", ""));
  }

  public void testMac_DoNotIncludeProcessedChangedOnTheSecondPSRun() throws Exception {
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
    assertOrderedEquals(infos, new ProcessInfo(1, "/dir/file param", "file", "param"));
  }

  public void testMac_DoNotIncludeZombies() throws Exception {
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
    assertOrderedEquals(infos, new ProcessInfo(1, "/dir/file", "file", ""));
  }

  public void testMac_VariousFormsPidStatUser() throws Exception {
    List<ProcessInfo> infos = ProcessListUtil.parseMacOutput(
      "   PID STAT USER      COMMAND\n\n" +
      "     1 S    user      /dir/file\n" +
      "   101 Ss   user_name /dir/file\n",
      "   PID STAT USER      COMM\n\n" +
      "     1 S    user      /dir/file\n" +
      "   101 Ss   user_name /dir/file\n"
    );
    assertOrderedEquals(infos,
                        new ProcessInfo(1, "/dir/file", "file", ""),
                        new ProcessInfo(101, "/dir/file", "file", ""));
  }

  public void testMac_WrongFormat() throws Exception {
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
  
  public void testWindows_WMIC() throws Exception {
    List<ProcessInfo> infos = ProcessListUtil.parseWMICOutput(
      "Caption                   CommandLine                                            ProcessId  \n" +
      "smss.exe                                                                         304        \n" +
      "sihost.exe                sihost.exe                                             3052       \n" +
      "taskhostw.exe             taskhostw.exe {222A245B-E637-4AE9-A93F-A59CA119A75E}   3068       \n" +
      "explorer.exe              C:\\WINDOWS\\Explorer.EXE                                3164       \n" +
      "TPAutoConnect.exe         TPAutoConnect.exe -q -i vmware -a COM1 -F 30           3336       \n" +
      "conhost.exe               \\??\\C:\\WINDOWS\\system32\\conhost.exe 0x4                3348       \n");
    assertOrderedEquals(infos,
                        new ProcessInfo(304, "smss.exe", "smss.exe", ""),
                        new ProcessInfo(3052, "sihost.exe", "sihost.exe", ""),
                        new ProcessInfo(3068, "taskhostw.exe {222A245B-E637-4AE9-A93F-A59CA119A75E}", "taskhostw.exe", "{222A245B-E637-4AE9-A93F-A59CA119A75E}"),
                        new ProcessInfo(3164, "C:\\WINDOWS\\Explorer.EXE", "explorer.exe", ""),
                        new ProcessInfo(3336, "TPAutoConnect.exe -q -i vmware -a COM1 -F 30", "TPAutoConnect.exe", "-q -i vmware -a COM1 -F 30"),
                        new ProcessInfo(3348, "\\??\\C:\\WINDOWS\\system32\\conhost.exe 0x4", "conhost.exe", "0x4"));
  }

  public void testOnWindows_WMIC_DoNotIncludeSystemIdleProcess() throws Exception {
    List<ProcessInfo> infos = ProcessListUtil.parseWMICOutput(
      "Caption                   CommandLine                                            ProcessId  \n" +
      "System Idle Process                                                              0          \n" +
      "System                                                                           4          \n" +
      "smss.exe                                                                         304        \n");
    assertOrderedEquals(infos,
                        new ProcessInfo(4, "System", "System", ""),
                        new ProcessInfo(304, "smss.exe", "smss.exe", ""));
  }

  public void testWindows_WMIC_WrongFormat() throws Exception {
    assertNull(ProcessListUtil.parseWMICOutput(
      ""));
    assertNull(ProcessListUtil.parseWMICOutput(
      "wrong format"));
    assertEmpty(ProcessListUtil.parseWMICOutput(
      "Caption                   CommandLine                                            ProcessId  \n"));
    assertNull(ProcessListUtil.parseWMICOutput(
      "smss.exe                                                                         304        \n"));

    assertNull(ProcessListUtil.parseWMICOutput(
      "Caption                   XXX                                                    ProcessId  \n" +
      "smss.exe                                                                         304        \n"));
    assertNull(ProcessListUtil.parseWMICOutput(
      "Caption                   CommandLine                                            XXX  \n" +
      "smss.exe                                                                         304        \n"));
    assertEmpty(ProcessListUtil.parseWMICOutput(
      "Caption                   CommandLine                                            ProcessId  \n" +
      "                                                                                            \n"));
  }

  public void testWindows_TaskList() throws Exception {
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

  public void testWindows_TaskList_WrongFormat() throws Exception {
    assertEmpty(ProcessListUtil.parseListTasksOutput(""));
    assertNull(ProcessListUtil.parseListTasksOutput("wrong format"));
    assertNull(ProcessListUtil.parseListTasksOutput("\"\""));
    assertEmpty(ProcessListUtil.parseListTasksOutput("\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\"\n"));
  }
}
