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

import com.intellij.execution.process.ProcessInfo;
import com.intellij.execution.process.ProcessUtils;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.testFramework.UsefulTestCase;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class ProcessListTest extends UsefulTestCase {
  private File myDir;

  @SuppressWarnings("ResultOfMethodCallIgnored")
  @Override
  public void setUp() throws Exception {
    super.setUp();
    myDir = IoTestUtil.createTestDir("test");
    new File(myDir, "dir/file").mkdirs();
    new File(myDir, "dir/dir/file").mkdirs();
    new File(myDir, "dir/dir/file with spaces").mkdirs();
  }

  @Override
  public void tearDown() throws Exception {
    try {
      FileUtil.delete(myDir);
    }
    finally {
      super.tearDown();
    }
  }

  public void testWorksOnAllPlatforms() throws Exception {
    assertNotEmpty(Arrays.asList(ProcessUtils.getProcessList()));

    if (SystemInfo.isWindows) {
      assertNotEmpty(Arrays.asList(ProcessListUtil.getProcessList_WindowsTaskList()));
      assertNotEmpty(Arrays.asList(ProcessListUtil.getProcessList_WindowsWMIC()));
    }
  }

  public void testUnix_DetermineExecutable() throws Exception {
    List<ProcessInfo> infos = ProcessListUtil.parseUnixOutput(
      "   PID S USER    COMMAND\n\n" +
      "     1 S user    " + myDir + "/dir/file\n" +
      "     2 S user    " + myDir + "/dir/dir/file\n" +
      "     3 S user    " + myDir + "/dir/dir/file param param\n" +
      "     4 S user    " + myDir + "/dir/dir/file with\n" +
      "     5 S user    " + myDir + "/dir/dir/file with spaces\n" +
      "     6 S user    " + myDir + "/dir/dir/file with spaces param param\n" +
      "     7 S user    " + myDir + "/dir/dir/file with spaces/foo\n" +
      "     8 S user    " + myDir + "/dir/dir/file/\n" +
      "     9 S user    " + myDir + "/dir/dir/file with spaces/\n" +
      "    10 S user    " + myDir + "/dir/dir\n" +
      "    11 S user    " + myDir + "/dir/dir/file/unknown\n" +
      "    12 S user    " + myDir + "/dir/dir/unknown\n" +
      "    13 S user    " + myDir + "/dir/dir/file_unknown\n"
      );
    assertOrderedEquals(infos,
                        new ProcessInfo(1, myDir + "/dir/file", "file", "", "user", "S"),
                        new ProcessInfo(2, myDir + "/dir/dir/file", "file", "", "user", "S"),
                        new ProcessInfo(3, myDir + "/dir/dir/file param param", "file", "param param", "user", "S"),
                        new ProcessInfo(4, myDir + "/dir/dir/file with", "file", "with", "user", "S"),
                        new ProcessInfo(5, myDir + "/dir/dir/file with spaces", "file with spaces", "", "user", "S"),
                        new ProcessInfo(6, myDir + "/dir/dir/file with spaces param param", "file with spaces", "param param", "user", "S"),
                        new ProcessInfo(7, myDir + "/dir/dir/file with spaces/foo", "file", "with spaces/foo", "user", "S"),
                        new ProcessInfo(8, myDir + "/dir/dir/file/", "file", "", "user", "S"),
                        new ProcessInfo(9, myDir + "/dir/dir/file with spaces/", "file with spaces", "", "user", "S"),
                        new ProcessInfo(10, myDir + "/dir/dir", "dir", "", "user", "S"));
  }

  public void testUnix_DetermineExecutableWithExtraSlashes() throws Exception {
    List<ProcessInfo> infos = ProcessListUtil.parseUnixOutput(
      "   PID S USER    COMMAND\n\n" +
      "     1 S user    //" + myDir + "//dir//file//\n" +
      "     2 S user    //" + myDir + "//dir//file// aaa bbb\n"
      );
    assertOrderedEquals(infos,
                        new ProcessInfo(1, "//" + myDir + "//dir//file//", "file", "", "user", "S"),
                        new ProcessInfo(2, "//" + myDir + "//dir//file// aaa bbb", "file", "aaa bbb", "user", "S"));
  }

  public void testUnix_VariousFormsPidStatUser() throws Exception {
    List<ProcessInfo> infos = ProcessListUtil.parseUnixOutput(
      "   PID STAT USER      COMMAND\n\n" +
      "     1 S    user      " + myDir + "/dir/file\n" +
      "   101 Ss   user_name " + myDir + "/dir/file\n"
    );
    assertOrderedEquals(infos,
                        new ProcessInfo(1, myDir + "/dir/file", "file", "", "user", "S"),
                        new ProcessInfo(101, myDir + "/dir/file", "file", "", "user_name", "Ss"));
  }

  public void testUnix_WrongFormat() throws Exception {
    assertNull(ProcessListUtil.parseUnixOutput(
      ""
    ));
    assertNull(ProcessListUtil.parseUnixOutput(
      "wrong format"));
    assertEmpty(ProcessListUtil.parseUnixOutput(
      "   PID S USER    COMMAND\n\n"
    ));
    assertEmpty(ProcessListUtil.parseUnixOutput(
      "   PID S USER    COMMAND\n\n" +
      "                           \n"
    ));
    assertNull(ProcessListUtil.parseUnixOutput(
      "     1 S user    " + myDir + "/dir/file\n"
    ));
    assertNull(ProcessListUtil.parseUnixOutput(
      "   PID S USER    XXX\n\n" +
      "     1 S user    " + myDir + "/dir/file\n"
    ));
    assertNull(ProcessListUtil.parseUnixOutput(
      "   PID S XXX     COMMAND\n\n" +
      "     1 S user    " + myDir + "/dir/file\n"
    ));
    assertNull(ProcessListUtil.parseUnixOutput(
      "   PID X USER    COMMAND\n\n" +
      "     1 S user    " + myDir + "/dir/file\n"
    ));
    assertNull(ProcessListUtil.parseUnixOutput(
      "   XXX S USER    COMMAND\n\n" +
      "     1 S user    " + myDir + "/dir/file\n"
    ));
  }

  public void testMac() throws Exception {
    List<ProcessInfo> infos = ProcessListUtil.parseUnixOutput(
      "   PID STAT USER    COMMAND\n\n" +
      "     1 S    user    " + myDir + "/dir/file\n" +
      "     2 S    user    " + myDir + "/dir/dir/file\n"
    );
    assertOrderedEquals(infos,
                        new ProcessInfo(1, myDir + "/dir/file", "file", "", "user", "S"),
                        new ProcessInfo(2, myDir + "/dir/dir/file", "file", "", "user", "S"));
  }

  public void testMac_DoNotIncludeZombies() throws Exception {
    List<ProcessInfo> infos = ProcessListUtil.parseUnixOutput(
      "   PID STAT USER    COMMAND\n\n" +
      "     1 S    user    " + myDir + "/dir/file\n" +
      "     2 Z    user    " + myDir + "/dir/file\n" +
      "     3 SZ   user    " + myDir + "/dir/file\n"
    );
    assertOrderedEquals(infos,
                        new ProcessInfo(1, myDir + "/dir/file", "file", "", "user", "S"));
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
                        new ProcessInfo(304, "smss.exe", "smss.exe", "", null, null),
                        new ProcessInfo(3052, "sihost.exe", "sihost.exe", "", null, null),
                        new ProcessInfo(3068, "taskhostw.exe {222A245B-E637-4AE9-A93F-A59CA119A75E}", "taskhostw.exe", "{222A245B-E637-4AE9-A93F-A59CA119A75E}", null, null),
                        new ProcessInfo(3164, "C:\\WINDOWS\\Explorer.EXE", "explorer.exe", "", null, null),
                        new ProcessInfo(3336, "TPAutoConnect.exe -q -i vmware -a COM1 -F 30", "TPAutoConnect.exe", "-q -i vmware -a COM1 -F 30", null, null),
                        new ProcessInfo(3348, "\\??\\C:\\WINDOWS\\system32\\conhost.exe 0x4", "conhost.exe", "0x4", null, null));
  }

  public void testOnWindows_WMIC_DoNotIncludeSystemIdleProcess() throws Exception {
    List<ProcessInfo> infos = ProcessListUtil.parseWMICOutput(
      "Caption                   CommandLine                                            ProcessId  \n" +
      "System Idle Process                                                              0          \n" +
      "System                                                                           4          \n" +
      "smss.exe                                                                         304        \n");
    assertOrderedEquals(infos,
                        new ProcessInfo(4, "System", "System", "", null, null),
                        new ProcessInfo(304, "smss.exe", "smss.exe", "", null, null));
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
                        new ProcessInfo(304, "smss.exe", "smss.exe", "", null, null),
                        new ProcessInfo(3052, "sihost.exe", "sihost.exe", "", null, null),
                        new ProcessInfo(3068, "taskhostw.exe", "taskhostw.exe", "", null, null),
                        new ProcessInfo(3164, "explorer.exe", "explorer.exe", "", null, null),
                        new ProcessInfo(3336, "TPAutoConnect.exe", "TPAutoConnect.exe", "", null, null),
                        new ProcessInfo(3348, "conhost.exe", "conhost.exe", "", null, null));
  }

  public void testWindows_TaskList_WrongFormat() throws Exception {
    assertEmpty(ProcessListUtil.parseListTasksOutput(""));
    assertNull(ProcessListUtil.parseListTasksOutput("wrong format"));
    assertNull(ProcessListUtil.parseListTasksOutput("\"\""));
    assertEmpty(ProcessListUtil.parseListTasksOutput("\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\"\n"));
  }
}