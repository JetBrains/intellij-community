// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.compiler;

import com.intellij.compiler.impl.javaCompiler.BackendCompiler;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.groovy.GreclipseBuilder;
import org.jetbrains.jps.model.java.compiler.CompilerOptions;
import org.jetbrains.plugins.groovy.GroovyFileType;

import java.util.Set;

public final class GreclipseIdeaCompiler implements BackendCompiler {
  private final Project myProject;

  public GreclipseIdeaCompiler(Project project) {
    myProject = project;
  }

  @Override
  public @NotNull String getId() {
    return GreclipseBuilder.ID;
  }

  @Override
  public @NotNull String getPresentableName() {
    return GreclipseBuilder.ID;
  }

  @Override
  public @NotNull Configurable createConfigurable() {
    return new GreclipseConfigurable(GreclipseIdeaCompilerSettings.getSettings(myProject));
  }

  @Override
  public @NotNull Set<FileType> getCompilableFileTypes() {
    return Set.of(JavaFileType.INSTANCE, GroovyFileType.GROOVY_FILE_TYPE);
  }

  @Override
  public @NotNull CompilerOptions getOptions() {
    return GreclipseIdeaCompilerSettings.getSettings(myProject);
  }
}
