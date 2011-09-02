/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.execution;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.UsefulTestCase;

import java.io.File;
import java.net.URL;
import java.util.*;

public class GeneralCommandLineTest extends UsefulTestCase {
  public void testPrintCommandLine() {
    final GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath("e x e path");
    commandLine.addParameter("with space");
    commandLine.addParameter("\"quoted\"");
    commandLine.addParameter("\"quoted with spaces\"");
    commandLine.addParameters("param 1", "param2");
    commandLine.addParameter("trailing slash\\");
    assertEquals("\"e x e path\"" +
                 " \"with space\"" +
                 " \\\"quoted\\\"" +
                 " \"\\\"quoted with spaces\\\"\"" +
                 " \"param 1\"" +
                 " param2" +
                 " \"trailing slash\\\"",
                 commandLine.getCommandLineString());
  }

  public void testExecuteCommandLine() throws Exception {
    final File tempFile;
    if (SystemInfo.isWindows) {
      final URL url = getClass().getClassLoader().getResource("com/intellij/execution/printArgs.exe");
      assertNotNull(url);
      tempFile = FileUtil.createTempFile("path with spaces 'and quotes' ", ".exe");
      FileUtil.copy(new File(url.getFile()), tempFile);
    }
    else {
      tempFile = ExecUtil.createTempExecutableScript(
        "path with spaces \"and quotes\" ", ".sh",
        "#!/bin/sh\n\n" +
        "echo \"=====\"\n" +
        "for f in \"$@\" ; do echo \"$f\"; done\n" +
        "echo \"=====\"\n"
      );
    }

    final String[] parameters = { "with space", "\"quoted\"", "\"quoted with spaces\"", "", "  ", "param 1", "\"", "param2", "trailing slash\\" };
    try {
      final GeneralCommandLine commandLine = new GeneralCommandLine();
      commandLine.setExePath(tempFile.getCanonicalPath());
      commandLine.addParameters(parameters);
      commandLine.setRedirectErrorStream(true);
      final Process process = commandLine.createProcess();
      final String output = FileUtil.loadTextAndClose(process.getInputStream());
      final int result = process.waitFor();

      assertEquals("Command:\n" + commandLine.getCommandLineString() + "\nOutput:\n" + output,
                   0, result);
      assertEquals("=====\n" + StringUtil.join(parameters, "\n") + "\n=====\n",
                   StringUtil.convertLineSeparators(output));
    }
    finally {
      FileUtil.delete(tempFile);
    }
  }

  public void testEnvironmentPassing() throws Exception {
    final URL url = getClass().getClassLoader().getResource("com/intellij/execution/EnvPassingTest.class");
    assertNotNull(url);
    final File testClass = new File(url.getFile());

    final String javaHome = System.getenv("JAVA_HOME");
    final GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath(javaHome != null ? javaHome + "/bin/java" : "java");
    commandLine.addParameter("-cp");
    commandLine.addParameter(testClass.getParentFile().getParentFile().getParentFile().getParentFile().getAbsolutePath());
    commandLine.addParameter("com.intellij.execution." + testClass.getName().replace(".class", ""));
    commandLine.setRedirectErrorStream(true);

    final Map<String, String> testEnv = new HashMap<String, String>();
    testEnv.put("VALUE_1", "some value");
    testEnv.put("VALUE_2", "another\n\"value\"");

    checkEnvPassing(commandLine, testEnv, true);
    checkEnvPassing(commandLine, testEnv, false);
  }

  private static void checkEnvPassing(final GeneralCommandLine commandLine,
                                      final Map<String, String> testEnv,
                                      final boolean passParentEnv) throws Exception {
    commandLine.setEnvParams(testEnv);
    commandLine.setPassParentEnvs(passParentEnv);

    final Process process = commandLine.createProcess();
    final String output = FileUtil.loadTextAndClose(process.getInputStream());
    final int result = process.waitFor();
    assertEquals("Command:\n" + commandLine.getCommandLineString() + "\nOutput:\n" + output,
                 0, result);

    final Set<String> lines = new HashSet<String>(Arrays.asList(StringUtil.convertLineSeparators(output).split("\n")));
    lines.remove("=====");

    for (Map.Entry<String, String> entry : testEnv.entrySet()) {
      final String str = EnvPassingTest.formatEntry(entry);
      assertTrue("\"" + str + "\" should be in " + lines,
                 lines.contains(str));
    }

    final Map<String, String> parentEnv = System.getenv();
    final List<String> missed = new ArrayList<String>();
    for (Map.Entry<String, String> entry : parentEnv.entrySet()) {
      final String str = EnvPassingTest.formatEntry(entry);
      if (!lines.contains(str)) {
        missed.add(str);
      }
    }

    final long pctMissed = Math.round((100.0 * missed.size()) / parentEnv.size());
    if (passParentEnv && pctMissed >= 10 || !passParentEnv && pctMissed <= 90) {
      fail("% missed: " + pctMissed + ", missed: " + missed + ", passed: " + lines);
    }
  }
}
