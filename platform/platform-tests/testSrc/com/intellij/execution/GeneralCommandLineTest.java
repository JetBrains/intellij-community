// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.GeneralCommandLine.ParentEnvironmentType;
import com.intellij.execution.process.CapturingProcessRunner;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessNotCreatedException;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.lang.JavaVersion;
import org.assertj.core.api.Assertions;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

@SuppressWarnings("SystemGetProperty")
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
    var commandLine = createCommandLine();
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
    var uni = IoTestUtil.getUnicodeName();
    assumeTrue(uni != null);
    assumeTrue(SystemInfo.isWindows || Objects.equals(System.getProperty("sun.jnu.encoding"), System.getProperty("file.encoding")));

    var mark = String.valueOf(new Random().nextInt());
    var command = SystemInfo.isWindows ? "@echo " + mark + '\n' : "#!/bin/sh\necho " + mark + '\n';
    var script = ExecUtil.createTempExecutableScript("spaces 'and quotes' and " + uni + " ", ".cmd", command).toPath();
    try {
      var output = execAndGetOutput(createCommandLine(script.toString()));
      assertEquals(mark + '\n', StringUtil.convertLineSeparators(output));
    }
    finally {
      Files.delete(script);
    }
  }

  @Test(timeout = 60000)
  public void unicodeClassPath() throws Exception {
    // in addition to the above ...
    // ... on Unix, JRE decodes arguments using "sun.jnu.encoding"
    // ... on Windows, JRE receives arguments in ANSI code page and decodes using "sun.jnu.encoding"
    var uni = SystemInfo.isWindows ? IoTestUtil.getUnicodeName(System.getProperty("sun.jnu.encoding")) : IoTestUtil.getUnicodeName();
    assumeTrue(uni != null);
    assumeTrue(SystemInfo.isWindows || Objects.equals(System.getProperty("sun.jnu.encoding"), System.getProperty("file.encoding")));

    var dir = tempDir.newDirectoryPath("spaces 'and quotes' and " + uni);
    var output = execHelper(makeHelperCommand(dir, CommandTestHelper.ARG, "test"));
    assertEquals("test\n", StringUtil.convertLineSeparators(output));
  }

  @Test(timeout = 60000)
  public void passingArgumentsToJavaApp() throws Exception {
    var output = execHelper(makeHelperCommand(null, CommandTestHelper.ARG, ARGUMENTS));
    checkParamPassing(output, ARGUMENTS);
  }

  @Test(timeout = 60000)
  public void passingArgumentsToJavaAppThroughWinShell() throws Exception {
    IoTestUtil.assumeWindows();

    var command = makeHelperCommand(null, CommandTestHelper.ARG, ARGUMENTS);
    var javaPath = command.first.getExePath();
    command.first.setExePath(CommandLineUtil.getWinShellName());
    command.first.getParametersList().prependAll("/D", "/C", "call", javaPath);
    var output = execHelper(command);
    checkParamPassing(output, ARGUMENTS);
  }

  @Test(timeout = 60000)
  public void passingArgumentsToJavaAppThroughNestedWinShell() throws Exception {
    IoTestUtil.assumeWindows();

    var command = makeHelperCommand(null, CommandTestHelper.ARG, ARGUMENTS);
    var javaPath = command.first.getExePath();
    command.first.setExePath(CommandLineUtil.getWinShellName());
    command.first.getParametersList().prependAll("/D", "/C", "call",
                                                 CommandLineUtil.getWinShellName(), "/D", "/C", "call",
                                                 CommandLineUtil.getWinShellName(), "/D", "/C", "@call",
                                                 javaPath);
    var output = execHelper(command);
    checkParamPassing(output, ARGUMENTS);
  }

  @Test(timeout = 60000)
  public void passingArgumentsToJavaAppThroughCmdScriptAndWinShell() throws Exception {
    IoTestUtil.assumeWindows();

    var command = makeHelperCommand(null, CommandTestHelper.ARG);
    var script = ExecUtil.createTempExecutableScript("my script ", ".cmd", "@" + command.first.getCommandLineString() + " %*").toPath();
    try {
      var commandLine = createCommandLine(CommandLineUtil.getWinShellName(), "/D", "/C", "call", script.toString());
      commandLine.addParameters(ARGUMENTS);
      var output = execHelper(new Pair<>(commandLine, command.second));
      checkParamPassing(output, ARGUMENTS);
    }
    finally {
      Files.delete(script);
    }
  }

  @Test(timeout = 60000)
  public void passingArgumentsToJavaAppThroughCmdScriptAndNestedWinShell() throws Exception {
    IoTestUtil.assumeWindows();

    var command = makeHelperCommand(null, CommandTestHelper.ARG);
    var script = ExecUtil.createTempExecutableScript("my script ", ".cmd", "@" + command.first.getCommandLineString() + " %*").toPath();
    try {
      var commandLine = createCommandLine(CommandLineUtil.getWinShellName(), "/D", "/C", "call",
                                          CommandLineUtil.getWinShellName(), "/D", "/C", "@call",
                                          CommandLineUtil.getWinShellName(), "/D", "/C", "call",
                                          script.toString());
      commandLine.addParameters(ARGUMENTS);
      var output = execHelper(new Pair<>(commandLine, command.second));
      checkParamPassing(output, ARGUMENTS);
    }
    finally {
      Files.delete(script);
    }
  }

  @Test(timeout = 60000)
  public void passingArgumentsToEchoThroughWinShell() throws Exception {
    IoTestUtil.assumeWindows();

    for (var argument : ARGUMENTS) {
      if (argument.trim().isEmpty()) continue;  // causes "ECHO is on"
      var commandLine = createCommandLine(CommandLineUtil.getWinShellName(), "/D", "/C", "echo", argument);
      var output = execAndGetOutput(commandLine);
      assertEquals(commandLine.getPreparedCommandLine(), filterExpectedOutput(argument) + "\n", output);
    }
  }

  @Test(timeout = 60000)
  public void passingArgumentsToCygwinPrintf() throws Exception {
    IoTestUtil.assumeWindows();

    var cygwinPrintf = FileUtil.findFirstThatExist("C:\\cygwin\\bin\\printf.exe", "C:\\cygwin64\\bin\\printf.exe").toPath();
    assumeTrue("Cygwin not found", cygwinPrintf != null);

    for (var argument : ARGUMENTS) {
      var commandLine = createCommandLine(cygwinPrintf.toString(), "[%s]\\\\n", argument);
      var output = execAndGetOutput(commandLine);
      assertEquals(commandLine.getPreparedCommandLine(), filterExpectedOutput("[" + argument + "]") + "\n", output);
    }
  }

  @Test(timeout = 60000)
  public void unicodeParameters() throws Exception {
    // on Unix, JRE uses "sun.jnu.encoding" for paths and "file.encoding" for forking; they should be the same for the test to pass
    // on Windows, JRE receives arguments in ANSI variant and decodes using "sun.jnu.encoding"
    var uni = SystemInfo.isWindows ? IoTestUtil.getUnicodeName(System.getProperty("sun.jnu.encoding")) : IoTestUtil.getUnicodeName();
    assumeTrue(uni != null);
    assumeTrue(SystemInfo.isWindows || Objects.equals(System.getProperty("sun.jnu.encoding"), System.getProperty("file.encoding")));

    var args = new String[]{"some", uni, "parameters"};
    var output = execHelper(makeHelperCommand(null, CommandTestHelper.ARG, args));
    checkParamPassing(output, args);
  }

  @Test(timeout = 60000)
  public void winShellCommand() {
    IoTestUtil.assumeWindows();

    var string = "http://localhost/wtf?a=b&c=d";
    var echo = ExecUtil.execAndReadLine(createCommandLine(CommandLineUtil.getWinShellName(), "/c", "echo", string));
    assertEquals(string, echo);
  }

  @Test(timeout = 60000)
  public void winShellScriptQuoting() throws Exception {
    IoTestUtil.assumeWindows();

    var scriptPrefix = "my_script";
    for (String scriptExt : new String[]{".cmd", ".bat"}) {
      var script = ExecUtil.createTempExecutableScript(scriptPrefix, scriptExt, "@echo %*\n").toPath();
      try {
        for (var argument : ARGUMENTS) {
          var commandLine = createCommandLine(script.toString(), GeneralCommandLine.inescapableQuote(argument));
          var output = execAndGetOutput(commandLine);
          assertEquals(commandLine.getPreparedCommandLine(), filterExpectedOutput(StringUtil.wrapWithDoubleQuote(argument)), output.trim());
        }
      }
      finally {
        Files.delete(script);
      }
    }
  }

  @Test(timeout = 60000)
  public void winShellQuotingWithExtraSwitch() throws Exception {
    IoTestUtil.assumeWindows();

    var param = "a&b";
    var output = execAndGetOutput(createCommandLine(CommandLineUtil.getWinShellName(), "/D", "/C", "echo", param));
    assertEquals(param, output.trim());
  }

  @Test(timeout = 60000)
  public void redirectInput() throws Exception {
    var content = "Line 1\nLine 2\n";
    var input = Files.writeString(tempDir.newFile("input").toPath(), content);
    var command = SystemInfo.isWindows ? "@echo off\nfindstr \"^\"\n" : "#!/bin/sh\ncat\n";
    var script = ExecUtil.createTempExecutableScript("print-stdin", ".cmd", command).toPath();
    try {
      var output = execAndGetOutput(createCommandLine(script.toString()).withInput(input.toFile()));
      assertEquals(content, StringUtil.convertLineSeparators(output));
    }
    finally {
      Files.delete(script);
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
      var commandLine = createCommandLine("find");
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
    var commandLine = createCommandLine(SystemInfo.isWindows ? "find" : "echo");
    commandLine.getEnvironment().put(name, value);
    try {
      commandLine.createProcess().waitFor();
      fail(message);
    }
    catch (IllegalEnvVarException ignored) { }
  }

  @Test(timeout = 60000)
  public void environmentPassing() throws Exception {
    var testEnv = new HashMap<String, String>();
    testEnv.put("VALUE_1", "some value");
    testEnv.put("VALUE_2", "another\n\"value\"");

    var command = makeHelperCommand(null, CommandTestHelper.ENV);
    checkEnvPassing(command, testEnv, true);
    checkEnvPassing(command, testEnv, false);
  }

  @Test(timeout = 60000)
  public void unicodeEnvironment() throws Exception {
    // on Unix, JRE uses "file.encoding" ("sun.jnu.encoding" in 18+) to encode and decode environment; on Windows, JRE uses wide characters
    var uni = SystemInfo.isWindows ? IoTestUtil.getUnicodeName() :
              JavaVersion.current().isAtLeast(18) ? IoTestUtil.getUnicodeName(System.getProperty("sun.jnu.encoding")) :
              IoTestUtil.getUnicodeName(System.getProperty("file.encoding"));
    assumeTrue(uni != null);

    var testEnv = Map.of("VALUE_1", uni + "_1", "VALUE_2", uni + "_2");
    var command = makeHelperCommand(null, CommandTestHelper.ENV);
    checkEnvPassing(command, testEnv, true);
    checkEnvPassing(command, testEnv, false);
  }

  @Test
  public void deleteTempFile() throws Exception {
    var temp = Files.writeString(tempDir.newFile("temp").toPath(), "something");
    Assertions.assertThat(temp).exists();
    var cmd = SystemInfo.isWindows ? new GeneralCommandLine("cmd", "/c", "ver") : new GeneralCommandLine("uname");
    OSProcessHandler.deleteFileOnTermination(cmd, temp.toFile());
    execAndGetOutput(cmd);
    Assertions.assertThat(temp).doesNotExist();
  }

  @Test
  public void deleteTempFileWhenProcessCreationFails() throws Exception {
    var temp = Files.writeString(tempDir.newFile("temp").toPath(), "something");
    Assertions.assertThat(temp).exists();
    var cmd = new GeneralCommandLine("there_should_not_be_such_command");
    OSProcessHandler.deleteFileOnTermination(cmd, temp.toFile());
    try {
      ExecUtil.execAndGetOutput(cmd);
      throw new AssertionError("Process creation should fail");
    }
    catch (ProcessNotCreatedException ignored) { }
    Assertions.assertThat(temp).doesNotExist();
  }

  @Test
  public void shortCommandLookup() throws Exception {
    var mark = String.valueOf(new Random().nextInt());
    var command = SystemInfo.isWindows ? "@echo " + mark + '\n' : "#!/bin/sh\necho " + mark + '\n';
    var script = tempDir.newFile("test_dir/script.cmd", command.getBytes(StandardCharsets.UTF_8)).toPath();
    NioFiles.setExecutable(script);
    var output = execAndGetOutput(createCommandLine(script.getFileName().toString()).withEnvironment(Map.of("PATH", script.getParent().toString())));
    assertEquals(mark + '\n', StringUtil.convertLineSeparators(output));
  }

  private String execAndGetOutput(GeneralCommandLine commandLine) throws ExecutionException {
    var output = new CapturingProcessRunner(createProcessHandler(commandLine)).runProcess();
    var ec = output.getExitCode();
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

  private Pair<GeneralCommandLine, Path> makeHelperCommand(@Nullable Path copyTo,
                                                           @MagicConstant(stringValues = {CommandTestHelper.ARG, CommandTestHelper.ENV}) String mode,
                                                           String... args) throws IOException {
    var mainClass = CommandTestHelper.class;
    var className = mainClass.getName();
    var commandLine = createCommandLine(PlatformTestUtil.getJavaExe());

    var encoding = System.getProperty("file.encoding");
    if (encoding != null) commandLine.addParameter("-D" + "file.encoding=" + encoding);

    var lang = System.getenv("LANG");
    if (lang != null) commandLine.withEnvironment("LANG", lang);

    commandLine.addParameter("-cp");
    if (copyTo == null) {
      var jar = PathManager.getJarPathForClass(mainClass);
      assertNotNull(jar);
      commandLine.addParameter(jar);
    }
    else {
      String resourceName = className.replace(".", "/") + ".class";
      Path outFile = copyTo.resolve(resourceName);
      Files.createDirectories(outFile.getParent());
      try (var inputStream = GeneralCommandLine.class.getClassLoader().getResourceAsStream(resourceName)) {
        assertNotNull(inputStream);
        Files.copy(inputStream, outFile);
      }
      commandLine.addParameter(copyTo.toString());
    }
    commandLine.addParameter(className);

    var out = tempDir.newFile("test_output").toPath();
    commandLine.addParameters(mode, CommandTestHelper.OUT, out.toString());
    commandLine.addParameters(args);

    return new Pair<>(commandLine, out);
  }

  private String execHelper(Pair<GeneralCommandLine, Path> pair) throws IOException, ExecutionException {
    execAndGetOutput(pair.first);
    return Files.readString(pair.second);
  }

  private static void checkParamPassing(String output, String... expected) {
    assertEquals(StringUtil.join(expected, "\n") + "\n", StringUtil.convertLineSeparators(output));
  }

  private void checkEnvPassing(Pair<GeneralCommandLine, Path> command,
                               Map<String, String> testEnv,
                               boolean passParentEnv) throws ExecutionException, IOException {
    command.first.withEnvironment(testEnv);
    command.first.withParentEnvironmentType(passParentEnv ? ParentEnvironmentType.SYSTEM : ParentEnvironmentType.NONE);
    var output = execHelper(command);

    var lines = new LinkedHashSet<>(List.of(StringUtil.convertLineSeparators(output).split("\n")));

    for (var entry : testEnv.entrySet()) {
      Assertions.assertThat(lines).contains(CommandTestHelper.format(entry));
    }

    var parentEnv = System.getenv();
    var missed = new ArrayList<>();
    for (var entry : parentEnv.entrySet()) {
      var str = CommandTestHelper.format(entry);
      if (!lines.contains(str)) {
        missed.add(str);
      }
    }

    var pctMissed = Math.round((100.0 * missed.size()) / parentEnv.size());
    if (passParentEnv && pctMissed > 25 || !passParentEnv && pctMissed < 75) {
      fail("% missed: " + pctMissed + ", missed: " + missed + ", passed: " + lines);
    }
  }
}
