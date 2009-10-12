/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.lang.completion.smartEnter.GroovySmartEnterProcessor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrWhileStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition;

/**
 * User: Dmitry.Krasilschikov
 * Date: 12.08.2008
 */
public class GrWhileConditionFixer implements GrFixer {
  public void apply(Editor editor, GroovySmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (psiElement instanceof GrWhileStatement) {
      final Document doc = editor.getDocument();
      final GrWhileStatement whileStatement = (GrWhileStatement) psiElement;
      final PsiElement rParenth = whileStatement.getRParenth();
      final PsiElement lParenth = whileStatement.getLParenth();
      final GrCondition condition = whileStatement.getCondition();

      if (condition == null) {
        if (lParenth == null || rParenth == null) {
          int stopOffset = doc.getLineEndOffset(doc.getLineNumber(whileStatement.getTextRange().getStartOffset()));
          final GrStatement block = whileStatement.getBody();
          if (block != null) {
            stopOffset = Math.min(stopOffset, block.getTextRange().getStartOffset());
          }
          stopOffset = Math.min(stopOffset, whileStatement.getTextRange().getEndOffset());

          doc.replaceString(whileStatement.getTextRange().getStartOffset(), stopOffset, "while ()");
          processor.registerUnresolvedError(whileStatement.getTextRange().getStartOffset() + "while (".length());
        } else {
          processor.registerUnresolvedError(lParenth.getTextRange().getEndOffset());
        }
      } else if (rParenth == null) {
        doc.insertString(condition.getTextRange().getEndOffset(), ")");
      }
    }
  }
}
