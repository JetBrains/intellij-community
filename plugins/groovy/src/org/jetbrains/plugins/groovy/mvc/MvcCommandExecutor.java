/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 * @since 6/19/2014
 */
public abstract class MvcCommandExecutor {
  private static final ExtensionPointName<MvcCommandExecutor> EP_NAME =
    ExtensionPointName.create("org.intellij.groovy.mvc.command.executor");

  @Nullable
  public static ConsoleProcessDescriptor run(@NotNull Module module,
                                             @NotNull MvcFramework framework,
                                             @NotNull MvcCommand mvcCommand,
                                             @Nullable Runnable onDone,
                                             boolean closeOnDone,
                                             String... input) {
    return run(module, framework, mvcCommand, onDone, true, closeOnDone, input);
  }

  @Nullable
  public static ConsoleProcessDescriptor run(@NotNull Module module,
                                             @NotNull MvcFramework framework,
                                             @NotNull MvcCommand mvcCommand,
                                             @Nullable Runnable onDone,
                                             boolean showConsole,
                                             boolean closeOnDone,
                                             String... input) {
    for (MvcCommandExecutor executor : EP_NAME.getExtensions()) {
      if (executor.isApplicable(module)) {
        return executor.doRun(module, framework, mvcCommand, onDone, showConsole, closeOnDone, input);
      }
    }

    // fallback to default CLI implementation
    return new MvcCliCommandExecutor().doRun(module, framework, mvcCommand, onDone, showConsole, closeOnDone, input);
  }

  protected abstract boolean isApplicable(Module module);

  @Nullable
  protected abstract ConsoleProcessDescriptor doRun(@NotNull Module module,
                                                    @NotNull MvcFramework framework,
                                                    @NotNull MvcCommand mvcCommand,
                                                    @Nullable Runnable onDone,
                                                    boolean showConsole,
                                                    boolean closeOnDone,
                                                    String... input);
}
