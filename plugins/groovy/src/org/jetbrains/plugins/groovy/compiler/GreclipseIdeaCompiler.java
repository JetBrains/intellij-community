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
package org.jetbrains.plugins.groovy.compiler;

import com.intellij.compiler.impl.javaCompiler.BackendCompiler;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.groovy.GreclipseBuilder;
import org.jetbrains.plugins.groovy.GroovyFileType;

import java.util.Set;

/**
 * @author peter
 */
public class GreclipseIdeaCompiler implements BackendCompiler {
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
    return ContainerUtil.newTroveSet(StdFileTypes.JAVA, (FileType)GroovyFileType.GROOVY_FILE_TYPE);
  }
}
