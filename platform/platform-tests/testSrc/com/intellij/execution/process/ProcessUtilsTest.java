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

}