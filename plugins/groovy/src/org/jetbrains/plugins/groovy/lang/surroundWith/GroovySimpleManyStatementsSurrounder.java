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

import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.intellij.openapi.util.TextRange;

/**
 * This is a base class for simple "Many Statement" surrounds, such as with() and shouldFail().
 *
 * User: Hamlet D'Arcy
 */
public abstract class GroovySimpleManyStatementsSurrounder extends GroovyManyStatementsSurrounder {
  @Override
  protected final GroovyPsiElement doSurroundElements(PsiElement[] elements, PsiElement context) throws IncorrectOperationException {
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(elements[0].getProject());
    GrMethodCallExpression withCall = (GrMethodCallExpression) factory.createExpressionFromText(getReplacementTokens(), context);
    addStatements(withCall.getClosureArguments()[0], elements);
    return withCall;
  }

  protected abstract String getReplacementTokens();

  @Override
  protected final TextRange getSurroundSelectionRange(GroovyPsiElement element) {
    assert element instanceof GrMethodCallExpression;

    GrMethodCallExpression withCall = (GrMethodCallExpression) element;
    GrCondition condition = withCall.getExpressionArguments()[0];
    int endOffset = condition.getTextRange().getStartOffset();

    condition.getParent().getNode().removeChild(condition.getNode());

    return new TextRange(endOffset, endOffset);
  }

}
