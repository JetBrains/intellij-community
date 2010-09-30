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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCommandArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path.GrMethodCallExpressionImpl;

/**
 * @author ilyas
 */
public class GrApplicationStatementImpl extends GrExpressionImpl implements GrApplicationStatement {

  public GrApplicationStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitApplicationStatement(this);
  }

  public String toString() {
    return "Call expression";
  }

  public GrExpression getFunExpression() {
    return getInvokedExpression();
  }

  public GrExpression[] getArguments() {
    final GrCommandArgumentList list = getArgumentList();
    if (list == null) return GrExpression.EMPTY_ARRAY;
    return list.getExpressionArguments();
  }

  public PsiMethod resolveMethod() {
    return PsiImplUtil.resolveMethod(this);
  }

  @Override
  public GrExpression getInvokedExpression() {
    return findNotNullChildByClass(GrExpression.class);
  }

  public GrCommandArgumentList getArgumentList() {
    return findChildByClass(GrCommandArgumentList.class);
  }

  public GrExpression removeArgument(final int number) {
    final GrCommandArgumentList list = getArgumentList();
    return list != null ? list.removeArgument(number) : null;
  }

  public GrNamedArgument addNamedArgument(final GrNamedArgument namedArgument) throws IncorrectOperationException {
    GrCommandArgumentList list = getArgumentList();
    assert list != null;
    return list.addNamedArgument(namedArgument);
  }

  public PsiType getType() {
    return GroovyPsiManager.getInstance(getProject()).getType(this, GrMethodCallExpressionImpl.METHOD_CALL_TYPES_CALCULATOR);
  }
}