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
package org.jetbrains.plugins.groovy.mvc;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 * @since 6/19/2014
 */
public class MvcCliCommandExecutor extends MvcCommandExecutor {
  @Override
  protected boolean isApplicable(Module module) {
    return true;
  }

  @Nullable
  @Override
  protected ConsoleProcessDescriptor doRun(@NotNull Module module,
                                           @NotNull MvcFramework framework,
                                           @NotNull MvcCommand mvcCommand,
                                           @Nullable Runnable onDone,
                                           boolean showConsole,
                                           boolean closeOnDone,
                                           String... input) {
    final GeneralCommandLine commandLine = framework.createCommandAndShowErrors(module, mvcCommand);
    if (commandLine == null) return null;
    return MvcConsole.executeProcess(module, commandLine, onDone, closeOnDone, input);
  }
}
