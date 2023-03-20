// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.surroundWith;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrCatchClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrTryCatchStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;

public abstract class TrySurrounder extends GroovyManyStatementsSurrounder {
  @Override
  protected TextRange getSurroundSelectionRange(GroovyPsiElement element) {
    assert element instanceof GrTryCatchStatement;
    int endOffset = element.getTextRange().getEndOffset();
    GrTryCatchStatement tryCatchStatement = (GrTryCatchStatement) element;

    GrCatchClause[] catchClauses = tryCatchStatement.getCatchClauses();
    if (catchClauses.length > 0) {
      GrParameter parameter = catchClauses[0].getParameter();
      if (parameter == null) {
        GrOpenBlock block = catchClauses[0].getBody();
        assert block != null;
        endOffset = block.getTextRange().getEndOffset();
      } else {
        endOffset = parameter.getTextRange().getStartOffset();
        parameter.getParent().getNode().removeChild(parameter.getNode());
      }
    } else {
      GrOpenBlock block = tryCatchStatement.getTryBlock();
      if (block != null) {
        GrStatement[] statements = block.getStatements();
        if (statements.length > 0) {
          endOffset = statements[0].getTextRange().getStartOffset();
        }
      }
    }

    return new TextRange(endOffset, endOffset);
  }

  @Override
  public String getTemplateDescription() {
    //noinspection DialogTitleCapitalization
    return GroovyBundle.message("surround.with.try");
  }
}
