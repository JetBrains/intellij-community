/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.console;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey Evdokimov
 */
public abstract class GroovyShellRunner {

  private static final ExtensionPointName<GroovyShellRunner> EP_NAME = ExtensionPointName.create("org.intellij.groovy.groovyShellRunner");

  @NotNull
  public abstract String getWorkingDirectory(@NotNull Module module);

  @NotNull
  public abstract JavaParameters createJavaParameters(@NotNull Module module) throws ExecutionException;

  public abstract boolean canRun(@NotNull Module module);

  @NotNull
  public String getTitle(@NotNull Module module) {
    return "";
  }

  @Nullable
  public PsiElement getContext(@NotNull Module module) {
    return null;
  }

  @NotNull
  public String transformUserInput(@NotNull String userInput) {
    return userInput;
  }

  @Nullable
  public static GroovyShellRunner getAppropriateRunner(@NotNull Module module) {
    for (GroovyShellRunner runner : EP_NAME.getExtensions()) {
      if (runner.canRun(module)) {
        return runner;
      }
    }

    return null;
  }

}
