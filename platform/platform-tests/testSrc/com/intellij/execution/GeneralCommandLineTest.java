// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.GeneralCommandLine.ParentEnvironmentType;
import com.intellij.execution.process.*;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.containers.ContainerUtil;
import org.assertj.core.api.Assertions;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import static com.intellij.openapi.util.Pair.pair;
import static org.junit.Assert.*;
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
    "\\<\"\\>\\",
    "\\<\"\\>",
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

  @Rule public TempDirectory tempDir = new TempDirectory();

  protected GeneralCommandLine createCommandLine(String... command) {
    return new GeneralCommandLine(command);
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
    // on Unix, JRE uses "sun.jnu.encoding" for paths and "file.encoding" for forking; they should be the same for the test to pass
    // on Windows, Unicode-aware functions are used both for paths and child process creation
    String uni = IoTestUtil.getUnicodeName();
    assumeTrue(uni != null);
    assumeTrue(SystemInfo.isWindows || Objects.equals(System.getProperty("sun.jnu.encoding"), System.getProperty("file.encoding")));

    String mark = String.valueOf(new Random().nextInt());
    String command = SystemInfo.isWindows ? "@echo " + mark + '\n' : "#!/bin/sh\necho " + mark + '\n';
    File script = ExecUtil.createTempExecutableScript("spaces 'and quotes' and " + uni + " ", ".cmd", command);
    try {
      String output = execAndGetOutput(createCommandLine(script.getPath()));
      assertEquals(mark + '\n', StringUtil.convertLineSeparators(output));
    }
    finally {
      FileUtil.delete(script);
    }
  }

  @Test(timeout = 60000)
  public void unicodeClassPath() throws Exception {
    // in addition to the above ...
    // ... on Unix, JRE decodes arguments using "sun.jnu.encoding"
    // ... on Windows, JRE receives arguments in ANSI code page and decodes using "sun.jnu.encoding"
    String uni = SystemInfo.isWindows ? IoTestUtil.getUnicodeName(System.getProperty("sun.jnu.encoding")) : IoTestUtil.getUnicodeName();
    assumeTrue(uni != null);
    assumeTrue(SystemInfo.isWindows || Objects.equals(System.getProperty("sun.jnu.encoding"), System.getProperty("file.encoding")));

    File dir = tempDir.newDirectory("spaces 'and quotes' and " + uni);
    Pair<GeneralCommandLine, File> command = makeHelperCommand(dir, CommandTestHelper.ARG, "test");
    String output = execHelper(command);
    assertEquals("test\n", StringUtil.convertLineSeparators(output));
  }

  @Test(timeout = 60000)
  public void passingArgumentsToJavaApp() throws Exception {
    Pair<GeneralCommandLine, File> command = makeHelperCommand(null, CommandTestHelper.ARG, ARGUMENTS);
    String output = execHelper(command);
    checkParamPassing(output, ARGUMENTS);
  }

  @Test(timeout = 60000)
  public void passingArgumentsToJavaAppThroughWinShell() throws Exception {
    IoTestUtil.assumeWindows();

    Pair<GeneralCommandLine, File> command = makeHelperCommand(null, CommandTestHelper.ARG, ARGUMENTS);
    String javaPath = command.first.getExePath();
    command.first.setExePath(ExecUtil.getWindowsShellName());
    command.first.getParametersList().prependAll("/D", "/C", "call", javaPath);
    String output = execHelper(command);
    checkParamPassing(output, ARGUMENTS);
  }

  @Test(timeout = 60000)
  public void passingArgumentsToJavaAppThroughNestedWinShell() throws Exception {
    IoTestUtil.assumeWindows();

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
    IoTestUtil.assumeWindows();

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
    IoTestUtil.assumeWindows();

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
    IoTestUtil.assumeWindows();

    for (String argument : ARGUMENTS) {
      if (argument.trim().isEmpty()) continue;  // would report "ECHO is on"
      GeneralCommandLine commandLine = createCommandLine(ExecUtil.getWindowsShellName(), "/D", "/C", "echo", argument);
      String output = execAndGetOutput(commandLine);
      assertEquals(commandLine.getPreparedCommandLine(), filterExpectedOutput(argument) + "\n", output);
    }
  }

  @Test(timeout = 60000)
  public void passingArgumentsToCygwinPrintf() throws Exception {
    IoTestUtil.assumeWindows();

    File cygwinPrintf = FileUtil.findFirstThatExist("C:\\cygwin\\bin\\printf.exe", "C:\\cygwin64\\bin\\printf.exe");
    assumeTrue("Cygwin not found", cygwinPrintf != null);

    for (String argument : ARGUMENTS) {
      GeneralCommandLine commandLine = createCommandLine(cygwinPrintf.getPath(), "[%s]\\\\n", argument);
      String output = execAndGetOutput(commandLine);
      assertEquals(commandLine.getPreparedCommandLine(), filterExpectedOutput("[" + argument + "]") + "\n", output);
    }
  }

  @Test(timeout = 60000)
  public void unicodeParameters() throws Exception {
    // on Unix, JRE uses "sun.jnu.encoding" for paths and "file.encoding" for forking; they should be the same for the test to pass
    // on Windows, JRE receives arguments in ANSI variant and decodes using "sun.jnu.encoding"
    String uni = SystemInfo.isWindows ? IoTestUtil.getUnicodeName(System.getProperty("sun.jnu.encoding")) : IoTestUtil.getUnicodeName();
    assumeTrue(uni != null);
    assumeTrue(SystemInfo.isWindows || Objects.equals(System.getProperty("sun.jnu.encoding"), System.getProperty("file.encoding")));

    String[] args = {"some", uni, "parameters"};
    Pair<GeneralCommandLine, File> command = makeHelperCommand(null, CommandTestHelper.ARG, args);
    String output = execHelper(command);
    checkParamPassing(output, args);
  }

  @Test(timeout = 60000)
  public void winShellCommand() {
    IoTestUtil.assumeWindows();

    String string = "http://localhost/wtf?a=b&c=d";
    String echo = ExecUtil.execAndReadLine(createCommandLine(ExecUtil.getWindowsShellName(), "/c", "echo", string));
    assertEquals(string, echo);
  }

  @Test(timeout = 60000)
  public void winShellScriptQuoting() throws Exception {
    IoTestUtil.assumeWindows();

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
    IoTestUtil.assumeWindows();

    String param = "a&b";
    GeneralCommandLine commandLine = createCommandLine(ExecUtil.getWindowsShellName(), "/D", "/C", "echo", param);
    String output = execAndGetOutput(commandLine);
    assertEquals(param, output.trim());
  }

  @Test(timeout = 60000)
  public void redirectInput() throws Exception {
    String content = "Line 1\nLine 2\n";
    File input = tempDir.newFile("input");
    FileUtil.writeToFile(input, content);
    String command = SystemInfo.isWindows ? "@echo off\nfindstr \"^\"\n" : "#!/bin/sh\ncat\n";
    File script = ExecUtil.createTempExecutableScript("print-stdin", ".cmd", command);
    try {
      GeneralCommandLine commandLine = createCommandLine(script.getPath()).withInput(input);
      String output = execAndGetOutput(commandLine);
      assertEquals(content, StringUtil.convertLineSeparators(output));
    }
    finally {
      FileUtil.delete(script);
    }
  }

  @Test(timeout = 60000)
  public void hackyEnvMap() throws Exception {
    //noinspection ConstantConditions
    createCommandLine().getEnvironment().putAll(null);

    checkEnvVar("", "-", "empty keys should be rejected");
    checkEnvVar("a\0b", "-", "keys with '\\0' should be rejected");
    checkEnvVar("a=b", "-", "keys with '=' should be rejected");
    if (SystemInfo.isWindows) {
      GeneralCommandLine commandLine = createCommandLine("find");
      commandLine.getEnvironment().put("=wtf", "-");
      commandLine.createProcess().waitFor();
    }
    else {
      checkEnvVar("=wtf", "-", "keys with '=' should be rejected");
    }

    checkEnvVar("key1", null, "null values should be rejected");
    checkEnvVar("key1", "a\0b", "values with '\\0' should be rejected");
  }

  private void checkEnvVar(String name, String value, String message) throws ExecutionException, InterruptedException {
    GeneralCommandLine commandLine = createCommandLine(SystemInfo.isWindows ? "find" : "echo");
    commandLine.getEnvironment().put(name, value);
    try {
      commandLine.createProcess().waitFor();
      fail(message);
    }
    catch (IllegalEnvVarException ignored) { }
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
    // on Unix, JRE uses "file.encoding" to encode and decode environment; on Windows, JRE uses wide characters
    String uni = SystemInfo.isWindows ? IoTestUtil.getUnicodeName() : IoTestUtil.getUnicodeName(System.getProperty("file.encoding"));
    assumeTrue(uni != null);

    Map<String, String> testEnv = Map.of("VALUE_1", uni + "_1", "VALUE_2", uni + "_2");
    Pair<GeneralCommandLine, File> command = makeHelperCommand(null, CommandTestHelper.ENV);
    checkEnvPassing(command, testEnv, true);
    checkEnvPassing(command, testEnv, false);
  }

  @Test
  public void deleteTempFile() throws Exception {
    File temp = tempDir.newFile("temp");
    FileUtil.writeToFile(temp, "something");
    assertTrue(temp.exists());
    GeneralCommandLine cmd = SystemInfo.isWindows ? new GeneralCommandLine("cmd", "/c", "ver") : new GeneralCommandLine("uname");
    OSProcessHandler.deleteFileOnTermination(cmd, temp);
    execAndGetOutput(cmd);
    assertFalse(temp.exists());
  }

  @Test
  public void deleteTempFileWhenProcessCreationFails() throws Exception {
    File temp = tempDir.newFile("temp");
    FileUtil.writeToFile(temp, "something");
    Assertions.assertThat(temp).exists();
    GeneralCommandLine cmd = new GeneralCommandLine("there_should_not_be_such_command");
    OSProcessHandler.deleteFileOnTermination(cmd, temp);
    try {
      ExecUtil.execAndGetOutput(cmd);
      throw new AssertionError("Process creation should fail");
    }
    catch (ProcessNotCreatedException ignored) { }
    Assertions.assertThat(temp).doesNotExist();
  }

  @Test
  public void shortCommandLookup() throws Exception {
    String mark = String.valueOf(new Random().nextInt());
    String command = SystemInfo.isWindows ? "@echo " + mark + '\n' : "#!/bin/sh\necho " + mark + '\n';
    File script = tempDir.newFile("test_dir/script.cmd", command.getBytes(StandardCharsets.UTF_8));
    NioFiles.setExecutable(script.toPath());
    String output = execAndGetOutput(createCommandLine(script.getName()).withEnvironment(Map.of("PATH", script.getParent())));
    assertEquals(mark + '\n', StringUtil.convertLineSeparators(output));
  }

  private @NotNull String execAndGetOutput(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
    ProcessHandler processHandler = createProcessHandler(commandLine);
    CapturingProcessRunner runner = new CapturingProcessRunner(processHandler);
    ProcessOutput output = runner.runProcess();
    int ec = output.getExitCode();
    if (ec != 0 || !output.getStderr().isEmpty()) {
      fail("Command:\n" + commandLine.getPreparedCommandLine() +
           "\nStdOut:\n" + output.getStdout() +
           "\nStdErr:\n" + output.getStderr());
    }
    return output.getStdout();
  }

  protected @NotNull ProcessHandler createProcessHandler(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
    return new OSProcessHandler(commandLine);
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
      Files.createDirectories(dir.toPath());
      Files.copy(classFile.toPath(), dir.toPath().resolve(classFile.getName()));
      commandLine.addParameter(copyTo.getPath());
    }

    commandLine.addParameter(className);

    File out = tempDir.newFile("test_output");

    commandLine.addParameters(mode, CommandTestHelper.OUT, out.getPath());
    commandLine.addParameters(args);

    return pair(commandLine, out);
  }

  private String execHelper(Pair<GeneralCommandLine, File> pair) throws IOException, ExecutionException {
    execAndGetOutput(pair.first);
    return FileUtil.loadFile(pair.second, CommandTestHelper.ENC);
  }

  private static void checkParamPassing(String output, String... expected) {
    assertEquals(StringUtil.join(expected, "\n") + "\n", StringUtil.convertLineSeparators(output));
  }

  private void checkEnvPassing(Pair<GeneralCommandLine, File> command,
                               Map<String, String> testEnv,
                               boolean passParentEnv) throws ExecutionException, IOException {
    command.first.withEnvironment(testEnv);
    command.first.withParentEnvironmentType(passParentEnv ? ParentEnvironmentType.SYSTEM : ParentEnvironmentType.NONE);
    String output = execHelper(command);

    Set<String> lines = ContainerUtil.newHashSet(StringUtil.convertLineSeparators(output).split("\n"));

    for (Map.Entry<String, String> entry : testEnv.entrySet()) {
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
