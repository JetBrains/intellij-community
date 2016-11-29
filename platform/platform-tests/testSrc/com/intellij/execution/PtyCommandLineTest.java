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
import com.intellij.execution.configurations.PtyCommandLine;
import com.intellij.openapi.util.SystemInfo;

import static org.junit.Assume.assumeFalse;

public class PtyCommandLineTest extends GeneralCommandLineTest {
  @Override
  protected GeneralCommandLine createCommandLine(String... command) {
    PtyCommandLine cmd = new PtyCommandLine();

    if (command.length > 0) {
      cmd.setExePath(command[0]);

      for (int i = 1; i < command.length; i++) {
        cmd.addParameter(command[i]);
      }
    }

    return cmd;
  }
}