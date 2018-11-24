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
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrOperatorExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrBindingVariable;
import org.jetbrains.plugins.groovy.lang.resolve.DependentResolver;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.DynamicMembersHint;

import java.util.Collection;
import java.util.List;
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
    return findNotNullChildByType(TokenSets.ASSIGNMENTS);
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
                                     @Nullable PsiElement lastParent,
                                     @NotNull PsiElement place) {
    if (!shouldProcessBindings(this, processor, lastParent, place)) return true;
    return processLValue(processor, state, place, (GroovyFileImpl)getParent(), getLValue());
  }

  static boolean shouldProcessBindings(@NotNull PsiElement owner,
                                       @NotNull PsiScopeProcessor processor,
                                       @Nullable PsiElement lastParent,
                                       @NotNull PsiElement place) {
    final ElementClassHint classHint = processor.getHint(ElementClassHint.KEY);
    if (!ResolveUtil.shouldProcessProperties(classHint)) return false;
    final DynamicMembersHint dynamicMembersHint = processor.getHint(DynamicMembersHint.KEY);
    if (dynamicMembersHint != null && !dynamicMembersHint.shouldProcessProperties()) return false;

    PsiElement parent = owner.getParent();
    if (!(parent instanceof GroovyFileImpl)) return false;

    final GroovyFileImpl file = (GroovyFileImpl)parent;
    if (!file.isInScriptBody(lastParent, place)) return false;

    return true;
  }


  static boolean processLValue(@NotNull PsiScopeProcessor processor,
                               @NotNull ResolveState state,
                               @NotNull PsiElement place,
                               @NotNull GroovyFileImpl file,
                               @NotNull GrExpression lValue) {
    if (!(lValue instanceof GrReferenceExpression)) return true;

    final GrReferenceExpression lReference = (GrReferenceExpression)lValue;
    if (lReference.isQualified()) return true;

    final String name = lReference.getReferenceName();
    if (name == null) return true;

    String hintName = ResolveUtil.getNameHint(processor);
    if (hintName != null && !name.equals(hintName)) return true;

    if (lReference != place && lReference.resolve() != null && !(lReference.resolve() instanceof GrBindingVariable)) return true;
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
    return getLValue().getType();
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

  private static final ResolveCache.PolyVariantResolver<GrAssignmentExpression> RESOLVER = new DependentResolver<GrAssignmentExpression>() {

    @Override
    public Collection<PsiPolyVariantReference> collectDependencies(@NotNull GrAssignmentExpression ref) {
      List<PsiPolyVariantReference> result = new SmartList<>();
      ref.accept(new PsiRecursiveElementWalkingVisitor() {
        @Override
        public void visitElement(PsiElement element) {
          if (element instanceof GrAssignmentExpression) {
            super.visitElement(element);
          }
          else if (element instanceof GrParenthesizedExpression) {
            GrExpression operand = ((GrParenthesizedExpression)element).getOperand();
            if (operand != null) super.visitElement(operand);
          }
        }

        @Override
        protected void elementFinished(PsiElement element) {
          if (element instanceof GrAssignmentExpression) {
            result.add(((GrAssignmentExpression)element));
          }
        }
      });
      return result;
    }

    @NotNull
    @Override
    public ResolveResult[] doResolve(@NotNull GrAssignmentExpression assignmentExpression, boolean incomplete) {
      final IElementType opType = assignmentExpression.getOperationTokenType();
      if (opType == GroovyTokenTypes.mASSIGN) return GroovyResolveResult.EMPTY_ARRAY;

      PsiType lType = assignmentExpression.getLeftType();
      if (lType == null) return GroovyResolveResult.EMPTY_ARRAY;

      PsiType rType = assignmentExpression.getRightType();

      final IElementType operatorToken = TokenSets.ASSIGNMENTS_TO_OPERATORS.get(opType);
      return TypesUtil.getOverloadedOperatorCandidates(lType, operatorToken, assignmentExpression, new PsiType[]{rType});
    }
  };
}
