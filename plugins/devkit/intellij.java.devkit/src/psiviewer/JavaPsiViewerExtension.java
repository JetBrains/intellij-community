// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.devkit.psiviewer;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElementFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.psiviewer.PsiViewerExtension;

/**
 * @author Konstantin Bulenkov
 */
public abstract class JavaPsiViewerExtension implements PsiViewerExtension{
  @Override
  @NotNull
  public FileType getDefaultFileType() {
    return JavaFileType.INSTANCE;
  }

  protected static PsiElementFactory getFactory(Project project) {
    return JavaPsiFacade.getElementFactory(project);
  }
}
