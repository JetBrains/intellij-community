/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

/**
 * @author peter
 */
public abstract class ActionOnFile implements MadTestingAction {
  private final Project myProject;
  private final VirtualFile myVirtualFile;

  protected ActionOnFile(PsiFile file) {
    myProject = file.getProject();
    myVirtualFile = file.getVirtualFile();
  }

  @NotNull
  protected PsiFile getFile() {
    return PsiManager.getInstance(getProject()).findFile(getVirtualFile());
  }

  @NotNull
  protected Project getProject() {
    return myProject;
  }

  @NotNull
  protected VirtualFile getVirtualFile() {
    return myVirtualFile;
  }

  @NotNull
  protected Document getDocument() {
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
