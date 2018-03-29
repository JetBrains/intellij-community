// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.completion.smartEnter.fixers;


import com.intellij.lang.SmartEnterProcessorWithFixers;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.completion.smartEnter.GroovySmartEnterProcessor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCommandArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

public class GrMethodCallFixer extends SmartEnterProcessorWithFixers.Fixer<GroovySmartEnterProcessor> {
  @Override
  public void apply(@NotNull Editor editor, @NotNull GroovySmartEnterProcessor processor, @NotNull PsiElement psiElement) {
    final GrArgumentList argList = psiElement instanceof GrCall ? ((GrCall)psiElement).getArgumentList() : null;
    if (argList == null || argList instanceof GrCommandArgumentList) return;

    GrCall call = (GrCall)psiElement;

    PsiElement parenth = argList.getLastChild();

    if (parenth != null && ")".equals(parenth.getText()) || call.hasClosureArguments()) return;

    int endOffset = -1;

    for (PsiElement child = argList.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (!(child instanceof PsiErrorElement)) continue;

      final PsiErrorElement errorElement = (PsiErrorElement)child;
      if (errorElement.getErrorDescription().contains("')'")) {
        endOffset = errorElement.getTextRange().getStartOffset();
        break;
      }
    }

    if (endOffset == -1) {
      endOffset = argList.getTextRange().getEndOffset();
    }

    final GrExpression[] params = argList.getExpressionArguments();
    if (params.length > 0 && GrForBodyFixer.startLine(editor.getDocument(), argList) !=
                             GrForBodyFixer.startLine(editor.getDocument(), params[0])) {
      endOffset = argList.getTextRange().getStartOffset() + 1;
    }

    endOffset = CharArrayUtil.shiftBackward(editor.getDocument().getCharsSequence(), endOffset - 1, " \t\n") + 1;
    editor.getDocument().insertString(endOffset, ")");
  }
}

