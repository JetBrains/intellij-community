// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl;

import com.intellij.execution.CommandLineUtil;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.junit.Assume.assumeTrue;

public class WSLCommandEscapingTest extends HeavyPlatformTestCase {

  @Nullable
  private WSLDistribution myWSL;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myWSL = ContainerUtil.getFirstItem(WSLUtil.getAvailableDistributions());
  }

  private void assumeWSLAvailable() {
    assumeTrue("WSL unavailable", myWSL != null);
  }

  public void testEmptyParams() {
    assumeWSLAvailable();

    assertEchoOutput("");
    assertEchoOutput("", "a");
    assertEchoOutput("a", "", "b");
    assertEchoOutput("", "a", "", "", "b", "");
  }

  public void testCommandLineEscaping() {
    assumeWSLAvailable();

    assertEchoOutput();
    assertEchoOutput("asd");
    assertEchoOutput("\"test\"");
    assertEchoOutput("'test'");
    assertEchoOutput("(asd)");
    assertEchoOutput("& &");
    assertEchoOutput("&& exit && exit");
    assertEchoOutput("a&b[");
    assertEchoOutput(".*");
    assertEchoOutput("*");
    assertEchoOutput("\\\\\\\"");
    assertEchoOutput("_ \"  ' ) \\");
    assertEchoOutput("' ''' '' '");
    assertEchoOutput("$ ]&<>:\"|'(*)[$PATH");
  }

  public void testMiscParamEscaping() {
    assumeWSLAvailable();

    List<String> params = ContainerUtil.newArrayList(
      " &",
      " \\A&",
      " `",
      "##\\#",
      "#&\\",
      "#&\\&",
      "#&\\A",
      "#&\\A\\",
      "#&\\A\\\\",
      "#A&",
      "#A& \\A",
      "#A&A",
      "#A&~",
      "#A&~A",
      "#A&~\\A",
      "#AA",
      "#A\\",
      "#\\",
      "#\\ ",
      "#\\#",
      "#\\&",
      "#\\A",
      "#\\A#",
      "#\\\\",
      "#\\\\\\A",
      "&A\\",
      "&\\ ",
      "&\\A",
      "&\\\\",
      "A#\\A",
      "A\\$",
      "\\#",
      "\\#\\",
      "\\$",
      "\\$(",
      "\\$A",
      "\\$\\",
      "\\$\\$",
      "\\&",
      "\\&\\",
      "\\A",
      "\\A#",
      "\\A&",
      "\\A& ",
      "\\A\\",
      "\\\\",
      "\\\\&A\\",
      "\\\\&\\A",
      "\\\\&\\A~A",
      "\\\\A",
      "\\\\A\\",
      "\\`",
      "~\\#",
      "~\\`",
      "~\\`HELLO"
    );
    assertEchoOutput(params);
  }

  private void assertEchoOutput(@NotNull String @NotNull... echoParams) {
    assertEchoOutput(Arrays.asList(echoParams));
  }

  private void assertEchoOutput(@NotNull List<String> echoParams) {
    assertEchoOutput("/bin/echo", echoParams);
  }

  private void assertEchoOutput(@NotNull String echoExecutableLinuxPath, @NotNull List<String> echoParams) {
    String expectedOut = StringUtil.join(echoParams, " ") + "\n";
    List<String> command = ContainerUtil.concat(Collections.singletonList(echoExecutableLinuxPath), echoParams);
    assertWslCommandOutput(expectedOut, (String)null, Collections.emptyMap(), command);
  }

  public void testSingleCharacters() {
    assumeWSLAvailable();

    assertEchoOutput(ContainerUtil.map(getRepresentativeCharacters(), String::valueOf));
  }

  public void testTwoCharsCombinations() {
    assumeWSLAvailable();

    List<String> params = new ArrayList<>();
    getRepresentativeCharacters().forEach((a) -> {
      getRepresentativeCharacters().forEach((b) -> {
        params.add(String.valueOf(a) + b);
      });
    });
    assertEchoOutput(params);
  }

  public void testThreeCharsCombinations() {
    assumeWSLAvailable();

    List<String> params = new ArrayList<>();
    getRepresentativeCharacters().forEach((a) -> {
      getRepresentativeCharacters().forEach((b) -> {
        getRepresentativeCharacters().forEach((c) -> {
          params.add(String.valueOf(a) + b + c);
        });
      });
    });
    // Need to limit amount of parameters. Otherwise, it fails with "CreateProcess error=206, The filename or extension is too long".
    int batch = 700;
    for (int i = 0; i < (params.size() + batch - 1) / batch; i++) {
      assertEchoOutput(params.subList(i * batch, Math.min(batch * (i + 1), params.size())));
    }
  }

  private static @NotNull List<Character> getRepresentativeCharacters() {
    List<Character> result = ContainerUtil.newArrayList('A', 'z', '0');
    for (char ch = ' '; ch < 128; ch++) {
      if (!Character.isLetterOrDigit(ch)) {
        result.add(ch);
      }
    }
    return result;
  }

  public void testPassingRemoteWorkingDir() throws IOException {
    assumeWSLAvailable();

    assertPwdOutputInDirectory("test");
    assertPwdOutputInDirectory("a b");
    assertPwdOutputInDirectory("a ");
    assertPwdOutputInDirectory(" a");
    assertPwdOutputInDirectory("a'");
    assertPwdOutputInDirectory("a&b[");
    assertPwdOutputInDirectory("a&b");
    assertPwdOutputInDirectory("a$b");
  }

  public void testPassingEnvironment() {
    assumeWSLAvailable();

    assertEnvOutput(ContainerUtil.newLinkedHashMap(Pair.create("A", "B")));
    assertEnvOutput(ContainerUtil.newLinkedHashMap(Pair.create("Test", "with space"), Pair.create("Empty", ""), Pair.create("a", "_")));
    assertEnvOutput(ContainerUtil.newLinkedHashMap(Pair.create("__aba", " with space"),
                                                   Pair.create("KEY1", "#\\A"),
                                                   Pair.create("KEY2", "!VALUE $("),
                                                   Pair.create("KEY2", "VA=LUE `"),
                                                   Pair.create("_KEY_", " ]&<>:\"'|?(*)[")
    ));
  }

  private void assertEnvOutput(@NotNull LinkedHashMap<String, String> envs) {
    assertNotEmpty(envs.keySet());
    List<String> command = ContainerUtil.concat(Collections.singletonList("printenv"), new ArrayList<>(envs.keySet()));
    String expectedOut = StringUtil.join(envs.values(), "\n") + "\n";
    assertWslCommandOutput(expectedOut, (String)null, envs, command);
    assertWslCommandOutput(expectedOut,
                           new WSLCommandLineOptions().setLaunchWithWslExe(false).setPassEnvVarsUsingInterop(true),
                           envs, command);
    assertWslCommandOutput(expectedOut,
                           new WSLCommandLineOptions().setLaunchWithWslExe(true).setPassEnvVarsUsingInterop(true),
                           envs, command);
  }

  private void assertPwdOutputInDirectory(@NotNull String directoryName) throws IOException {
    File dir = FileUtil.createTempDirectory(directoryName, null);
    try {
      String path = myWSL.getWslPath(dir.getAbsolutePath());
      assertWslCommandOutput(path + "\n", path, Collections.emptyMap(), Collections.singletonList("pwd"));
    }
    finally {
      FileUtil.delete(dir);
    }
  }

  public void testExecutableEscaping() throws IOException {
    assumeWSLAvailable();

    List<String> params = Arrays.asList("hello", "\\$(", "&", "`", "'test", "\"test\"");
    assertEchoOutput(createEchoScriptAndGetLinuxPath("my-echo"), params);
    assertEchoOutput(createEchoScriptAndGetLinuxPath("echo (1)"), params);
    assertEchoOutput(createEchoScriptAndGetLinuxPath("echo'`"), params);
  }

  private @NotNull String createEchoScriptAndGetLinuxPath(@NotNull String executableName) throws IOException {
    File file = FileUtil.createTempFile(executableName, ".sh", true);
    FileUtil.writeToFile(file, "#!/bin/sh\necho \"$@\"");
    return Objects.requireNonNull(myWSL.getWslPath(file.getAbsolutePath()));
  }

  private void assertWslCommandOutput(@NotNull String expectedOut,
                                      @Nullable String remoteWorkingDirectory,
                                      @NotNull Map<String, String> envs,
                                      @NotNull List<String> command) {
    String bashParameters = StringUtil.join(ContainerUtil.map(command, (c) -> {
      return c.isEmpty() ? "''" : CommandLineUtil.posixQuote(c);
    }), " ");
    assertWslCommandOutput(expectedOut,
                           new WSLCommandLineOptions().setLaunchWithWslExe(false).setRemoteWorkingDirectory(remoteWorkingDirectory),
                           envs, Arrays.asList("bash", "-c", bashParameters));
    assertWslCommandOutput(expectedOut,
                           new WSLCommandLineOptions().setLaunchWithWslExe(true).setRemoteWorkingDirectory(remoteWorkingDirectory),
                           envs, Arrays.asList("bash", "-c", bashParameters));

    assertWslCommandOutput(expectedOut,
                           new WSLCommandLineOptions().setLaunchWithWslExe(false).setRemoteWorkingDirectory(remoteWorkingDirectory),
                           envs, command);
    assertWslCommandOutput(expectedOut,
                           new WSLCommandLineOptions().setLaunchWithWslExe(true).setRemoteWorkingDirectory(remoteWorkingDirectory),
                           envs, command);
    if (!ContainerUtil.exists(command, String::isEmpty) && remoteWorkingDirectory == null) {
      // wsl.exe --exec doesn't support empty parameters: https://github.com/microsoft/WSL/issues/6072
      assertWslCommandOutput(expectedOut, new WSLCommandLineOptions().setLaunchWithWslExe(true).setExecuteCommandInShell(false),
                             envs, command);
    }
  }

  private void assertWslCommandOutput(@NotNull String expectedOut,
                                      @NotNull WSLCommandLineOptions options,
                                      @NotNull Map<String, String> envs,
                                      @NotNull List<String> command) {
    assertNotEmpty(command);
    GeneralCommandLine commandLine = new GeneralCommandLine(command);
    commandLine.getEnvironment().putAll(envs);

    try {
      final GeneralCommandLine cmd = myWSL.patchCommandLine(commandLine, null, options);
      final CapturingProcessHandler process = new CapturingProcessHandler(cmd);
      ProcessOutput output = process.runProcess(10_000);

      String expected = stringify(false, "", 0, expectedOut);
      String actual = stringify(output.isTimeout(), output.getStderr(), output.getExitCode(), output.getStdout());
      assertEquals(expected, actual);
    }
    catch (ExecutionException e) {
      fail(e.getMessage());
    }
  }

  private static @NotNull String stringify(boolean timeout, @NotNull String stderr, int exitCode, @NotNull String stdout) {
    return StringUtil.join(ContainerUtil.newArrayList(
      "timeout: " + timeout,
      "stderr: " + stderr,
      "exitCode: " + exitCode,
      "stdout: " + stdout
    ), "\n");
  }
}
