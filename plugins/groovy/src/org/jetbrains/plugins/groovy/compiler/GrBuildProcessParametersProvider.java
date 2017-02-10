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

public class GrBuildProcessParametersProvider extends BuildProcessParametersProvider {
  private final Project myProject;

  public GrBuildProcessParametersProvider(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public List<String> getClassPath() {
    CompilerConfiguration config = CompilerConfiguration.getInstance(myProject);
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