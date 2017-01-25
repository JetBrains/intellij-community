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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveState;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ConcurrencyUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrTupleExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrOperatorExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrBindingVariable;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.Objects;
import java.util.concurrent.ConcurrentMap;

/**
 * @author ilyas
 */
public class GrAssignmentExpressionImpl extends GrOperatorExpressionImpl implements GrAssignmentExpression {

  public GrAssignmentExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Assignment expression";
  }

  @Override
  @NotNull
  public GrExpression getLValue() {
    return Objects.requireNonNull(findExpressionChild(this));
  }

  @Override
  @Nullable
  public GrExpression getRValue() {
    GrExpression[] exprs = findChildrenByClass(GrExpression.class);
    if (exprs.length > 1) {
      return exprs[1];
    }
    return null;
  }

  @Override
  @NotNull
  public PsiElement getOperationToken() {
    return findNotNullChildByType(TokenSets.ASSIGN_OP_SET);
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitAssignmentExpression(this);
  }

  @NotNull
  @Override
  public GroovyResolveResult[] multiResolve(boolean incompleteCode) {
    return TypeInferenceHelper.getCurrentContext().multiResolve(this, incompleteCode, RESOLVER);
  }

  @Override
  public PsiReference getReference() {
    final IElementType operationToken = getOperationTokenType();
    if (operationToken == GroovyTokenTypes.mASSIGN) return null;

    return this;
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    final ElementClassHint classHint = processor.getHint(ElementClassHint.KEY);
    if (!ResolveUtil.shouldProcessProperties(classHint)) return true;

    if (!(getParent() instanceof GroovyFileImpl)) return true;
    final GroovyFileImpl file = (GroovyFileImpl)getParent();
    if (!file.isInScriptBody(lastParent, place)) return true;

    final GrExpression lValue = getLValue();
    if (!processLValue(processor, state, place, file, lValue)) return false;
    if (lValue instanceof GrTupleExpression) {
      for (GrExpression expression : ((GrTupleExpression)lValue).getExpressions()) {
        if (!processLValue(processor, state, place, file, expression)) return false;
      }
    }

    return true;
  }

  private static boolean processLValue(@NotNull PsiScopeProcessor processor,
                                       @NotNull ResolveState state,
                                       @NotNull PsiElement place,
                                       @NotNull GroovyFileImpl file,
                                       @NotNull GrExpression lValue) {
    if (!(lValue instanceof GrReferenceExpression)) return true;
    final GrReferenceExpression lReference = (GrReferenceExpression)lValue;
    if (lReference.isQualified()) return true;
    if (lReference != place && lReference.resolve() != null && !(lReference.resolve() instanceof GrBindingVariable)) return true;

    final String name = lReference.getReferenceName();
    if (name == null) return true;

    String hintName = ResolveUtil.getNameHint(processor);
    if (hintName != null && !name.equals(hintName)) return true;

    final ConcurrentMap<String, GrBindingVariable> bindings = file.getBindings();
    GrBindingVariable variable = bindings.get(name);
    if (variable == null) {
      variable = ConcurrencyUtil.cacheOrGet(bindings, name, new GrBindingVariable(file, name, true));
    }

    if (!variable.hasWriteAccess()) return true;
    return processor.execute(variable, state);
  }

  @Nullable
  @Override
  public PsiType getLeftType() {
    final GrExpression lValue = getLValue();
    if (lValue instanceof GrIndexProperty) {
      // now we have something like map[i] += 2. It equals to map.putAt(i, map.getAt(i).plus(2))
      // by default map[i] resolves to putAt, but we need getAt(). so this hack is for it =)
      return ((GrIndexProperty)lValue).getGetterType();
    }
    else if (lValue instanceof GrReferenceExpression) {
      return ((GrReferenceExpression)lValue).getRValueType();
    }
    else {
      return lValue.getType();
    }
  }

  @Nullable
  @Override
  public PsiType getRightType() {
    GrExpression rValue = getRValue();
    return rValue == null ? null : rValue.getType();
  }

  @Nullable
  @Override
  public PsiType getType() {
    if (TokenSets.ASSIGNMENTS_TO_OPERATORS.containsKey(getOperationTokenType())) {
      return super.getType();
    }
    else {
      return getRightType();
    }
  }

  private static final ResolveCache.PolyVariantResolver<GrAssignmentExpression> RESOLVER = (assignmentExpression, incompleteCode) -> {
    final IElementType opType = assignmentExpression.getOperationTokenType();
    if (opType == GroovyTokenTypes.mASSIGN) return GroovyResolveResult.EMPTY_ARRAY;

    PsiType lType = assignmentExpression.getLeftType();
    if (lType == null) return GroovyResolveResult.EMPTY_ARRAY;

    PsiType rType = assignmentExpression.getRightType();

    final IElementType operatorToken = TokenSets.ASSIGNMENTS_TO_OPERATORS.get(opType);
    return TypesUtil.getOverloadedOperatorCandidates(lType, operatorToken, assignmentExpression, new PsiType[]{rType});
  };
}
