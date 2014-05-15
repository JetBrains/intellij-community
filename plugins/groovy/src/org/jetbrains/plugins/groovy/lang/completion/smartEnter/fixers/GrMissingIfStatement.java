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

import com.intellij.lang.SmartEnterProcessorWithFixers;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.completion.smartEnter.GroovySmartEnterProcessor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrBlockStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;

/**
 * User: Dmitry.Krasilschikov
 * Date: 04.07.2008
 */
public class GrMissingIfStatement extends SmartEnterProcessorWithFixers.Fixer<GroovySmartEnterProcessor> {
  @Override
  public void apply(@NotNull Editor editor, @NotNull GroovySmartEnterProcessor processor, @NotNull PsiElement psiElement) {
    if (!(psiElement instanceof GrIfStatement)) return;

    GrIfStatement ifStatement = (GrIfStatement) psiElement;
    final Document document = editor.getDocument();
    final GrStatement elseBranch = ifStatement.getElseBranch();
    final PsiElement elseElement = ifStatement.getElseKeyword();

    if (elseElement != null && (elseBranch == null || !(elseBranch instanceof GrBlockStatement) &&
            GrForBodyFixer.startLine(editor.getDocument(), elseBranch) > GrForBodyFixer.startLine(editor.getDocument(), elseElement))) {
      document.insertString(elseElement.getTextRange().getEndOffset(), "{}");
    }

    GrStatement thenBranch = ifStatement.getThenBranch();
    if (thenBranch instanceof GrBlockStatement) return;

    boolean transformingOneLiner = false;
    if (thenBranch != null && GrForBodyFixer.startLine(editor.getDocument(), thenBranch) ==
                              GrForBodyFixer.startLine(editor.getDocument(), ifStatement)) {
      if (ifStatement.getCondition() != null) {
        return;
      }
      transformingOneLiner = true;
    }

    final PsiElement rParenth = ifStatement.getRParenth();
    if (rParenth == null) return;

    if (elseBranch == null && !transformingOneLiner || thenBranch == null) {
      document.insertString(rParenth.getTextRange().getEndOffset(), "{}");
    } else {
      document.insertString(rParenth.getTextRange().getEndOffset(), "{");
      document.insertString(thenBranch.getTextRange().getEndOffset() + 1, "}");
    }
  }
}
