/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.remoteServer.impl.runtime.log;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.io.OutputStream;

public class ConsoleTerminalProvider extends CloudTerminalProvider {

  @Override
  public TerminalHandlerBase createTerminal(@NotNull String presentableName,
                                            @NotNull Project project,
                                            @NotNull InputStream terminalOutput,
                                            @NotNull OutputStream terminalInput) {
    return new ConsoleTerminalHandlerImpl(presentableName, project, terminalOutput, terminalInput);
  }

  @Override
  public boolean isTtySupported() {
    return false;
  }
}
