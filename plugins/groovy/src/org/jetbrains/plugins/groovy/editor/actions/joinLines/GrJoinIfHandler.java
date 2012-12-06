/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.editor.actions.joinLines;

import com.intellij.codeInsight.editorActions.JoinLinesHandlerDelegate;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrBlockStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;

public class GrJoinIfHandler implements JoinLinesHandlerDelegate {
  @Override
  public int tryJoinLines(Document document, PsiFile file, int start, int end) {
    if (!(file instanceof GroovyFileBase)) return CANNOT_JOIN;

    final PsiElement element = file.findElementAt(end);

    final GrStatementOwner statementOwner = PsiTreeUtil.getParentOfType(element, GrStatementOwner.class, true, GroovyFileBase.class);
    if (statementOwner == null) return CANNOT_JOIN;
    if (statementOwner.getStatements().length > 1) return CANNOT_JOIN;

    final PsiElement parent = statementOwner.getParent();
    if (!(parent instanceof GrBlockStatement)) return CANNOT_JOIN;

    final PsiElement pparent = parent.getParent();
    if (!(pparent instanceof GrIfStatement)) return CANNOT_JOIN;

    final GrIfStatement ifStatement = (GrIfStatement)pparent;
    if (parent == ifStatement.getThenBranch() || parent == ifStatement.getElseBranch()) {
      final GrStatement statement = ((GrBlockStatement)(parent)).replaceWithStatement(statementOwner.getStatements()[0]);
      return statement.getTextRange().getStartOffset();
    }

    return CANNOT_JOIN;
  }
}
