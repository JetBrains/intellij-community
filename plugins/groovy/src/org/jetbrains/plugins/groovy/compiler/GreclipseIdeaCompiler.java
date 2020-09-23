// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

/**
 * @author peter
 */
public final class GreclipseIdeaCompiler implements BackendCompiler {
  private final Project myProject;

  public GreclipseIdeaCompiler(Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public String getId() {
    return GreclipseBuilder.ID;
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return GreclipseBuilder.ID;
  }

  @NotNull
  @Override
  public Configurable createConfigurable() {
    return new GreclipseConfigurable(GreclipseIdeaCompilerSettings.getSettings(myProject));
  }

  @NotNull
  @Override
  public Set<FileType> getCompilableFileTypes() {
    return Set.of(JavaFileType.INSTANCE, GroovyFileType.GROOVY_FILE_TYPE);
  }

  @NotNull
  @Override
  public CompilerOptions getOptions() {
    return GreclipseIdeaCompilerSettings.getSettings(myProject);
  }
}
