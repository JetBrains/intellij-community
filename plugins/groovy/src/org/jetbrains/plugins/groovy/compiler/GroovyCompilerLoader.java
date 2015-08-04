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

import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;

/**
 * @author ilyas
 */
public class GroovyCompilerLoader extends AbstractProjectComponent {
  private final CompilerManager myCompilerManager;

  public GroovyCompilerLoader(Project project, CompilerManager manager) {
    super(project);
    myCompilerManager = manager;
  }

  @Override
  public void projectOpened() {
    myCompilerManager.addCompilableFileType(GroovyFileType.GROOVY_FILE_TYPE);
  }

  @Override
  @NotNull
  public String getComponentName() {
    return "GroovyCompilerLoader";
  }
}
