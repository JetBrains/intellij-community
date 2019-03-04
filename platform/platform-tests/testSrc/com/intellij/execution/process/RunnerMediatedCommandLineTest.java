// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process;

import com.intellij.execution.GeneralCommandLineTest;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assume.assumeTrue;

public class RunnerMediatedCommandLineTest extends GeneralCommandLineTest {
  @Before
  public void ensureRightOS() {
    assumeTrue("Windows-oly test", SystemInfo.isWindows);
  }

  @NotNull
  @Override
  protected GeneralCommandLine postProcessCommandLine(@NotNull GeneralCommandLine commandLine) {
    boolean injected = RunnerMediator.injectRunnerCommand(super.postProcessCommandLine(commandLine), false);
    assumeTrue("runner mediator not found", injected);
    return commandLine;
  }

  @Ignore @Test @Override
  public void passingArgumentsToJavaAppThroughWinShell() { }

  @Ignore @Test @Override
  public void passingArgumentsToJavaAppThroughNestedWinShell() { }

  @Ignore @Test @Override
  public void passingArgumentsToJavaAppThroughCmdScriptAndWinShell() { }

  @Ignore @Test @Override
  public void passingArgumentsToJavaAppThroughCmdScriptAndNestedWinShell() { }

  @Ignore @Test @Override
  public void passingArgumentsToEchoThroughWinShell() { }

  @Ignore @Test @Override
  public void winShellCommand() { }

  @Ignore @Test @Override
  public void winShellScriptQuoting() { }

  @Ignore @Test @Override
  public void winShellQuotingWithExtraSwitch() { }
}