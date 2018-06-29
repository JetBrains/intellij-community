// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.propertyBased;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class InsertLineComment extends ActionOnFile {

  private final String myToInsert;

  public InsertLineComment(PsiFile file, String toInsert) {
    super(file);
    myToInsert = toInsert;
  }

  @Override
  public void performCommand(@NotNull Environment env) {
    PsiDocumentManager.getInstance(getProject()).commitDocument(getDocument());
    
    int randomOffset = generatePsiOffset(env, null);
    PsiElement leaf = getFile().findElementAt(randomOffset);
    TextRange leafRange = leaf != null ? leaf.getTextRange() : null;
    int insertOffset = leafRange != null ? leafRange.getEndOffset() : 0;
    env.logMessage("Inserting '" + StringUtil.escapeStringCharacters(myToInsert) + "' at " + insertOffset);
    WriteCommandAction.runWriteCommandAction(getProject(), () -> getDocument().insertString(insertOffset, myToInsert));
  }

}
