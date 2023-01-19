// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.impl;

import com.intellij.execution.process.OSProcessUtil;
import com.intellij.execution.process.ProcessInfo;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import junit.framework.TestCase;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
      """
          PID  PPID STAT USER    COMM

            1     0 S    user    /dir/file
            2     1 S    user    ./dir/dir/file
            3     2 S    user    ./dir/dir/file
        10000     3 S    user    ./dir/dir/file""",
      """
          PID  PPID STAT USER    COMMAND

            1     0 S    user    /dir/file
            2     1 S    user    ./dir/dir/file
            3     2 S    user    ./dir/dir/file param param
        10000     3 S    user    ./dir/dir/file param2 param2"""
    );
    assertOrderedEquals(infos,
                        new ProcessInfo(1, "/dir/file", "file", "", "/dir/file", 0, "user", ThreeState.UNSURE),
                        new ProcessInfo(2, "./dir/dir/file", "file", "", "./dir/dir/file", 1, "user", ThreeState.UNSURE),
                        new ProcessInfo(3, "./dir/dir/file param param", "file", "param param", "./dir/dir/file", 2, "user", ThreeState.UNSURE),
                        new ProcessInfo(10000, "./dir/dir/file param2 param2", "file", "param2 param2", "./dir/dir/file", 3, "user", ThreeState.UNSURE));
  }

  public void testMac_DoNotIncludeProcessedMissingOnTheSecondPSRun() {
    List<ProcessInfo> infos = ProcessListUtil.parseMacOutput(
      """
          PID  PPID STAT USER    COMM

            1     0 S    user    /dir/file
            2     1 S    user    /dir/file
        """,
      """
          PID  PPID STAT USER    COMMAND

            1     0 S    user    /dir/file
            5     1 S    user    /dir/file
        """
    );
    assertOrderedEquals(infos, new ProcessInfo(1, "/dir/file", "file", "", "/dir/file", 0, "user", ThreeState.UNSURE));
  }

  public void testMac_DoNotIncludeProcessedChangedOnTheSecondPSRun() {
    List<ProcessInfo> infos = ProcessListUtil.parseMacOutput(
      """
          PID  PPID STAT USER    COMM

            1     0 S    user    /dir/file
            2     1 S    user    /dir/file
            3     2 S    user    /dir/file
            4     3 S    user    /dir/file
        """,
      """
          PID  PPID STAT USER    COMMAND

            1     0 S    user    /dir/file param
            2     1 S    user    /dir/ffff
            3     2 S    user    /dir/file1
            4     3 S    user    /dir/file/1
        """
    );
    assertOrderedEquals(infos, new ProcessInfo(1, "/dir/file param", "file", "param", "/dir/file", 0, "user", ThreeState.UNSURE));
  }

  public void testMac_DoNotIncludeZombies() {
    List<ProcessInfo> infos = ProcessListUtil.parseMacOutput(
      """
          PID  PPID STAT USER    COMM

            1     0 S    user    /dir/file
            2     1 Z    user    /dir/file
            3     1 SZ   user    /dir/file
        """,
      """
          PID  PPID STAT USER    COMM

            1     0 S    user    /dir/file
            2     1 Z    user    /dir/file
            3     1 SZ   user    /dir/file
        """
    );
    assertOrderedEquals(infos, new ProcessInfo(1, "/dir/file", "file", "", "/dir/file", 0, "user", ThreeState.UNSURE));
  }

  public void testMac_VariousFormsPidStatUser() {
    List<ProcessInfo> infos = ProcessListUtil.parseMacOutput(
      """
          PID  PPID STAT USER      COMMAND

            1     0 S    user      /dir/file
          101     1 Ss   user_name /dir/file
        """,
      """
          PID  PPID STAT USER      COMM

            1     0 S    user      /dir/file
          101     1 Ss   user_name /dir/file
        """
    );
    assertOrderedEquals(infos,
                        new ProcessInfo(1, "/dir/file", "file", "", "/dir/file", 0, "user", ThreeState.UNSURE),
                        new ProcessInfo(101, "/dir/file", "file", "", "/dir/file", 1, "user_name", ThreeState.UNSURE));
  }

  public void testMac_WrongFormat() {
    assertNull(ProcessListUtil.parseMacOutput(
      """
           PID STAT USER    COMM

             1 S    user    /dir/file
        """,
      ""
    ));
    assertNull(ProcessListUtil.parseMacOutput(
      "",
      """
           PID STAT USER    COMMAND

             1 S    user    /dir/file
        """
    ));


    assertNull(ProcessListUtil.parseMacOutput(
      """
           PID STAT USER    COMM

             1 S    user    /dir/file
        """,
      "wrong format"
    ));
    assertNull(ProcessListUtil.parseMacOutput(
      "wrong format",
      """
           PID STAT USER    COMMAND

             1 S    user    /dir/file
        """
    ));

    assertEmpty(ProcessListUtil.parseMacOutput(
      """
           PID PPID STAT USER    COMM

             1    0 S    user    /dir/file
        """,
      """
           PID PPID S USER    COMMAND

                                  \s
        """
    ));
    assertEmpty(ProcessListUtil.parseMacOutput(
      """
           PID PPID S USER    COMMAND

                                  \s
        """,
      """
           PID PPID STAT USER    COMMAND

             1    0 S    user    /dir/file
        """
    ));
  }

  public void testWindows_WMIC() {
    List<ProcessInfo> infos = ProcessListUtil.parseWMICOutput(
      """
        Caption                   CommandLine                                            ExecutablePath                                ParentProcessId                          ProcessId \s
        smss.exe                                                                                                                       0                                        304       \s
        sihost.exe                sihost.exe                                                                                           304                                      3052      \s
        taskhostw.exe             taskhostw.exe {222A245B-E637-4AE9-A93F-A59CA119A75E}                                                 0                                        3068      \s
        explorer.exe              C:\\WINDOWS\\Explorer.EXE                                C:\\WINDOWS\\Explorer.EXE                       3068                                     3164      \s
        TPAutoConnect.exe         TPAutoConnect.exe -q -i vmware -a COM1 -F 30                                                         3164                                     3336      \s
        conhost.exe               \\??\\C:\\WINDOWS\\system32\\conhost.exe 0x4                \\??\\C:\\WINDOWS\\system32\\conhost.exe           0                                        3348      \s
        """,
      Map.of(
        304L, "user1",
        3052L, "user2",
        3068L, "user1",
        3164L, "user3"
      ),
      null);
    assertOrderedEquals(infos,
                        new ProcessInfo(304, "smss.exe", "smss.exe", "", null, 0, "user1", ThreeState.UNSURE),
                        new ProcessInfo(3052, "sihost.exe", "sihost.exe", "", null, 304, "user2", ThreeState.UNSURE),
                        new ProcessInfo(3068, "taskhostw.exe {222A245B-E637-4AE9-A93F-A59CA119A75E}", "taskhostw.exe", "{222A245B-E637-4AE9-A93F-A59CA119A75E}", null, 0, "user1", ThreeState.UNSURE),
                        new ProcessInfo(3164, "C:\\WINDOWS\\Explorer.EXE", "explorer.exe", "", "C:\\WINDOWS\\Explorer.EXE", 3068, "user3", ThreeState.UNSURE),
                        new ProcessInfo(3336, "TPAutoConnect.exe -q -i vmware -a COM1 -F 30", "TPAutoConnect.exe", "-q -i vmware -a COM1 -F 30", null, 3164, null, ThreeState.UNSURE),
                        new ProcessInfo(3348, "\\??\\C:\\WINDOWS\\system32\\conhost.exe 0x4", "conhost.exe", "0x4", "\\??\\C:\\WINDOWS\\system32\\conhost.exe", 0, null, ThreeState.UNSURE));
  }

  public void testOnWindows_WMIC_DoNotIncludeSystemIdleProcess() {
    List<ProcessInfo> infos = ProcessListUtil.parseWMICOutput(
      """
        Caption                   CommandLine                     ExecutablePath                       ParentProcessId                     ProcessId \s
        System Idle Process                                                                            -1                                  0         \s
        System                                                                                         0                                   4         \s
        smss.exe                                                                                       0                                   304       \s
        """,
      Collections.emptyMap(), null);
    assertOrderedEquals(infos,
                        new ProcessInfo(4, "System", "System", "", null, 0),
                        new ProcessInfo(304, "smss.exe", "smss.exe", "", null, 0));
  }

  public void testWindows_WMIC_WrongFormat() {
    assertNull(ProcessListUtil.parseWMICOutput(
      "", Collections.emptyMap(), null));
    assertNull(ProcessListUtil.parseWMICOutput(
      "wrong format", Collections.emptyMap(), null));
    assertEmpty(ProcessListUtil.parseWMICOutput(
      "Caption                   CommandLine                   ExecutablePath                         ParentProcessId                         ProcessId  \n",
      Collections.emptyMap(), null));
    assertNull(ProcessListUtil.parseWMICOutput(
      "smss.exe                                                                         304        \n", Collections.emptyMap(), null));

    assertNull(ProcessListUtil.parseWMICOutput(
      """
        Caption                   XXX                ExecutablePath                                    ProcessId \s
        smss.exe                                                                                       304       \s
        """, Collections.emptyMap(), null));
    assertNull(ProcessListUtil.parseWMICOutput(
      """
        Caption                   CommandLine               ExecutablePath                             XXX \s
        smss.exe                                                                                       304       \s
        """, Collections.emptyMap(), null));
    assertEmpty(ProcessListUtil.parseWMICOutput(
      """
        Caption                   CommandLine               ExecutablePath                         ParentProcessId                         ProcessId \s
                                                                                                                 \s
        """, Collections.emptyMap(), null));
  }

  public void testWindows_TaskList() {
    List<ProcessInfo> infos = ProcessListUtil.parseListTasksOutput(
      """
        "smss.exe","304","Services","0","224 K","Unknown","N/A","0:00:00","N/A"
        "sihost.exe","3052","Console","1","10,924 K","Running","VM-WINDOWS\\Anton Makeev","0:00:02","N/A"
        "taskhostw.exe","3068","Console","1","5,860 K","Running","VM-WINDOWS\\Anton Makeev","0:00:00","Task Host Window"
        "explorer.exe","3164","Console","1","30,964 K","Running","VM-WINDOWS\\Anton Makeev","0:00:04","N/A"
        "TPAutoConnect.exe","3336","Console","1","5,508 K","Running","VM-WINDOWS\\Anton Makeev","0:00:04","HiddenTPAutoConnectWindow"
        "conhost.exe","3348","Console","1","1,172 K","Unknown","VM-WINDOWS\\Anton Makeev","0:00:00","N/A"
        """,
      "VM-WINDOWS\\Anton Makeev");
    assertOrderedEquals(infos,
                        new ProcessInfo(304, "smss.exe", "smss.exe", "", null, -1, null, ThreeState.NO),
                        new ProcessInfo(3052, "sihost.exe", "sihost.exe", "",  null, -1, "VM-WINDOWS\\Anton Makeev", ThreeState.YES),
                        new ProcessInfo(3068, "taskhostw.exe", "taskhostw.exe", "",  null, -1, "VM-WINDOWS\\Anton Makeev", ThreeState.YES),
                        new ProcessInfo(3164, "explorer.exe", "explorer.exe", "", null, -1, "VM-WINDOWS\\Anton Makeev", ThreeState.YES),
                        new ProcessInfo(3336, "TPAutoConnect.exe", "TPAutoConnect.exe", "",  null, -1, "VM-WINDOWS\\Anton Makeev", ThreeState.YES),
                        new ProcessInfo(3348, "conhost.exe", "conhost.exe", "",  null, -1, "VM-WINDOWS\\Anton Makeev", ThreeState.YES));
  }

  public void testWindows_TaskList_WrongFormat() {
    assertEmpty(ProcessListUtil.parseListTasksOutput("", null));
    assertNull(ProcessListUtil.parseListTasksOutput("wrong format", null));
    assertNull(ProcessListUtil.parseListTasksOutput("\"\"", null));
    assertEmpty(ProcessListUtil.parseListTasksOutput("\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\"\n", null));
  }

  public void testWinProcessListHelperOutputParsing() {
    List<ProcessInfo> infos = ProcessListUtil.parseWinProcessListHelperOutput(
      """
        pid:19608
        parentPid:0
        name:SourceTree.exe
        cmd:"C:\\\\Users\\\\grahams\\\\AppData\\\\Local\\\\SourceTree\\\\app-3.1.3\\\\SourceTree.exe"
        pid:12300
        parentPid:0
        name:conhost.exe
        cmd:\\\\??\\\\C:\\\\Windows\\\\system32\\\\conhost.exe 0x4
        pid:26284
        parentPid:0
        name:Unity Hub.exe
        cmd:"C:\\\\Program Files\\\\Unity Hub\\\\Unity Hub.exe" --no-sandbox --lang=en-US --node-integration=true /prefetch:1
        pid:25064
        parentPid:12300
        name:cmd.exe
        cmd:"C:\\\\WINDOWS\\\\system32\\\\cmd.exe" /c "pause\\necho 123"
        """,
      Map.of(
        19608L, "user1",
        12300L, "user2",
        26284L, "user1"
      ),
      null);
    assertOrderedEquals(
      infos,
      new ProcessInfo(19608, "\"C:\\Users\\grahams\\AppData\\Local\\SourceTree\\app-3.1.3\\SourceTree.exe\"", "SourceTree.exe", "", null, 0, "user1", ThreeState.UNSURE),
      new ProcessInfo(12300, "\\??\\C:\\Windows\\system32\\conhost.exe 0x4", "conhost.exe", "0x4", null, 0, "user2", ThreeState.UNSURE),
      new ProcessInfo(26284,
                      "\"C:\\Program Files\\Unity Hub\\Unity Hub.exe\" --no-sandbox --lang=en-US --node-integration=true /prefetch:1",
                      "Unity Hub.exe",
                      "--no-sandbox --lang=en-US --node-integration=true /prefetch:1", null, 0, "user1", ThreeState.UNSURE),
      new ProcessInfo(25064,
                      "\"C:\\WINDOWS\\system32\\cmd.exe\" /c \"pause\necho 123\"",
                      "cmd.exe",
                      "/c \"pause\necho 123\"", null, 12300, null, ThreeState.UNSURE)
    );

    assertNull(ProcessListUtil.parseWinProcessListHelperOutput("", Collections.emptyMap(), null));
    assertNull(ProcessListUtil.parseWinProcessListHelperOutput("Hello", Collections.emptyMap(), null));
    assertNull(ProcessListUtil.parseWinProcessListHelperOutput("""
                                                                 pid:12345
                                                                 name:git.exe
                                                                 cmd:git.exe fetch
                                                                 pid:1x
                                                                 name:node.exe
                                                                 cmd:node.exe qq
                                                                 """,
                                                               Collections.emptyMap(), null));
  }

  public void testMacOwnership() {
    String commandOnly = """
      PID  PPID STAT USER    COMM
          
        1     0 S    user    /dir/file""";
    String full = """
          PID  PPID STAT USER    COMMAND

            1     0 S    user    /dir/file""";
    List<ProcessInfo> infos = ProcessListUtil.parseMacOutput(commandOnly, full, null);
    assertOrderedEquals(infos, new ProcessInfo(1, "/dir/file", "file", "", "/dir/file", 0, "user", ThreeState.UNSURE));

    infos = ProcessListUtil.parseMacOutput(commandOnly, full, "user");
    assertOrderedEquals(infos, new ProcessInfo(1, "/dir/file", "file", "", "/dir/file", 0, "user", ThreeState.YES));

    infos = ProcessListUtil.parseMacOutput(commandOnly, full, "user2");
    assertOrderedEquals(infos, new ProcessInfo(1, "/dir/file", "file", "", "/dir/file", 0, "user", ThreeState.NO));
  }

  public void testWMICOwnership() {
    String output = """
      Caption                   CommandLine                                            ExecutablePath                                ParentProcessId                          ProcessId \s
      smss.exe                                                                                                                       0                                        304       \s
      sihost.exe                sihost.exe                                                                                           304                                      3052      \s
      taskhostw.exe             taskhostw.exe {222A245B-E637-4AE9-A93F-A59CA119A75E}                                                 0                                        3068      \s
      explorer.exe              C:\\WINDOWS\\Explorer.EXE                                C:\\WINDOWS\\Explorer.EXE                       3068                                     3164      \s
      TPAutoConnect.exe         TPAutoConnect.exe -q -i vmware -a COM1 -F 30                                                         3164                                     3336      \s
      conhost.exe               \\??\\C:\\WINDOWS\\system32\\conhost.exe 0x4                \\??\\C:\\WINDOWS\\system32\\conhost.exe           0                                        3348      \s
      """;
    Map<Long, String> processOwners = Map.of(
      304L, "user1",
      3052L, "user2",
      3068L, "user1"
    );
    List<ProcessInfo> infos = ProcessListUtil.parseWMICOutput(output, processOwners, null);
    assertNull(ContainerUtil.find(infos, it -> it.isOwnedByCurrentUser() != ThreeState.UNSURE));

    infos = ProcessListUtil.parseWMICOutput(output, processOwners, "user1");
    assertEquals(ThreeState.YES, ContainerUtil.find(infos, it -> it.getPid() == 304).isOwnedByCurrentUser());
    assertEquals(ThreeState.YES, ContainerUtil.find(infos, it -> it.getPid() == 3068).isOwnedByCurrentUser());
    assertEquals(ThreeState.NO, ContainerUtil.find(infos, it -> it.getPid() == 3052).isOwnedByCurrentUser());
    assertEquals(ThreeState.NO, ContainerUtil.find(infos, it -> it.getPid() == 3348).isOwnedByCurrentUser());
  }

  public void testWinProcessListHelperOwnership() {
    String output = """
      pid:19608
      parentPid:0
      name:SourceTree.exe
      cmd:"C:\\\\Users\\\\grahams\\\\AppData\\\\Local\\\\SourceTree\\\\app-3.1.3\\\\SourceTree.exe"
      pid:12300
      parentPid:0
      name:conhost.exe
      cmd:\\\\??\\\\C:\\\\Windows\\\\system32\\\\conhost.exe 0x4
      pid:26284
      parentPid:0
      name:Unity Hub.exe
      cmd:"C:\\\\Program Files\\\\Unity Hub\\\\Unity Hub.exe" --no-sandbox --lang=en-US --node-integration=true /prefetch:1
      pid:25064
      parentPid:12300
      name:cmd.exe
      cmd:"C:\\\\WINDOWS\\\\system32\\\\cmd.exe" /c "pause\\necho 123"
      """;
    Map<Long, String> processOwners = Map.of(
      19608L, "user1",
      12300L, "user2",
      26284L, "user1"
    );
    List<ProcessInfo> infos = ProcessListUtil.parseWinProcessListHelperOutput(output, processOwners, null);
    assertNull(ContainerUtil.find(infos, it -> it.isOwnedByCurrentUser() != ThreeState.UNSURE));

    infos = ProcessListUtil.parseWinProcessListHelperOutput(output, processOwners, "user1");
    assertEquals(ThreeState.YES, ContainerUtil.find(infos, it -> it.getPid() == 19608).isOwnedByCurrentUser());
    assertEquals(ThreeState.YES, ContainerUtil.find(infos, it -> it.getPid() == 26284).isOwnedByCurrentUser());
    assertEquals(ThreeState.NO, ContainerUtil.find(infos, it -> it.getPid() == 12300).isOwnedByCurrentUser());
    assertEquals(ThreeState.NO, ContainerUtil.find(infos, it -> it.getPid() == 25064).isOwnedByCurrentUser());
  }
}
