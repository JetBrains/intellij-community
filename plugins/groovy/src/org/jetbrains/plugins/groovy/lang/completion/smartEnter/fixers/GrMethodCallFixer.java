/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.completion.smartEnter.fixers;

/**
 * User: Dmitry.Krasilschikov
 * Date: 05.08.2008
 */

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
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

public class GrMethodCallFixer extends SmartEnterProcessorWithFixers.Fixer<GroovySmartEnterProcessor> {
  @Override
  public void apply(@NotNull Editor editor, @NotNull GroovySmartEnterProcessor processor, @NotNull PsiElement psiElement) {
    final GrArgumentList argList = psiElement instanceof GrCall ? ((GrCall)psiElement).getArgumentList() : null;
    if (argList == null || argList instanceof GrCommandArgumentList) return;

    GrCall call = (GrCall)psiElement;

    PsiElement parenth = argList.getLastChild();

    if (parenth != null && ")".equals(parenth.getText()) || PsiImplUtil.hasClosureArguments(call)) return;

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

