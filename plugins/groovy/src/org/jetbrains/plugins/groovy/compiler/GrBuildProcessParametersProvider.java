// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.compiler;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.compiler.impl.javaCompiler.BackendCompiler;
import com.intellij.compiler.server.BuildProcessParametersProvider;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.impl.java.EclipseCompilerTool;
import org.jetbrains.jps.incremental.groovy.GreclipseBuilder;

import java.io.File;
import java.util.Collections;
import java.util.List;

final class GrBuildProcessParametersProvider extends BuildProcessParametersProvider {
  private final Project myProject;

  GrBuildProcessParametersProvider(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public List<String> getClassPath() {
    CompilerConfiguration config = myProject.isDefault() ? null : CompilerConfiguration.getInstance(myProject);
    if (config instanceof CompilerConfigurationImpl) {
      BackendCompiler backend = ((CompilerConfigurationImpl)config).getDefaultCompiler();
      if (backend != null && backend.getId() == GreclipseBuilder.ID) {
        File file = EclipseCompilerTool.findEcjJarFile();
        if (file != null) {
          return Collections.singletonList(file.getAbsolutePath());
        }
      }
    }

    return Collections.emptyList();
  }
}