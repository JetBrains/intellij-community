// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.console;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.ResolveScopeProvider;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.jetbrains.plugins.groovy.bundled.BundledGroovy.createBundledGroovyScope;
import static org.jetbrains.plugins.groovy.console.GroovyConsoleUtil.hasGroovyAll;

public class GroovyConsoleResolveScopeProvider extends ResolveScopeProvider {

  @Nullable
  @Override
  public GlobalSearchScope getResolveScope(@NotNull VirtualFile file, Project project) {
    final GroovyConsoleStateService projectConsole = GroovyConsoleStateService.getInstance(project);

    final Module module = projectConsole.getSelectedModule(file);
    if (module == null || module.isDisposed()) return null;

    GlobalSearchScope moduleScope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
    if (hasGroovyAll(module)) return moduleScope;

    GlobalSearchScope bundledScope = createBundledGroovyScope(project);
    return bundledScope != null ? moduleScope.uniteWith(bundledScope) : moduleScope;
  }
}
