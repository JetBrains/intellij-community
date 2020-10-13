// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.psiutils;

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;

/**
 * @author Bas Leijdekkers
 */
public class JavaCommentUtil {

  public static boolean isEndOfLineComment(PsiElement element) {
    if (!(element instanceof PsiComment)) {
      return false;
    }
    final PsiComment comment = (PsiComment)element;
    final IElementType tokenType = comment.getTokenType();
    return JavaTokenType.END_OF_LINE_COMMENT.equals(tokenType);
  }
}
