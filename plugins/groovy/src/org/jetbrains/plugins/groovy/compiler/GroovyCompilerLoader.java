// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.compiler;

import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;

public class GroovyCompilerLoader implements StartupActivity.DumbAware {

  @Override
  public void runActivity(@NotNull Project project) {
    CompilerManager.getInstance(project).addCompilableFileType(GroovyFileType.GROOVY_FILE_TYPE);
  }
}
