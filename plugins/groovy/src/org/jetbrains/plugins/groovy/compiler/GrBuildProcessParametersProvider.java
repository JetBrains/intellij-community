// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.compiler;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.compiler.impl.javaCompiler.BackendCompiler;
import com.intellij.compiler.server.BuildProcessParametersProvider;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.groovy.compiler.rt.GroovyRtJarPaths;
import org.jetbrains.jps.builders.impl.java.EclipseCompilerTool;
import org.jetbrains.jps.incremental.groovy.GreclipseBuilder;
import org.jetbrains.jps.incremental.groovy.GroovyBuilder;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

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

  @Override
  public @NotNull Iterable<String> getAdditionalPluginPaths() {
    final Path jarPath = PathManager.getJarForClass(GroovyBuilder.class);
    if (jarPath != null) {
      final Supplier<List<String>> roots = lazy(() -> GroovyRtJarPaths.getGroovyRtRoots(jarPath.toFile(), false));
      return () -> roots.get().iterator();
    }
    return Collections.emptyList();
  }

  private static <T> Supplier<T> lazy(Supplier<T> calculation) {
    return new Supplier<>() {
      T cached = null;
      @Override
      public T get() {
        T val = cached;
        if (val == null) {
          cached = val = calculation.get();
        }
        return val;
      }
    };
  }
}