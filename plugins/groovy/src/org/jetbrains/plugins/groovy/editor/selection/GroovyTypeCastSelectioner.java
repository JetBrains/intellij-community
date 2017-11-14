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

package org.jetbrains.plugins.groovy.editor.selection;

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrTypeCastExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

import java.util.List;

/**
 * @author ilyas
 */
public class GroovyTypeCastSelectioner extends ExtendWordSelectionHandlerBase {
  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    return e instanceof GrTypeCastExpression;
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    List<TextRange> result = super.select(e, editorText, cursorOffset, editor);

    if (e instanceof GrTypeCastExpression) {
      GrTypeCastExpression castExpression = ((GrTypeCastExpression) e);
      GrTypeElement type = castExpression.getCastTypeElement();
      TextRange range = type.getTextRange();
      if (range.contains(cursorOffset)) {
        PsiElement leftParen = castExpression.getLeftParen();
        PsiElement rightParen = castExpression.getRightParen();
        if (leftParen.getTextOffset() < rightParen.getTextOffset()) {
          range = new TextRange(leftParen.getTextRange().getStartOffset(), rightParen.getTextRange().getEndOffset());
          result.add(range);
        }
      }
    }
    return result;
  }
}