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
package com.intellij.execution.process;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.testFramework.UsefulTestCase;

import java.io.File;
import java.util.Arrays;

public class ProcessUtilsTest extends UsefulTestCase {
  private File myDir;

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
    FileUtil.delete(myDir);
    super.tearDown();
  }

  public void testProcessList_WorksOnAllPlatforms() throws Exception {
    assertNotEmpty(Arrays.asList(ProcessUtils.getProcessList()));
  }
  
  public void testProcessListOnLinux_DetermineExecutable() throws Exception {
    ProcessInfo[] infos = ProcessListLinux.parseOutput(
      false,
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
                        new ProcessInfo(1, myDir + "/dir/file", "", "user", "S"),
                        new ProcessInfo(2, myDir + "/dir/dir/file", "", "user", "S"),
                        new ProcessInfo(3, myDir + "/dir/dir/file", "param param", "user", "S"),
                        new ProcessInfo(4, myDir + "/dir/dir/file", "with", "user", "S"),
                        new ProcessInfo(5, myDir + "/dir/dir/file with spaces", "", "user", "S"),
                        new ProcessInfo(6, myDir + "/dir/dir/file with spaces", "param param", "user", "S"),
                        new ProcessInfo(7, myDir + "/dir/dir/file", "with spaces/foo", "user", "S"),
                        new ProcessInfo(8, myDir + "/dir/dir/file/", "", "user", "S"),
                        new ProcessInfo(9, myDir + "/dir/dir/file with spaces/", "", "user", "S"),
                        new ProcessInfo(10, myDir + "/dir/dir", "", "user", "S"));
  }
  
  public void testProcessListOnLinux_DetermineExecutableWithExtraSlashes() throws Exception {
    ProcessInfo[] infos = ProcessListLinux.parseOutput(
      false,
      "   PID S USER    COMMAND\n\n" +
      "     1 S user    //" + myDir + "//dir//file//\n" +
      "     2 S user    //" + myDir + "//dir//file// aaa bbb\n"
      );
    assertOrderedEquals(infos,
                        new ProcessInfo(1, "//" + myDir + "//dir//file//", "", "user", "S"),
                        new ProcessInfo(2, "//" + myDir + "//dir//file//", "aaa bbb", "user", "S"));
  }

  public void testProcessListOnLinuxMac_WrongFormat() throws Exception {
    assertEmpty(ProcessListLinux.parseOutput(
      false,
      ""
    ));
    assertEmpty(ProcessListLinux.parseOutput(
      false,
      "wrong format"));
    assertEmpty(ProcessListLinux.parseOutput(
      false,
      "   PID S USER    COMMAND\n\n"
    ));
    assertEmpty(ProcessListLinux.parseOutput(
      false,
      "   PID S USER    COMMAND\n\n" +
      "                           \n"
    ));
    assertEmpty(ProcessListLinux.parseOutput(
      false,
      "     1 S user    " + myDir + "/dir/file\n"
    ));
    assertEmpty(ProcessListLinux.parseOutput(
      false,
      "   PID S USER    XXX\n\n" +
      "     1 S user    " + myDir + "/dir/file\n"
    ));
    assertEmpty(ProcessListLinux.parseOutput(
      false,
      "   PID S XXX     COMMAND\n\n" +
      "     1 S user    " + myDir + "/dir/file\n"
    ));
    assertEmpty(ProcessListLinux.parseOutput(
      false,
      "   PID X USER    COMMAND\n\n" +
      "     1 S user    " + myDir + "/dir/file\n"
    ));
    assertEmpty(ProcessListLinux.parseOutput(
      false,
      "   XXX S USER    COMMAND\n\n" +
      "     1 S user    " + myDir + "/dir/file\n"
    ));
  }

  public void testProcessListOnMac() throws Exception {
    ProcessInfo[] infos = ProcessListLinux.parseOutput(
      true,
      "   PID STAT USER    COMMAND\n\n" +
      "     1 S    user    " + myDir + "/dir/file\n" +
      "     2 S    user    " + myDir + "/dir/dir/file\n"
    );
    assertOrderedEquals(infos,
                        new ProcessInfo(1, myDir + "/dir/file", "", "user", "S"),
                        new ProcessInfo(2, myDir + "/dir/dir/file", "", "user", "S"));
  }

  public void testProcessListOnMac_DoNotIncludeZombies() throws Exception {
    ProcessInfo[] infos = ProcessListLinux.parseOutput(
      true,
      "   PID STAT USER    COMMAND\n\n" +
      "     1 S    user    " + myDir + "/dir/file\n" +
      "     2 Z    user    " + myDir + "/dir/file\n" +
      "     3 SZ   user    " + myDir + "/dir/file\n"
    );
    assertOrderedEquals(infos,
                        new ProcessInfo(1, myDir + "/dir/file", "", "user", "S"));
  }

  public void testProcessListOnLinuxMac_VariousFormsPidStatUser() throws Exception {
    ProcessInfo[] infos = ProcessListLinux.parseOutput(
      true,
      "   PID STAT USER      COMMAND\n\n" +
      "     1 S    user      " + myDir + "/dir/file\n" +
      "   101 Ss   user_name " + myDir + "/dir/file\n"
      );
    assertOrderedEquals(infos,
                        new ProcessInfo(1, myDir + "/dir/file", "", "user", "S"),
                        new ProcessInfo(101, myDir + "/dir/file", "", "user_name", "Ss"));
  }

  public void testWindows_WMIC() throws Exception {
    ProcessInfo[] infos = ProcessListWin32.parseWMICOutput(
      "Caption                   CommandLine                                            ProcessId  \n" +
      "smss.exe                                                                         304        \n" +
      "sihost.exe                sihost.exe                                             3052       \n" +
      "taskhostw.exe             taskhostw.exe {222A245B-E637-4AE9-A93F-A59CA119A75E}   3068       \n" +
      "explorer.exe              C:\\WINDOWS\\Explorer.EXE                                3164       \n" +
      "TPAutoConnect.exe         TPAutoConnect.exe -q -i vmware -a COM1 -F 30           3336       \n" +
      "conhost.exe               \\??\\C:\\WINDOWS\\system32\\conhost.exe 0x4                3348       \n");
    assertOrderedEquals(infos,
                        new ProcessInfo(304, "smss.exe", "", null, null),
                        new ProcessInfo(3052, "sihost.exe", "", null, null),
                        new ProcessInfo(3068, "taskhostw.exe", "{222A245B-E637-4AE9-A93F-A59CA119A75E}", null, null),
                        new ProcessInfo(3164, "explorer.exe", "", null, null),
                        new ProcessInfo(3336, "TPAutoConnect.exe", "-q -i vmware -a COM1 -F 30", null, null),
                        new ProcessInfo(3348, "conhost.exe", "0x4", null, null));
  }

  public void testOnWindows_WMIC_DoNotIncludeSystemIdleProcess() throws Exception {
    ProcessInfo[] infos = ProcessListWin32.parseWMICOutput(
      "Caption                   CommandLine                                            ProcessId  \n" +
      "System Idle Process                                                              0          \n" +
      "System                                                                           4          \n" +
      "smss.exe                                                                         304        \n");
    assertOrderedEquals(infos,
                        new ProcessInfo(4, "System", "", null, null),
                        new ProcessInfo(304, "smss.exe", "", null, null));
  }

  public void testWindows_WMIC_WrongFormat() throws Exception {
    assertNull(ProcessListWin32.parseWMICOutput(
      ""));
    assertNull(ProcessListWin32.parseWMICOutput(
      "wrong format"));
    assertEmpty(ProcessListWin32.parseWMICOutput(
      "Caption                   CommandLine                                            ProcessId  \n"));
    assertNull(ProcessListWin32.parseWMICOutput(
      "smss.exe                                                                         304        \n"));

    assertNull(ProcessListWin32.parseWMICOutput(
      "Caption                   XXX                                                    ProcessId  \n" +
      "smss.exe                                                                         304        \n"));
    assertNull(ProcessListWin32.parseWMICOutput(
      "Caption                   CommandLine                                            XXX  \n" +
      "smss.exe                                                                         304        \n"));
    assertEmpty(ProcessListWin32.parseWMICOutput(
      "Caption                   CommandLine                                            ProcessId  \n" +
      "                                                                                            \n"));
  }

  public void testWindows_TaskList() throws Exception {
    ProcessInfo[] infos = ProcessListWin32.parseListTasksOutput(
      "\"smss.exe\",\"304\",\"Services\",\"0\",\"224 K\",\"Unknown\",\"N/A\",\"0:00:00\",\"N/A\"\n" +
      "\"sihost.exe\",\"3052\",\"Console\",\"1\",\"10,924 K\",\"Running\",\"VM-WINDOWS\\Anton Makeev\",\"0:00:02\",\"N/A\"\n" +
      "\"taskhostw.exe\",\"3068\",\"Console\",\"1\",\"5,860 K\",\"Running\",\"VM-WINDOWS\\Anton Makeev\",\"0:00:00\",\"Task Host Window\"\n" +
      "\"explorer.exe\",\"3164\",\"Console\",\"1\",\"30,964 K\",\"Running\",\"VM-WINDOWS\\Anton Makeev\",\"0:00:04\",\"N/A\"\n" +
      "\"TPAutoConnect.exe\",\"3336\",\"Console\",\"1\",\"5,508 K\",\"Running\",\"VM-WINDOWS\\Anton Makeev\",\"0:00:04\",\"HiddenTPAutoConnectWindow\"\n" +
      "\"conhost.exe\",\"3348\",\"Console\",\"1\",\"1,172 K\",\"Unknown\",\"VM-WINDOWS\\Anton Makeev\",\"0:00:00\",\"N/A\"\n");
    assertOrderedEquals(infos,
                        new ProcessInfo(304, "smss.exe", "", null, null),
                        new ProcessInfo(3052, "sihost.exe", "", null, null),
                        new ProcessInfo(3068, "taskhostw.exe", "", null, null),
                        new ProcessInfo(3164, "explorer.exe", "", null, null),
                        new ProcessInfo(3336, "TPAutoConnect.exe", "", null, null),
                        new ProcessInfo(3348, "conhost.exe", "", null, null));
  }

  public void testWindows_TaskList_WrongFormat() throws Exception {
    assertEmpty(ProcessListWin32.parseListTasksOutput(""));
    assertEmpty(ProcessListWin32.parseListTasksOutput("wrong format"));
    assertEmpty(ProcessListWin32.parseListTasksOutput("\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\"\n"));
  }
}