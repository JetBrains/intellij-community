/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProviders;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path.GrCallExpressionImpl;
import org.jetbrains.plugins.groovy.lang.resolve.GrReferenceResolveRunnerKt;
import org.jetbrains.plugins.groovy.lang.typing.GrTypeCalculator;

/**
 * @author Maxim.Medvedev
 */
public abstract class GrMethodCallImpl extends GrCallExpressionImpl implements GrMethodCall {

  public GrMethodCallImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  @NotNull
  public GroovyResolveResult[] getCallVariants(@Nullable GrExpression upToArgument) {
    final GrExpression invoked = getInvokedExpression();
    if (!(invoked instanceof GrReferenceExpression)) return GroovyResolveResult.EMPTY_ARRAY;

    return GrReferenceResolveRunnerKt.getCallVariants(((GrReferenceExpression)invoked), upToArgument);
  }

  @Override
  @NotNull
  public GrExpression getInvokedExpression() {
    for (PsiElement cur = this.getFirstChild(); cur != null; cur = cur.getNextSibling()) {
      if (cur instanceof GrExpression) return (GrExpression)cur;
    }
    throw new IncorrectOperationException("invoked expression must not be null");
  }

  @Override
  public PsiType getType() {
    return TypeInferenceHelper.getCurrentContext().getExpressionType(this, GrTypeCalculator::getTypeFromCalculators);
  }

  @Override
  public boolean isCommandExpression() {
    final GrExpression expression = getInvokedExpression();
    if (!(expression instanceof GrReferenceExpression) || ((GrReferenceExpression)expression).getQualifier() == null) return false;

    return ((GrReferenceExpression)expression).getDotToken() == null;
  }

  @NotNull
  @Override
  public GroovyResolveResult[] multiResolve(boolean incompleteCode) {
    GrExpression expression = getInvokedExpression();
    if (!(expression instanceof GrReferenceExpression)) return GroovyResolveResult.EMPTY_ARRAY;
    return ((GrReferenceExpression)expression).multiResolve(incompleteCode);
  }

  @Override
  public ItemPresentation getPresentation() {
    return ItemPresentationProviders.getItemPresentation(this);
  }

  @NotNull
  @Override
  public GrArgumentList getArgumentList() {
    return findNotNullChildByClass(GrArgumentList.class);
  }
}
