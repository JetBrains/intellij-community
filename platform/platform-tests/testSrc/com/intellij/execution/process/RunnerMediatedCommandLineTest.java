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
package com.intellij.execution.process;

import com.intellij.execution.GeneralCommandLineTest;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

import static org.junit.Assume.assumeTrue;

public class RunnerMediatedCommandLineTest extends GeneralCommandLineTest {
  @Override
  protected GeneralCommandLine createCommandLine(String... command) {
    assumeTrue(SystemInfo.isWindows);
    return super.createCommandLine(command);
  }

  @NotNull
  @Override
  protected GeneralCommandLine postProcessCommandLine(@NotNull GeneralCommandLine commandLine) {
    boolean injected = RunnerMediator.injectRunnerCommand(super.postProcessCommandLine(commandLine), false);
    assumeTrue("runner mediator not found", injected);
    return commandLine;
  }

  @Override
  protected void assumeCanTestWindowsShell() {
    assumeTrue(false);
  }

  @Override
  public void printCommandLine() {
    assumeTrue(false);
  }
}
