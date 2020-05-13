// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.intellij.execution.wsl;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    assertWslCommandOutput("&& exit && exit\n", "echo", "&& exit", "&&", "exit");
    assertWslCommandOutput(".*\n", "echo", ".*");
    assertWslCommandOutput("*\n", "echo", "*");
    assertWslCommandOutput("\\\\\\\"\n", "echo", "\\\\\\\"");
    assertWslCommandOutput("_ \"  ' ) \\\n", "echo", "_", "\"", "", "'", ")", "\\");
    assertWslCommandOutput("' ''' '' '\n", "echo", "'", "'''", "''", "'");
  }

  private void assertWslCommandOutput(@NotNull String expectedOut, String command, String... parameters) {
    final GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath(command);
    commandLine.addParameters(parameters);

    try {
      final GeneralCommandLine cmd = myWSL.patchCommandLine(commandLine, null, null, false);
      final CapturingProcessHandler process = new CapturingProcessHandler(cmd);
      ProcessOutput output = process.runProcess(10_000);

      assertFalse(output.isTimeout());
      assertEquals(0, output.getExitCode());
      assertEquals(expectedOut, output.getStdout());
      assertEquals("", output.getStderr());
    }
    catch (ExecutionException e) {
      fail(e.getMessage());
    }
  }
}