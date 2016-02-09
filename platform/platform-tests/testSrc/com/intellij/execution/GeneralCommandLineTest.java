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
package com.intellij.execution;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.GeneralCommandLine.ParentEnvironmentType;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;

import static com.intellij.openapi.util.Pair.pair;
import static com.intellij.util.containers.ContainerUtil.newHashMap;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public class GeneralCommandLineTest {
  private static final String[] ARGUMENTS = {
    "with space",
    "\"quoted\"",
    "\"quoted with spaces\"",
    "",
    "  ",
    "param 1",
    "\"",
    "quote\"inside",
    "space \"and \"quotes\" inside",
    "\"space \"and \"quotes\" inside\"",
    "param2",
    "trailing slash\\",
    (SystemInfo.isWindows ? "windows_sucks" : "two trailing slashes\\\\")
  };

  @SuppressWarnings("SpellCheckingInspection") private static final String UNICODE_RU = "Юникоде";
  @SuppressWarnings("SpellCheckingInspection") private static final String UNICODE_EU = "Úñíçødê";

  private static final String UNICODE;
  static {
    if (SystemInfo.isWindows) {
      String jnuEncoding = System.getProperty("sun.jnu.encoding");
      if ("Cp1251".equalsIgnoreCase(jnuEncoding)) UNICODE = UNICODE_RU;
      else if ("Cp1252".equalsIgnoreCase(jnuEncoding)) UNICODE = UNICODE_EU;
      else UNICODE = null;
    }
    else {
      UNICODE = UNICODE_RU + "_" + UNICODE_EU;
    }
  }

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
    String prefix = "spaces 'and quotes' and " + UNICODE_RU + "_" + UNICODE_EU + " ";

    File script;
    if (SystemInfo.isWindows) {
      script = ExecUtil.createTempExecutableScript(prefix, ".cmd", "@echo " + mark + "\n");
    }
    else {
      script = ExecUtil.createTempExecutableScript(prefix, ".sh", "#!/bin/sh\n" + "echo " + mark + "\n");
    }

    try {
      String output = execAndGetOutput(new GeneralCommandLine(script.getPath()));
      assertEquals(mark + "\n", StringUtil.convertLineSeparators(output));
    }
    finally {
      FileUtil.delete(script);
    }
  }

  @Test
  public void unicodeClassPath() throws Exception {
    assumeTrue(UNICODE != null);

    File dir = FileUtil.createTempDirectory("spaces 'and quotes' and " + UNICODE, ".tmp");
    try {
      Pair<GeneralCommandLine, File> command = makeHelperCommand(dir, CommandTestHelper.ARG, "test");
      String output = execHelper(command);
      assertEquals("test\n", StringUtil.convertLineSeparators(output));
    }
    finally {
      FileUtil.delete(dir);
    }
  }

  @Test
  public void passingArgumentsToJavaApp() throws Exception {
    String[] args = ArrayUtil.mergeArrays(ARGUMENTS, "&<>()@^|", "\"&<>()@^|\"");
    Pair<GeneralCommandLine, File> command = makeHelperCommand(null, CommandTestHelper.ARG, args);
    String output = execHelper(command);
    checkParamPassing(output, args);
  }

  @Test
  public void passingArgumentsToJavaAppThroughWinShell() throws Exception {
    assumeTrue(SystemInfo.isWindows);

    Pair<GeneralCommandLine, File> command = makeHelperCommand(null, CommandTestHelper.ARG, ARGUMENTS);
    String javaPath = command.first.getExePath();
    command.first.setExePath(ExecUtil.getWindowsShellName());
    command.first.getParametersList().prependAll("/D", "/C", "call", javaPath);
    String output = execHelper(command);
    checkParamPassing(output, ARGUMENTS);
  }

  @Test
  public void passingArgumentsToJavaAppThroughCmdScriptAndWinShell() throws Exception {
    assumeTrue(SystemInfo.isWindows);

    Pair<GeneralCommandLine, File> command = makeHelperCommand(null, CommandTestHelper.ARG);
    File script = ExecUtil.createTempExecutableScript("my script ", ".cmd", "@" + command.first.getCommandLineString() + " %*");
    try {
      GeneralCommandLine commandLine = new GeneralCommandLine(ExecUtil.getWindowsShellName(), "/D", "/C", "call", script.getAbsolutePath());
      commandLine.addParameters(ARGUMENTS);
      String output = execHelper(pair(commandLine, command.second));
      checkParamPassing(output, ARGUMENTS);
    }
    finally {
      FileUtil.delete(script);
    }
  }

  @Test
  public void unicodeParameters() throws Exception {
    assumeTrue(UNICODE != null);

    String[] args = {"some", UNICODE, "parameters"};
    Pair<GeneralCommandLine, File> command = makeHelperCommand(null, CommandTestHelper.ARG, args);
    String output = execHelper(command);
    checkParamPassing(output, args);
  }

  @Test
  public void winShellCommand() {
    assumeTrue(SystemInfo.isWindows);

    String string = "http://localhost/wtf?a=b&c=d";
    String echo = ExecUtil.execAndReadLine(new GeneralCommandLine(ExecUtil.getWindowsShellName(), "/c", "echo", string));
    assertEquals('"' + string + '"', echo);
  }

  @Test
  public void winShellScriptQuoting() throws Exception {
    assumeTrue(SystemInfo.isWindows);

    String scriptPrefix = "my_script";
    for (String scriptExt : new String[]{".cmd", ".bat"}) {
      File script = ExecUtil.createTempExecutableScript(scriptPrefix, scriptExt, "@echo %1\n");
      String param = "a&b";
      GeneralCommandLine commandLine = new GeneralCommandLine(script.getAbsolutePath(), param);
      String text = commandLine.getPreparedCommandLine(Platform.WINDOWS);
      assertEquals(commandLine.getExePath() + "\n" + StringUtil.wrapWithDoubleQuote(param), text);
      try {
        String output = execAndGetOutput(commandLine);
        assertEquals(StringUtil.wrapWithDoubleQuote(param), output.trim());
      }
      finally {
        FileUtil.delete(script);
      }
    }
  }

  @Test
  public void winShellQuotingWithExtraSwitch() throws Exception {
    assumeTrue(SystemInfo.isWindows);

    String param = "a&b";
    GeneralCommandLine commandLine = new GeneralCommandLine(ExecUtil.getWindowsShellName(), "/D", "/C", "echo", param);
    String output = execAndGetOutput(commandLine);
    assertEquals(StringUtil.wrapWithDoubleQuote(param), output.trim());
  }

  @Test
  public void hackyEnvMap() {
    Map<String, String> env = new GeneralCommandLine().getEnvironment();

    //noinspection ConstantConditions
    env.putAll(null);

    try {
      env.put("key1", null);
      fail("null values should be rejected");
    }
    catch (AssertionError ignored) { }

    try {
      Map<String, String> indirect = newHashMap(pair("key2", (String)null));
      env.putAll(indirect);
      fail("null values should be rejected");
    }
    catch (AssertionError ignored) { }
  }

  @Test
  public void environmentPassing() throws Exception {
    Map<String, String> testEnv = new HashMap<>();
    testEnv.put("VALUE_1", "some value");
    testEnv.put("VALUE_2", "another\n\"value\"");

    Pair<GeneralCommandLine, File> command = makeHelperCommand(null, CommandTestHelper.ENV);
    checkEnvPassing(command, testEnv, true);
    checkEnvPassing(command, testEnv, false);
  }

  @Test
  public void unicodeEnvironment() throws Exception {
    assumeTrue("UTF-8".equals(System.getProperty("file.encoding")));

    Map<String, String> testEnv = newHashMap(pair("VALUE_1", UNICODE_RU), pair("VALUE_2", UNICODE_EU));
    Pair<GeneralCommandLine, File> command = makeHelperCommand(null, CommandTestHelper.ENV);
    checkEnvPassing(command, testEnv, true);
    checkEnvPassing(command, testEnv, false);
  }

  @Test
  public void emptyEnvironmentPassing() throws Exception {
    Map<String, String> env = newHashMap(pair("a", "b"), pair("", "c"));
    Map<String, String> expected = newHashMap(pair("a", "b"));
    Pair<GeneralCommandLine, File> command = makeHelperCommand(null, CommandTestHelper.ENV);
    checkEnvPassing(command, env, expected, false);
  }


  private static String execAndGetOutput(GeneralCommandLine commandLine) throws ExecutionException {
    commandLine.setRedirectErrorStream(true);
    ProcessOutput output = ExecUtil.execAndGetOutput(commandLine);
    String stdout = output.getStdout();
    assertEquals("Command:\n" + commandLine.getCommandLineString() + "\nOutput:\n" + stdout, 0, output.getExitCode());
    return stdout;
  }

  private static Pair<GeneralCommandLine, File> makeHelperCommand(@Nullable File copyTo,
                                                                  @MagicConstant(stringValues = {CommandTestHelper.ARG, CommandTestHelper.ENV}) String mode,
                                                                  String... args) throws IOException, URISyntaxException {
    String className = CommandTestHelper.class.getName();
    URL url = GeneralCommandLine.class.getClassLoader().getResource(className.replace(".", "/") + ".class");
    assertNotNull(url);

    GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath(System.getProperty("java.home") + (SystemInfo.isWindows ? "\\bin\\java.exe" : "/bin/java"));

    String encoding = System.getProperty("file.encoding");
    if (encoding != null) {
      commandLine.addParameter("-D" + "file.encoding=" + encoding);
    }

    commandLine.addParameter("-cp");
    String[] packages = className.split("\\.");
    File classFile = new File(url.toURI());
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

    File out = FileUtil.createTempFile("test.", ".out");

    commandLine.addParameters(mode, CommandTestHelper.OUT, out.getPath());
    commandLine.addParameters(args);
    commandLine.setRedirectErrorStream(true);

    return pair(commandLine, out);
  }

  private static String execHelper(Pair<GeneralCommandLine, File> pair) throws IOException, ExecutionException {
    try {
      ProcessOutput output = ExecUtil.execAndGetOutput(pair.first);
      assertEquals("Command:\n" + pair.first.getCommandLineString() + "\nOutput:\n" + output.getStdout(), 0, output.getExitCode());
      return FileUtil.loadFile(pair.second, CommandTestHelper.ENC);
    }
    finally {
      FileUtil.delete(pair.second);
    }
  }

  private static void checkParamPassing(String output, String... expected) {
    assertEquals(StringUtil.join(expected, "\n") + "\n", StringUtil.convertLineSeparators(output));
  }

  private static void checkEnvPassing(Pair<GeneralCommandLine, File> command,
                                      Map<String, String> testEnv,
                                      boolean passParentEnv) throws ExecutionException, IOException {
    checkEnvPassing(command, testEnv, testEnv, passParentEnv);
  }

  private static void checkEnvPassing(Pair<GeneralCommandLine, File> command,
                                      Map<String, String> testEnv,
                                      Map<String, String> expectedOutputEnv,
                                      boolean passParentEnv) throws ExecutionException, IOException {
    command.first.withEnvironment(testEnv);
    command.first.withParentEnvironmentType(passParentEnv ? ParentEnvironmentType.SYSTEM : ParentEnvironmentType.NONE);
    String output = execHelper(command);

    Set<String> lines = ContainerUtil.newHashSet(StringUtil.convertLineSeparators(output).split("\n"));

    for (Map.Entry<String, String> entry : expectedOutputEnv.entrySet()) {
      String str = CommandTestHelper.format(entry);
      assertTrue("\"" + str + "\" should be in " + lines,
                 lines.contains(str));
    }

    Map<String, String> parentEnv = System.getenv();
    List<String> missed = new ArrayList<>();
    for (Map.Entry<String, String> entry : parentEnv.entrySet()) {
      String str = CommandTestHelper.format(entry);
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
