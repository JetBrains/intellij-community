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
package org.jetbrains.plugins.groovy.console;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.ResolveScopeProvider;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GroovyConsoleResolveScopeProvider extends ResolveScopeProvider {

  @Nullable
  @Override
  public GlobalSearchScope getResolveScope(@NotNull VirtualFile file, Project project) {
    final GroovyConsoleStateService projectConsole = GroovyConsoleStateService.getInstance(project);
    final Module module = projectConsole.getSelectedModule(file);
    return module == null || module.isDisposed() ? null : GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
  }
}
