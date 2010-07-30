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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrReferenceExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.ArrayList;

/**
 * @author ilyas
 */
public class GrMethodCallExpressionImpl extends GrCallExpressionImpl implements GrMethodCallExpression {
  private static final Function<GrMethodCallExpressionImpl, PsiType> TYPES_CALCULATOR = new Function<GrMethodCallExpressionImpl, PsiType>() {
    @Nullable
    public PsiType fun(GrMethodCallExpressionImpl callExpression) {
      GrExpression invoked = callExpression.getInvokedExpression();
      if (invoked instanceof GrReferenceExpression) {
        GrReferenceExpression refExpr = (GrReferenceExpression) invoked;
        final GroovyResolveResult[] resolveResults = refExpr.multiResolve(false);
        PsiManager manager = callExpression.getManager();
        GlobalSearchScope scope = callExpression.getResolveScope();
        PsiType result = null;
        for (GroovyResolveResult resolveResult : resolveResults) {
          PsiElement resolved = resolveResult.getElement();
          PsiType returnType = null;
          if (resolved instanceof PsiMethod && !GroovyPsiManager.isTypeBeingInferred(resolved)) {
            PsiMethod method = (PsiMethod) resolved;
            if (refExpr.getUserData(GrReferenceExpressionImpl.IS_RESOLVED_TO_GETTER) != null) {
              final PsiType propertyType = PsiUtil.getSmartReturnType(method);
              if (propertyType instanceof GrClosureType) {
                returnType = ((GrClosureType)propertyType).getSignature().getReturnType();
              }
            } else {
              returnType = getClosureCallOrCurryReturnType(callExpression, refExpr, method);
              if (returnType == null) {
                returnType = PsiUtil.getSmartReturnType(method);
              }
            }
          } else if (resolved instanceof GrVariable) {
            PsiType refType = refExpr.getType();
            final PsiType type = refType == null ? ((GrVariable) resolved).getTypeGroovy() : refType;
            if (type instanceof GrClosureType) {
              returnType = ((GrClosureType) type).getSignature().getReturnType();
            }
          }
          if (returnType == null) return null;
          returnType = resolveResult.getSubstitutor().substitute(returnType);
          returnType = TypesUtil.boxPrimitiveType(returnType, manager, scope);

          if (result == null || returnType.isAssignableFrom(result)) result = returnType;
          else if (!result.isAssignableFrom(returnType))
            result = TypesUtil.getLeastUpperBound(result, returnType, manager);
        }

        if (result == null) return null;

        if (refExpr.getDotTokenType() != GroovyTokenTypes.mSPREAD_DOT) {
          return result;
        } else {
          return ResolveUtil.getListTypeForSpreadOperator(refExpr, result);
        }
      }

      return null;
    }
  };

  @Nullable
  private static PsiType getClosureCallOrCurryReturnType(GrMethodCallExpressionImpl callExpression,
                                                         GrReferenceExpression refExpr,
                                                         PsiMethod resolved) {
    PsiClass clazz = resolved.getContainingClass();
    if (clazz != null && GrClosableBlock.GROOVY_LANG_CLOSURE.equals(clazz.getQualifiedName())) {
      if ("call".equals(resolved.getName()) || "curry".equals(resolved.getName())) {
        GrExpression qualifier = refExpr.getQualifierExpression();
        if (qualifier != null) {
          PsiType qType = qualifier.getType();
          if (qType instanceof GrClosureType) {
            if ("call".equals(resolved.getName())) {
              return ((GrClosureType)qType).getSignature().getReturnType();
            }
            else if ("curry".equals(resolved.getName())) {
              return ((GrClosureType)qType).curry(callExpression.getExpressionArguments().length);
            }
          }
        }
      }
    }
    return null;
  }

  public GrMethodCallExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Method call";
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitMethodCallExpression(this);
  }

  public PsiType getType() {
    return GroovyPsiManager.getInstance(getProject()).getType(this, TYPES_CALCULATOR);
  }

  @Nullable
  public GrExpression getInvokedExpression() {
    return findChildByClass(GrExpression.class);
  }

  public GrExpression replaceClosureArgument(@NotNull GrClosableBlock closure, @NotNull GrExpression newExpr) throws IncorrectOperationException {

    ASTNode parentNode = this.getParent().getNode();
    if (!(newExpr instanceof GrClosableBlock)) {
      ArrayList<GrExpression> allArgs = new ArrayList<GrExpression>();
      // Collecting all arguments
      ContainerUtil.addAll(allArgs, getExpressionArguments());
      ArrayList<GrExpression> closureArgs = new ArrayList<GrExpression>();
      for (GrExpression closArg : getClosureArguments()) {
        if (closArg.equals(closure)) break;
        closureArgs.add(closArg);
      }
      allArgs.addAll(closureArgs);
      allArgs.add(newExpr);
      int refIndex = allArgs.size() - 1;

      // New argument list
      GrArgumentList newArgList =
        GroovyPsiElementFactory.getInstance(getProject()).createExpressionArgumentList(allArgs.toArray(new GrExpression[allArgs.size()]));
      while (closure.getNode().getTreePrev() != null &&
             !(closure.getNode().getTreePrev().getPsi() instanceof GrArgumentList)) {
        parentNode.removeChild(closure.getNode().getTreePrev());
      }
      parentNode.removeChild(closure.getNode());
      getArgumentList().replaceWithArgumentList(newArgList);
      GrExpression[] arguments = getArgumentList().getExpressionArguments();
      assert arguments.length == refIndex + 1;
      return arguments[refIndex];
    } else {
      return closure.replaceWithExpression(newExpr, true);
    }
  }

  public PsiMethod resolveMethod() {
    return PsiImplUtil.resolveMethod(this);
  }

  @NotNull
  public GroovyResolveResult[] getCallVariants(@Nullable GrExpression upToArgument) {
    final GrExpression invoked = getInvokedExpression();
    if (!(invoked instanceof GrReferenceExpressionImpl)) return GroovyResolveResult.EMPTY_ARRAY;

    return ((GrReferenceExpressionImpl)invoked).getCallVariants(upToArgument);
  }

}
