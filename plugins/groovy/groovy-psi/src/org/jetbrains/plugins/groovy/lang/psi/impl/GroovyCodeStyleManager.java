// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;

public abstract class GroovyCodeStyleManager {
  public static GroovyCodeStyleManager getInstance(Project project) {
    return project.getService(GroovyCodeStyleManager.class);
  }

  @NotNull
  public abstract GrImportStatement addImport(@NotNull GroovyFile psiFile, @NotNull GrImportStatement statement) throws IncorrectOperationException;

  public abstract void removeImport(@NotNull GroovyFileBase psiFile, @NotNull GrImportStatement importStatement) throws IncorrectOperationException;
}
