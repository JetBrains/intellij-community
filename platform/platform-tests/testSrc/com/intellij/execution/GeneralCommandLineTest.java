/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.util.Function;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public class GeneralCommandLineTest {
  @Test
  public void printCommandLine() {
    GeneralCommandLine commandLine = new GeneralCommandLine();
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

  @Test
  public void unicodePath() throws Exception {
    String mark = String.valueOf(new Random().nextInt());
    File script;
    if (SystemInfo.isWindows) {
      script = ExecUtil.createTempExecutableScript(
        "path with spaces 'and quotes' и юникодом ", ".cmd",
        "@echo " + mark + "\n"
      );
    }
    else {
      script = ExecUtil.createTempExecutableScript(
        "path with spaces 'and quotes' и юникодом ", ".sh",
        "#!/bin/sh\n" + "echo " + mark + "\n"
      );
    }

    try {
      GeneralCommandLine commandLine = new GeneralCommandLine(script.getPath());
      String output = execAndGetOutput(commandLine, null);
      assertEquals(mark + "\n", StringUtil.convertLineSeparators(output));
    }
    finally {
      FileUtil.delete(script);
    }
  }

  @Test
  public void unicodeClassPath() throws Exception {
    assumeTrue(SystemInfo.isUnix);

    File dir = FileUtil.createTempDirectory("path with spaces 'and quotes' и юникодом ", ".tmp");
    try {
      GeneralCommandLine commandLine = makeJavaCommand(ParamPassingTest.class, dir);
      String output = execAndGetOutput(commandLine, null);
      assertEquals("=====\n=====\n", StringUtil.convertLineSeparators(output));
    }
    finally {
      FileUtil.delete(dir);
    }
  }

  @Test
  public void argumentsPassing() throws Exception {
    String[] parameters = {
      "with space", "\"quoted\"", "\"quoted with spaces\"", "", "  ", "param 1", "\"", "param2", "trailing slash\\"
    };

    GeneralCommandLine commandLine = makeJavaCommand(ParamPassingTest.class, null);
    commandLine.addParameters(parameters);
    String output = execAndGetOutput(commandLine, null);
    assertEquals("=====\n" + StringUtil.join(parameters, new Function<String, String>() {
      @Override
      public String fun(String s) {
        return ParamPassingTest.format(s);
      }
    }, "\n") + "\n=====\n", StringUtil.convertLineSeparators(output));
  }

  @Test
  public void unicodeArguments() throws Exception {
    assumeTrue("UTF-8".equals(System.getProperty("file.encoding")));

    File script;
    GeneralCommandLine commandLine;
    String encoding = null;
    if (SystemInfo.isWindows) {
      script = ExecUtil.createTempExecutableScript(
        "args.", ".js",
        "WSH.Echo(\"=====\");\n" +
        "for (i = 0; i < WSH.Arguments.length; i++) {\n" +
        "  WSH.Echo(WSH.Arguments(i));\n" +
        "}\n" +
        "WSH.Echo(\"=====\");\n"
      );
      commandLine = new GeneralCommandLine("cscript", "//Nologo", "//U", script.getPath());
      encoding = "UTF-16LE";
    }
    else {
      script = ExecUtil.createTempExecutableScript(
        "args.", ".sh",
        "#!/bin/sh\n\n" +
        "echo \"=====\"\n" +
        "for f in \"$@\" ; do echo \"$f\"; done\n" +
        "echo \"=====\"\n"
      );
      commandLine = new GeneralCommandLine(script.getPath());
    }

    try {
      commandLine.addParameters("немного", "юникодных", "параметров");
      String output = execAndGetOutput(commandLine, encoding);
      assertEquals("=====\nнемного\nюникодных\nпараметров\n=====\n", StringUtil.convertLineSeparators(output));
    }
    finally {
      FileUtil.delete(script);
    }
  }

  @Test
  public void winShellCommand() throws Exception {
    assumeTrue(SystemInfo.isWindows);

    String string = "http://localhost/wtf?a=b&c=d";
    String echo = ExecUtil.execAndReadLine(ExecUtil.getWindowsShellName(), "/c", "echo", string);
    assertEquals('"' + string + '"', echo);
  }

  @Test
  public void hackyEnvMap () throws Exception {
    GeneralCommandLine commandLine = new GeneralCommandLine();
    //noinspection ConstantConditions
    commandLine.getEnvironment().putAll(null);
  }

  @Test
  public void environmentPassing() throws Exception {
    Map<String, String> testEnv = new HashMap<String, String>();
    testEnv.put("VALUE_1", "some value");
    testEnv.put("VALUE_2", "another\n\"value\"");

    GeneralCommandLine commandLine = makeJavaCommand(EnvPassingTest.class, null);
    checkEnvPassing(commandLine, testEnv, true);
    checkEnvPassing(commandLine, testEnv, false);
  }

  @Test
  public void unicodeEnvironment() throws Exception {
    assumeTrue("UTF-8".equals(System.getProperty("file.encoding")));

    Map<String, String> testEnv = new HashMap<String, String>();
    testEnv.put("VALUE_1", "немного");
    testEnv.put("VALUE_2", "юникода");

    GeneralCommandLine commandLine = makeJavaCommand(EnvPassingTest.class, null);
    checkEnvPassing(commandLine, testEnv, true);
    checkEnvPassing(commandLine, testEnv, false);
  }


  private static String execAndGetOutput(GeneralCommandLine commandLine, @Nullable String encoding) throws Exception {
    Process process = commandLine.createProcess();
    byte[] bytes = FileUtil.loadBytes(process.getInputStream());
    String output = encoding != null ? new String(bytes, encoding) : new String(bytes);
    int result = process.waitFor();
    assertEquals("Command:\n" + commandLine.getCommandLineString() + "\nOutput:\n" + output, 0, result);
    return output;
  }

  private GeneralCommandLine makeJavaCommand(Class<?> testClass, @Nullable File copyTo) throws IOException {
    String className = testClass.getName();
    URL url = getClass().getClassLoader().getResource(className.replace(".", "/") + ".class");
    assertNotNull(url);

    GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath(System.getProperty("java.home") + (SystemInfo.isWindows ? "\\bin\\java.exe" : "/bin/java"));

    String encoding = System.getProperty("file.encoding");
    if (encoding != null) {
      commandLine.addParameter("-D" + "file.encoding=" + encoding);
    }

    commandLine.addParameter("-cp");
    String[] packages = className.split("\\.");
    File classFile = new File(url.getFile());
    if (copyTo == null) {
      File dir = classFile;
      for (String ignored : packages) dir = dir.getParentFile();
      commandLine.addParameter(dir.getPath());
    }
    else {
      File dir = copyTo;
      for (int i = 0; i < packages.length - 1; i++) dir = new File(dir, packages[i]);
      FileUtil.copy(classFile, new File(dir, classFile.getName()));
      commandLine.addParameter(copyTo.getPath());
    }

    commandLine.addParameter(className);
    commandLine.setRedirectErrorStream(true);

    return commandLine;
  }

  private static void checkEnvPassing(GeneralCommandLine commandLine, Map<String, String> testEnv, boolean passParentEnv) throws Exception {
    commandLine.getEnvironment().putAll(testEnv);
    commandLine.setPassParentEnvironment(passParentEnv);
    String output = execAndGetOutput(commandLine, null);

    Set<String> lines = new HashSet<String>(Arrays.asList(StringUtil.convertLineSeparators(output).split("\n")));
    lines.remove("=====");

    for (Map.Entry<String, String> entry : testEnv.entrySet()) {
      String str = EnvPassingTest.format(entry);
      assertTrue("\"" + str + "\" should be in " + lines,
                 lines.contains(str));
    }

    Map<String, String> parentEnv = System.getenv();
    List<String> missed = new ArrayList<String>();
    for (Map.Entry<String, String> entry : parentEnv.entrySet()) {
      String str = EnvPassingTest.format(entry);
      if (!lines.contains(str)) {
        missed.add(str);
      }
    }

    long pctMissed = Math.round((100.0 * missed.size()) / parentEnv.size());
    if (passParentEnv && pctMissed > 25 || !passParentEnv && pctMissed < 75) {
      fail("% missed: " + pctMissed + ", missed: " + missed + ", passed: " + lines);
    }
  }
}
