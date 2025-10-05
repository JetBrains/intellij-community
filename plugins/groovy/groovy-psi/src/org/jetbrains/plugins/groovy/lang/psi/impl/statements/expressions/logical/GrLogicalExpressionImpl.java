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
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.logical;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrLogicalExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrBinaryExpressionImpl;

import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtilKt.shouldProcessLocals;
import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtilKt.shouldProcessPatternVariables;

/**
 * author ven
 */
public class GrLogicalExpressionImpl extends GrBinaryExpressionImpl implements GrLogicalExpression {

  public GrLogicalExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    if (!shouldProcessLocals(processor) || !shouldProcessPatternVariables(state)) return true;

    return PsiScopesUtil.walkChildrenScopes(this, processor, state, lastParent, place);
  }

  @Override
  public String toString() {
    return "Logical expression";
  }
}
