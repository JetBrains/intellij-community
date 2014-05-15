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
package org.jetbrains.plugins.groovy.lang.surroundWith;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrBlockStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrForStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForClause;

/**
 * User: Dmitry.Krasilschikov
 * Date: 25.05.2007
 */
public class ForSurrounder extends GroovyManyStatementsSurrounder {
  @Override
  protected GroovyPsiElement doSurroundElements(PsiElement[] elements, PsiElement context) throws IncorrectOperationException {
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(elements[0].getProject());
    GrForStatement whileStatement = (GrForStatement) factory.createStatementFromText("for(a in b){\n}", context);
    addStatements(((GrBlockStatement) whileStatement.getBody()).getBlock(), elements);
    return whileStatement;
  }

  @Override
  protected TextRange getSurroundSelectionRange(GroovyPsiElement element) {
    assert element instanceof GrForStatement;
    GrForClause clause = ((GrForStatement) element).getClause();

    int endOffset = element.getTextRange().getEndOffset();
    if (clause != null) {
      endOffset = clause.getTextRange().getStartOffset();
      clause.getParent().getNode().removeChild(clause.getNode());
    }
    return new TextRange(endOffset, endOffset);
  }

  @Override
  public String getTemplateDescription() {
    return "for";
  }
}
