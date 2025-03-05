// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.propertyBased;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jetCheck.Generator;

public abstract class ActionOnFile implements MadTestingAction {
  private final Project myProject;
  private final VirtualFile myVirtualFile;

  protected ActionOnFile(@NotNull PsiFile file) {
    myProject = file.getProject();
    myVirtualFile = file.getVirtualFile();
  }

  protected @NotNull PsiFile getFile() {
    return PsiManager.getInstance(getProject()).findFile(getVirtualFile());
  }

  protected @NotNull Project getProject() {
    return myProject;
  }

  protected @NotNull VirtualFile getVirtualFile() {
    return myVirtualFile;
  }

  protected @NotNull Document getDocument() {
    return FileDocumentManager.getInstance().getDocument(getVirtualFile());
  }

  @SuppressWarnings("SameParameterValue")
  protected int generatePsiOffset(@NotNull Environment env, @Nullable String logMessage) {
    return env.generateValue(Generator.integers(0, getFile().getTextLength()).noShrink(), logMessage);
  }

  protected int generateDocOffset(@NotNull Environment env, @Nullable String logMessage) {
    return env.generateValue(Generator.integers(0, getDocument().getTextLength()).noShrink(), logMessage);
  }
    
}
