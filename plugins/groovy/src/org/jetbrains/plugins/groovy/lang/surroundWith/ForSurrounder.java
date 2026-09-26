// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.surroundWith;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrBlockStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrForStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForClause;

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
    return GroovyBundle.message("surround.with.for");
  }
}
