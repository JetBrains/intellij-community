/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
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
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

public class GeneralCommandLineTest {
  public static final String[] ARGUMENTS = {
    "with space",
    "  leading and trailing  spaces  ",
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
    "\\backslash",
    "trailing slash\\",
    "two trailing slashes\\\\",
    "trailing-slash\\",
    "two-trailing-slashes\\\\",
    "\"quoted slash\\\"",
    "\"quoted two slashes\\\\\"",
    "\"quoted-slash\\\"",
    "\"quoted-two-slashes\\\\\"",
    "some\ttab",
    "new\nline",
    "\nnew\nline\n",
    "^% % %%% %\"%%",
    //"%PATH%",
    "^",
    "\\^",
    "^ ^^",
    "specials \\  &  |  >  <  ^",
    "carets: ^ ^^ ^^^ ^^^^",
    "caret escape ^\\  ^&  ^|  ^>  ^<  ^^",
    "caret escape2 ^^\\  ^^&  ^^|  ^^>  ^^<  ^^^",
    "&<>()@^|",
    "\"^\"",
    "\"^\"^\"",
    "\"^\"\"^^\"^^^",
    "\"^&<>(\")@^|\"",
    " < \" > ",
    " \" ^ \" ",
    " \" ^ \" ^ \" ",
    " \" ^ \" \" ^ ^\" ^^^ ",
    " \" ^ &< >( \" ) @ ^ | \" ",
    " < \" > ",
    "*",
    "\\*",
    "\"*\"",
    "*.*",
    "?",
    "???",
    "??????",
    "????????",    // testData
    "??????????",
    "????????????",
    "??????????????",
    "????????????????",
    "??????????????????",    // platform-tests.iml
    "\\?",
    "\"?\"",
    "*.???", // ^ the Xmas tree above is to catch at least one file matching those globs
    "stash@{1}",
    "{}",
    "{1}",
    "{1,2}{a,b}",
    "test[Src][Src][Src]",
    "[t]estData",
    "~",
    "'~'",
    "'single-quoted'",
    "''",
    "C:\\cygwin*",
    "C:\\cygwin{,64}",
    "C:\\cygwin{,64}\\printf.e[x]e",
    "\\\\dos\\path\\{1,2}{a,b}",
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

  protected GeneralCommandLine createCommandLine(String... command) {
    return new GeneralCommandLine(command);
  }

  @NotNull
  protected GeneralCommandLine postProcessCommandLine(@NotNull GeneralCommandLine commandLine) {
    return commandLine;
  }

  protected void assumeCanTestWindowsShell() {
    assumeTrue("Windows-only test", SystemInfo.isWindows);
  }

  @NotNull
  protected String filterExpectedOutput(@NotNull String output) {
    return output;
  }

  @Test(timeout = 60000)
  public void printCommandLine() {
    GeneralCommandLine commandLine = createCommandLine();
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
                 " \"trailing slash\"\\",
                 commandLine.getCommandLineString());
  }

  @Test(timeout = 60000)
  public void unicodePath() throws Exception {
    String mark = String.valueOf(new Random().nextInt());
    File script = createTempScript("spaces 'and quotes' and " + UNICODE_RU + "_" + UNICODE_EU + " ",
                                   "@echo " + mark + "\n",
                                   "#!/bin/sh\n" + "echo " + mark + "\n");

    try {
      String output = execAndGetOutput(createCommandLine(script.getPath()));
      assertEquals(mark + "\n", StringUtil.convertLineSeparators(output));
    }
    finally {
      FileUtil.delete(script);
    }
  }

  @NotNull
  private static File createTempScript(@NotNull String scriptNamePrefix,
                                       @NotNull String winScriptContent,
                                       @NotNull String unixScriptContent) throws IOException, ExecutionException {
    if (SystemInfo.isWindows) {
      return ExecUtil.createTempExecutableScript(scriptNamePrefix, ".cmd", winScriptContent);
    }
    return ExecUtil.createTempExecutableScript(scriptNamePrefix, ".sh", unixScriptContent);
  }

  @Test(timeout = 60000)
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

  @Test(timeout = 60000)
  public void passingArgumentsToJavaApp() throws Exception {
    Pair<GeneralCommandLine, File> command = makeHelperCommand(null, CommandTestHelper.ARG, ARGUMENTS);
    String output = execHelper(command);
    checkParamPassing(output, ARGUMENTS);
  }

  @Test(timeout = 60000)
  public void passingArgumentsToJavaAppThroughWinShell() throws Exception {
    assumeCanTestWindowsShell();

    Pair<GeneralCommandLine, File> command = makeHelperCommand(null, CommandTestHelper.ARG, ARGUMENTS);
    String javaPath = command.first.getExePath();
    command.first.setExePath(ExecUtil.getWindowsShellName());
    command.first.getParametersList().prependAll("/D", "/C", "call", javaPath);
    String output = execHelper(command);
    checkParamPassing(output, ARGUMENTS);
  }

  @Test(timeout = 60000)
  public void passingArgumentsToJavaAppThroughNestedWinShell() throws Exception {
    assumeCanTestWindowsShell();

    Pair<GeneralCommandLine, File> command = makeHelperCommand(null, CommandTestHelper.ARG, ARGUMENTS);
    String javaPath = command.first.getExePath();
    command.first.setExePath(ExecUtil.getWindowsShellName());
    command.first.getParametersList().prependAll("/D", "/C", "call",
                                                 ExecUtil.getWindowsShellName(), "/D", "/C", "call",
                                                 ExecUtil.getWindowsShellName(), "/D", "/C", "@call",
                                                 javaPath);
    String output = execHelper(command);
    checkParamPassing(output, ARGUMENTS);
  }

  @Test(timeout = 60000)
  public void passingArgumentsToJavaAppThroughCmdScriptAndWinShell() throws Exception {
    assumeCanTestWindowsShell();

    Pair<GeneralCommandLine, File> command = makeHelperCommand(null, CommandTestHelper.ARG);
    File script = ExecUtil.createTempExecutableScript("my script ", ".cmd", "@" + command.first.getCommandLineString() + " %*");
    try {
      GeneralCommandLine commandLine = createCommandLine(ExecUtil.getWindowsShellName(), "/D", "/C", "call", script.getAbsolutePath());
      commandLine.addParameters(ARGUMENTS);
      String output = execHelper(pair(commandLine, command.second));
      checkParamPassing(output, ARGUMENTS);
    }
    finally {
      FileUtil.delete(script);
    }
  }

  @Test(timeout = 60000)
  public void passingArgumentsToJavaAppThroughCmdScriptAndNestedWinShell() throws Exception {
    assumeCanTestWindowsShell();

    Pair<GeneralCommandLine, File> command = makeHelperCommand(null, CommandTestHelper.ARG);
    File script = ExecUtil.createTempExecutableScript("my script ", ".cmd", "@" + command.first.getCommandLineString() + " %*");
    try {
      GeneralCommandLine commandLine = createCommandLine(ExecUtil.getWindowsShellName(), "/D", "/C", "call",
                                                         ExecUtil.getWindowsShellName(), "/D", "/C", "@call",
                                                         ExecUtil.getWindowsShellName(), "/D", "/C", "call",
                                                         script.getAbsolutePath());
      commandLine.addParameters(ARGUMENTS);
      String output = execHelper(pair(commandLine, command.second));
      checkParamPassing(output, ARGUMENTS);
    }
    finally {
      FileUtil.delete(script);
    }
  }

  @Test(timeout = 60000)
  public void passingArgumentsToEchoThroughWinShell() throws Exception {
    assumeCanTestWindowsShell();

    for (String argument : ARGUMENTS) {
      if (argument.trim().isEmpty()) continue;  // would report "ECHO is on"
      GeneralCommandLine commandLine = createCommandLine(ExecUtil.getWindowsShellName(), "/D", "/C", "echo", argument);
      String output = execAndGetOutput(commandLine);
      assertEquals(commandLine.getPreparedCommandLine(), filterExpectedOutput(argument) + "\n", output);
    }
  }

  @Test(timeout = 60000)
  public void passingArgumentsToCygwinPrintf() throws Exception {
    assumeTrue(SystemInfo.isWindows);
    File cygwinPrintf = FileUtil.findFirstThatExist("C:\\cygwin\\bin\\printf.exe",
                                                    "C:\\cygwin64\\bin\\printf.exe");
    assumeNotNull(cygwinPrintf);

    for (String argument : ARGUMENTS) {
      GeneralCommandLine commandLine = createCommandLine(cygwinPrintf.getPath(), "[%s]\\\\n", argument);
      String output = execAndGetOutput(commandLine);
      assertEquals(commandLine.getPreparedCommandLine(), filterExpectedOutput("[" + argument + "]") + "\n", output);
    }
  }

  @Test(timeout = 60000)
  public void unicodeParameters() throws Exception {
    assumeTrue(UNICODE != null);

    String[] args = {"some", UNICODE, "parameters"};
    Pair<GeneralCommandLine, File> command = makeHelperCommand(null, CommandTestHelper.ARG, args);
    String output = execHelper(command);
    checkParamPassing(output, args);
  }

  @Test(timeout = 60000)
  public void winShellCommand() {
    assumeCanTestWindowsShell();

    String string = "http://localhost/wtf?a=b&c=d";
    String echo = ExecUtil.execAndReadLine(createCommandLine(ExecUtil.getWindowsShellName(), "/c", "echo", string));
    assertEquals(string, echo);
  }

  @Test(timeout = 60000)
  public void winShellScriptQuoting() throws Exception {
    assumeCanTestWindowsShell();

    String scriptPrefix = "my_script";
    for (String scriptExt : new String[]{".cmd", ".bat"}) {
      File script = ExecUtil.createTempExecutableScript(scriptPrefix, scriptExt, "@echo %*\n");
      try {
        for (String argument : ARGUMENTS) {
          GeneralCommandLine commandLine = createCommandLine(script.getAbsolutePath(), GeneralCommandLine.inescapableQuote(argument));
          String output = execAndGetOutput(commandLine);
          assertEquals(commandLine.getPreparedCommandLine(), filterExpectedOutput(StringUtil.wrapWithDoubleQuote(argument)), output.trim());
        }
      }
      finally {
        FileUtil.delete(script);
      }
    }
  }

  @Test(timeout = 60000)
  public void winShellQuotingWithExtraSwitch() throws Exception {
    assumeCanTestWindowsShell();

    String param = "a&b";
    GeneralCommandLine commandLine = createCommandLine(ExecUtil.getWindowsShellName(), "/D", "/C", "echo", param);
    String output = execAndGetOutput(commandLine);
    assertEquals(param, output.trim());
  }

  @Test(timeout = 60000)
  public void redirectInput() throws Exception {
    String content = "Line 1\nLine 2\n";
    File input = FileUtil.createTempFile("input", null);
    FileUtil.writeToFile(input, content);
    File script = createTempScript("print-stdin",
                                   "@echo off\n" + "findstr \"^\"\n",
                                   "#!/bin/sh\n" + "cat\n");
    try {
      GeneralCommandLine commandLine = createCommandLine(script.getPath()).withInput(input);
      String output = execAndGetOutput(commandLine);
      assertEquals(content, StringUtil.convertLineSeparators(output));
    }
    finally {
      FileUtil.delete(script);
      FileUtil.delete(input);
    }
  }

  @Test(timeout = 60000)
  public void hackyEnvMap() {
    Map<String, String> env = createCommandLine().getEnvironment();

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

  @Test(timeout = 60000)
  public void environmentPassing() throws Exception {
    Map<String, String> testEnv = new HashMap<>();
    testEnv.put("VALUE_1", "some value");
    testEnv.put("VALUE_2", "another\n\"value\"");

    Pair<GeneralCommandLine, File> command = makeHelperCommand(null, CommandTestHelper.ENV);
    checkEnvPassing(command, testEnv, true);
    checkEnvPassing(command, testEnv, false);
  }

  @Test(timeout = 60000)
  public void unicodeEnvironment() throws Exception {
    assumeTrue("UTF-8".equals(System.getProperty("file.encoding")));

    Map<String, String> testEnv = newHashMap(pair("VALUE_1", UNICODE_RU), pair("VALUE_2", UNICODE_EU));
    Pair<GeneralCommandLine, File> command = makeHelperCommand(null, CommandTestHelper.ENV);
    checkEnvPassing(command, testEnv, true);
    checkEnvPassing(command, testEnv, false);
  }

  @Test(timeout = 60000)
  public void emptyEnvironmentPassing() throws Exception {
    Map<String, String> env = newHashMap(pair("a", "b"), pair("", "c"));
    Map<String, String> expected = newHashMap(pair("a", "b"));
    Pair<GeneralCommandLine, File> command = makeHelperCommand(null, CommandTestHelper.ENV);
    checkEnvPassing(command, env, expected, false);
  }

  @NotNull
  private String execAndGetOutput(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
    ProcessOutput output = ExecUtil.execAndGetOutput(postProcessCommandLine(commandLine));
    int ec = output.getExitCode();
    if (ec != 0 || !output.getStderr().isEmpty()) {
      fail("Command:\n" + commandLine.getPreparedCommandLine() +
           "\nStdOut:\n" + output.getStdout() +
           "\nStdErr:\n" + output.getStderr());
    }
    return output.getStdout();
  }

  private Pair<GeneralCommandLine, File> makeHelperCommand(@Nullable File copyTo,
                                                           @MagicConstant(stringValues = {CommandTestHelper.ARG, CommandTestHelper.ENV}) String mode,
                                                           String... args) throws IOException, URISyntaxException {
    String className = CommandTestHelper.class.getName();
    URL url = GeneralCommandLine.class.getClassLoader().getResource(className.replace(".", "/") + ".class");
    assertNotNull(url);

    GeneralCommandLine commandLine = createCommandLine();
    commandLine.setExePath(PlatformTestUtil.getJavaExe());

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

    return pair(commandLine, out);
  }

  private String execHelper(Pair<GeneralCommandLine, File> pair) throws IOException, ExecutionException {
    try {
      execAndGetOutput(pair.first);
      return FileUtil.loadFile(pair.second, CommandTestHelper.ENC);
    }
    finally {
      FileUtil.delete(pair.second);
    }
  }

  private static void checkParamPassing(String output, String... expected) {
    assertEquals(StringUtil.join(expected, "\n") + "\n", StringUtil.convertLineSeparators(output));
  }

  private void checkEnvPassing(Pair<GeneralCommandLine, File> command,
                               Map<String, String> testEnv,
                               boolean passParentEnv) throws ExecutionException, IOException {
    checkEnvPassing(command, testEnv, testEnv, passParentEnv);
  }

  private void checkEnvPassing(Pair<GeneralCommandLine, File> command,
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
