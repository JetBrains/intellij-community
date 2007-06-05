/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCommandArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic.TypesUtil;

/**
 * @author ilyas
 */
public class GrApplicationExpressionImpl extends GrExpressionImpl implements GrApplicationExpression {

  public GrApplicationExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Call expression";
  }

  public GrExpression getFunExpression() {
    return findChildByClass(GrExpression.class);
  }

  public GrExpression[] getArguments() {
    return getArgumentList().getArguments();
  }

  public GrCommandArgumentList getArgumentList() {
    return findChildByClass(GrCommandArgumentList.class);
  }

  public PsiType getType() {
    GrExpression invoked = getFunExpression();
    if (invoked instanceof GrReferenceExpression) {
      PsiElement resolved = ((GrReferenceExpression) invoked).resolve();
      if (resolved instanceof PsiMethod) {
        PsiType type = ((PsiMethod) resolved).getReturnType();
        return TypesUtil.boxPrimitiveType(type, getManager(), getResolveScope());
      }
    }

    return null;
  }
}