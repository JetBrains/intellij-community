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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrTryCatchStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrCatchClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;

/**
 * User: Dmitry.Krasilschikov
 * Date: 22.05.2007
 */
public abstract class TrySurrounder extends GroovyManyStatementsSurrounder {
  @Override
  protected TextRange getSurroundSelectionRange(GroovyPsiElement element) {
    assert element instanceof GrTryCatchStatement;
    int endOffset = element.getTextRange().getEndOffset();
    GrTryCatchStatement tryCatchStatement = (GrTryCatchStatement) element;

    GrCatchClause[] catchClauses = tryCatchStatement.getCatchClauses();
    if (catchClauses != null && catchClauses.length > 0) {
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
    return "try";
  }
}
