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
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public abstract class FilePsiMutation extends ActionOnFile {

  public FilePsiMutation(PsiFile file) {
    super(file);
  }

  @Override
  public void performCommand(@NotNull Environment env) {
    env.logMessage(toString());
    PsiDocumentManager.getInstance(getProject()).commitDocument(getFile().getViewProvider().getDocument());
    WriteCommandAction.runWriteCommandAction(getProject(), this::performMutation);
    PsiTestUtil.checkPsiStructureWithCommit(getFile(), PsiTestUtil::checkStubsMatchText);
  }

  protected abstract void performMutation();

}
