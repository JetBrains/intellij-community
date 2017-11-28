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

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.testFramework.PsiTestUtil;

/**
 * @author peter
 */
public abstract class FilePsiMutation implements MadTestingAction {
  private final SmartPsiElementPointer<PsiFile> myFile;

  public FilePsiMutation(PsiFile file) {
    myFile = SmartPointerManager.getInstance(file.getProject()).createSmartPsiElementPointer(file);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "[" + myFile.getVirtualFile().getPath() + "]";
  }

  @Override
  public void performAction() {
    PsiDocumentManager.getInstance(myFile.getProject()).commitDocument(getFile().getViewProvider().getDocument());
    WriteCommandAction.runWriteCommandAction(myFile.getProject(), this::performMutation);
    PsiTestUtil.checkPsiStructureWithCommit(getFile(), PsiTestUtil::checkStubsMatchText);
  }

  protected abstract void performMutation();

  public PsiFile getFile() {
    return myFile.getElement();
  }
}
