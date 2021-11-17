// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.containers.ContainerUtil;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public final class WSLCommandEscapingTest extends WslTestBase {
  @Rule
  public final TempDirectory myTempDirectory = new TempDirectory();

  @Test
  public void testEmptyParams() throws Exception {
    assertEchoOutput("");
    assertEchoOutput("", "a");
    assertEchoOutput("a", "", "b");
    assertEchoOutput("", "a", "", "", "b", "");
  }

  @Test
  public void testCommandLineEscaping() throws Exception {
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
    assertEchoOutput("  ");
    assertEchoOutput("_ \"  ' ) \\");
    assertEchoOutput("' ''' '' '");
    assertEchoOutput("$ ]&<>:\"|'(*)[$PATH");
  }

  @Test
  public void testMiscParamEscaping() throws Exception {
    List<String> params = List.of(
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

  private void assertEchoOutput(String... echoParams) throws ExecutionException {
    assertEchoOutput(Arrays.asList(echoParams));
  }

  private void assertEchoOutput(List<String> echoParams) throws ExecutionException {
    assertEchoOutput("/bin/echo", echoParams);
  }

  private void assertEchoOutput(String echoExecutableLinuxPath, List<String> echoParams) throws ExecutionException {
    String expectedOut = String.join(" ", echoParams) + "\n";
    List<String> command = new ArrayList<>(echoParams.size() + 1);
    command.add(echoExecutableLinuxPath);
    command.addAll(echoParams);
    assertWslCommandOutput(expectedOut, null, Collections.emptyMap(), command);

    List<String> execEchoParams = ContainerUtil.filter(echoParams, (param) -> {
      // wsl.exe --exec doesn't support empty parameters: https://github.com/microsoft/WSL/issues/6072
      return !param.isEmpty() && !param.contains("\\");
    });
    if (execEchoParams.size() > 0) {
      List<String> execCommand = new ArrayList<>(execEchoParams.size() + 1);
      execCommand.add(echoExecutableLinuxPath);
      execCommand.addAll(execEchoParams);
      String execExpectedOut = String.join(" ", execEchoParams) + "\n";
      assertWslCommandOutput(execExpectedOut, Collections.emptyMap(), execCommand, new WSLCommandLineOptions().setExecuteCommandInShell(false));
    }
  }

  @Test
  public void testSingleCharacters() throws Exception {
    List<Character> characters = getRepresentativeCharacters();
    List<String> params = new ArrayList<>(characters.size());
    for (Character c : characters) params.add(String.valueOf(c));
    assertEchoOutput(params);
  }

  @Test
  public void testTwoCharsCombinations() throws Exception {
    List<Character> characters = getRepresentativeCharacters();
    List<String> params = new ArrayList<>(characters.size() * characters.size());
    for (Character a : characters) {
      for (Character b : characters) {
        params.add(String.valueOf(a) + b);
      }
    }
    assertEchoOutput(params);
  }

  @Test
  public void testThreeCharsCombinations() throws Exception {
    List<Character> characters = getRepresentativeCharacters();
    int batch = 700;  // Need to limit amount of parameters. Otherwise, it fails with "CreateProcess error=206, The filename or extension is too long".
    List<String> params = new ArrayList<>(batch);
    for (Character a : characters) {
      for (Character b : characters) {
        for (Character c : characters) {
          params.add(String.valueOf(a) + b + c);
          if (params.size() == batch) {
            assertEchoOutput(params);
            params.clear();
          }
        }
      }
    }
    if (!params.isEmpty()) {
      assertEchoOutput(params);
    }
  }

  private static List<Character> getRepresentativeCharacters() {
    List<Character> result = new ArrayList<>();
    Collections.addAll(result, 'A', 'z', '0');
    for (char ch = ' '; ch < 128; ch++) {
      if (!Character.isLetterOrDigit(ch)) {
        result.add(ch);
      }
    }
    return result;
  }

  @Test
  public void testPassingRemoteWorkingDir() throws IOException, ExecutionException {
    assertPwdOutputInDirectory("test");
    assertPwdOutputInDirectory("a b");
    assertPwdOutputInDirectory(" a");
    assertPwdOutputInDirectory("a'");
    assertPwdOutputInDirectory("a&b[");
    assertPwdOutputInDirectory("a&b");
    assertPwdOutputInDirectory("a$b");
  }

  private void assertPwdOutputInDirectory(String directoryName) throws ExecutionException {
    String path = getWsl().getWslPath(myTempDirectory.newDirectory(directoryName).getPath());
    assertWslCommandOutput(path + "\n", path, Collections.emptyMap(), List.of("pwd"));
  }

  @Test
  public void testPassingEnvironment() throws ExecutionException {
    assertEnvOutput(Map.of("A", "B"));
    assertEnvOutput(Map.of("Test", "with space",
                           "Empty", "",
                           "a", "_"));
    assertEnvOutput(Map.of("__aba", " with space",
                           "KEY1", "#\\A",
                           "KEY2", "!VALUE $(",
                           "KEY3", "VA=LUE `",
                           "_KEY_", " ]&<>:\"'|?(*)["));
  }

  private void assertEnvOutput(Map<String, String> envs) throws ExecutionException {
    List<String> command = ContainerUtil.concat(List.of("printenv"), List.copyOf(envs.keySet()));
    String expectedOut = String.join("\n", envs.values()) + "\n";
    assertWslCommandOutput(expectedOut, null, envs, command);
    assertWslCommandOutput(expectedOut, envs, command, new WSLCommandLineOptions().setLaunchWithWslExe(false).setPassEnvVarsUsingInterop(true));
    assertWslCommandOutput(expectedOut, envs, command, new WSLCommandLineOptions().setLaunchWithWslExe(true).setPassEnvVarsUsingInterop(true));
  }

  @Test
  public void testExecutableEscaping() throws Exception {
    List<String> params = Arrays.asList("hello", "\\$(", "&", "`", "'test", "\"test\"");
    assertEchoOutput(createEchoScriptAndGetLinuxPath("my-echo"), params);
    assertEchoOutput(createEchoScriptAndGetLinuxPath("echo (1)"), params);
    assertEchoOutput(createEchoScriptAndGetLinuxPath("echo'`"), params);
  }

  @Test
  public void testReadShellPath() {
    Assert.assertNotEquals("/bin/sh", getWsl().getShellPath());
  }

  private String createEchoScriptAndGetLinuxPath(String executableName) {
    File file = myTempDirectory.newFile(executableName + ".sh", "#!/bin/sh\necho \"$@\"".getBytes(StandardCharsets.UTF_8));
    String wslPath = getWsl().getWslPath(file.getPath());
    assertNotNull("local path: " + file, wslPath);
    return wslPath;
  }

  private void assertWslCommandOutput(String expectedOut, String remoteWorkingDirectory, Map<String, String> envs, List<String> command) throws ExecutionException {
    var wsl = getWsl();
    assertWslCommandOutput(expectedOut, envs, command, new WSLCommandLineOptions().setLaunchWithWslExe(false)
      .setRemoteWorkingDirectory(remoteWorkingDirectory));
    assertWslCommandOutput(expectedOut, envs, command, new WSLCommandLineOptions().setRemoteWorkingDirectory(remoteWorkingDirectory));

    assertWslCommandOutput(expectedOut, envs, command, new WSLCommandLineOptions().setShellPath(wsl.getShellPath())
      .setRemoteWorkingDirectory(remoteWorkingDirectory));

    assertWslCommandOutput(expectedOut, envs, command, new WSLCommandLineOptions().setShellPath(wsl.getShellPath())
      .setExecuteCommandInLoginShell(true).setRemoteWorkingDirectory(remoteWorkingDirectory));

    assertWslCommandOutput(expectedOut, envs, command, new WSLCommandLineOptions().setShellPath(wsl.getShellPath())
      .setExecuteCommandInInteractiveShell(true).setRemoteWorkingDirectory(remoteWorkingDirectory));

    if (remoteWorkingDirectory == null && ContainerUtil.all(command, (param) -> {
      // wsl.exe --exec doesn't support empty parameters: https://github.com/microsoft/WSL/issues/6072
      return !param.isEmpty() && !param.contains("\\");
    })) {
      assertWslCommandOutput(expectedOut, envs, command, new WSLCommandLineOptions().setExecuteCommandInShell(false));
    }
  }

  private void assertWslCommandOutput(String expectedOut, Map<String, String> envs, List<String> command, WSLCommandLineOptions options) throws ExecutionException {
    var wsl = getWsl();
    GeneralCommandLine commandLine = new GeneralCommandLine(command).withEnvironment(envs);
    ProcessOutput output;
    if (options.isExecuteCommandInShell()) {
      output = WslExecution.executeInShellAndGetCommandOnlyStdout(wsl, commandLine, options, 10_000);
    }
    else {
      wsl.patchCommandLine(commandLine, null, options);
      output = new CapturingProcessHandler(commandLine).runProcess(10_000);
    }
    String expected = stringify(false, "", 0, expectedOut);
    String actual = stringify(output.isTimeout(), output.getStderr(), output.getExitCode(), output.getStdout());
    assertEquals(expected, actual);
  }

  private static String stringify(boolean timeout, String stderr, int exitCode, String stdout) {
    return "timeout: " + timeout + "\nstderr: " + stderr + "\nexitCode: " + exitCode + "\nstdout: " + stdout;
  }
}
