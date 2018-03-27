// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.propertyBased;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.jetCheck.Generator;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class InsertLineComment extends ActionOnRange {

  private final String myToInsert;

  public InsertLineComment(PsiFile file, int startOffset, String toInsert) {
    super(file, startOffset, startOffset);
    myToInsert = toInsert;
  }

  public static Generator<InsertLineComment> insertComment(@NotNull PsiFile psiFile, String validComment) {
    return Generator.integers(0, psiFile.getTextLength())
      .map((offset) -> {
        PsiElement start = psiFile.findElementAt(offset);
        TextRange textRange = start != null ? start.getTextRange() : null;
        return new InsertLineComment(psiFile,
                                     textRange != null ? textRange.getEndOffset() : 0,
                                     validComment);
      })
      .suchThat(Objects::nonNull)
      .noShrink();
  }

  @Override
  public String toString() {
    return "LineComment{" + getVirtualFile().getPath() + " " + getCurrentStartOffset() + " " + myToInsert + "}";
  }

  @Override
  public String getConstructorArguments() {
    return "file, " + myInitialStart + ", " + myToInsert;
  }

  public void performAction() {
    TextRange range = getFinalRange();
    if (range == null) return;

    WriteCommandAction.runWriteCommandAction(getProject(), () -> getDocument().insertString(range.getStartOffset(), myToInsert + "\n"));
  }
}
