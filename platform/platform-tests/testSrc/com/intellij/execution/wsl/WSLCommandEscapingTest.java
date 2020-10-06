// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.intellij.execution.wsl;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.util.ArrayUtil;
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

  public void testCommandLineEscaping() {
    assumeWSLAvailable();

    assertWslCommandOutput("\n", "echo");
    assertWslCommandOutput("asd\n", "echo", "asd");
    assertWslCommandOutput("\"test\"\n", "echo", "\"test\"");
    assertWslCommandOutput("'test'\n", "echo", "'test'");
    assertWslCommandOutput("(asd)\n", "echo", "(asd)");
    assertWslCommandOutput("&& exit && exit\n", new WSLCommandLineOptions().setLaunchWithWslExe(false), Collections.emptyMap(),
                           Arrays.asList("echo", "&& exit", "&&", "exit"));
    assertWslCommandOutput(".*\n", "echo", ".*");
    assertWslCommandOutput("*\n", "echo", "*");
    assertWslCommandOutput("\\\\\\\"\n", "echo", "\\\\\\\"");
    assertWslCommandOutput("_ \"  ' ) \\\n", "echo", "_", "\"", "", "'", ")", "\\");
    assertWslCommandOutput("' ''' '' '\n", "echo", "'", "'''", "''", "'");
  }

  public void testPassingRemoteWorkingDir() throws IOException {
    assertPwdOutputInDirectory("test");
    assertPwdOutputInDirectory("a b");
    assertPwdOutputInDirectory("a ");
    assertPwdOutputInDirectory(" a");
    assertPwdOutputInDirectory("a'");
    //assertPwdOutputInDirectory("a&b");
  }

  public void testPassingEnvironment() {
    assertEnvOutput(ContainerUtil.newLinkedHashMap(Pair.create("A", "B")));
    assertEnvOutput(ContainerUtil.newLinkedHashMap(Pair.create("Test", "with space")));
    assertEnvOutput(ContainerUtil.newLinkedHashMap(Pair.create("__aba", " with space"),
                                                   Pair.create("KEY1", "VALUE2 "),
                                                   Pair.create("KEY2", "VALUE 2"),
                                                   Pair.create("_KEY_", "| & *")
    ));
  }

  private void assertEnvOutput(@NotNull LinkedHashMap<String, String> envs) {
    assertNotEmpty(envs.keySet());
    List<String> command = ContainerUtil.concat(Collections.singletonList("printenv"), new ArrayList<>(envs.keySet()));
    assertWslCommandOutput(StringUtil.join(envs.values(), "\n") + "\n", null, envs, ArrayUtil.toStringArray(command));
  }

  private void assertPwdOutputInDirectory(@NotNull String directoryName) throws IOException {
    File dir = FileUtil.createTempDirectory(directoryName, null);
    try {
      String path = myWSL.getWslPath(dir.getAbsolutePath());
      assertWslCommandOutput(path + "\n", path, Collections.emptyMap(), "pwd");
    }
    finally {
      FileUtil.delete(dir);
    }
  }

  private void assertWslCommandOutput(@NotNull String expectedOut, @NotNull String @NotNull... command) {
    assertWslCommandOutput(expectedOut, null, Collections.emptyMap(), command);
  }

  private void assertWslCommandOutput(@NotNull String expectedOut,
                                      @Nullable String remoteWorkingDirectory,
                                      @NotNull Map<String, String> envs,
                                      @NotNull String @NotNull ... command) {
    assertWslCommandOutput(expectedOut, new WSLCommandLineOptions().setLaunchWithWslExe(false).setRemoteWorkingDirectory(remoteWorkingDirectory),
                           envs, Arrays.asList(command));
    assertWslCommandOutput(expectedOut, new WSLCommandLineOptions().setLaunchWithWslExe(true).setRemoteWorkingDirectory(remoteWorkingDirectory),
                           envs, Arrays.asList(command));
    String bashParameters = StringUtil.join(command, " ");
    assertWslCommandOutput(expectedOut, new WSLCommandLineOptions().setLaunchWithWslExe(false).setRemoteWorkingDirectory(remoteWorkingDirectory),
                           envs, Arrays.asList("bash", "-c", bashParameters));
    assertWslCommandOutput(expectedOut, new WSLCommandLineOptions().setLaunchWithWslExe(true).setRemoteWorkingDirectory(remoteWorkingDirectory),
                           envs, Arrays.asList("bash", "-c", bashParameters));
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

      assertFalse(output.isTimeout());
      if (!output.getStderr().isEmpty()) {
        System.out.println();
      }
      assertEquals("", output.getStderr());
      assertEquals(0, output.getExitCode());
      assertEquals(expectedOut, output.getStdout());
    }
    catch (ExecutionException e) {
      fail(e.getMessage());
    }
  }
}
