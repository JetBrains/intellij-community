// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.compiler;

import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.components.ProjectComponent;
import org.jetbrains.plugins.groovy.GroovyFileType;

public class GroovyCompilerLoader implements ProjectComponent {
  private final CompilerManager myCompilerManager;

  public GroovyCompilerLoader(CompilerManager manager) {
    myCompilerManager = manager;
  }

  @Override
  public void projectOpened() {
    myCompilerManager.addCompilableFileType(GroovyFileType.GROOVY_FILE_TYPE);
  }
}
