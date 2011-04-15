/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

/**
 * @author sergey.evdokimov
 */
public class DefaultCallExpressionTypeCalculator extends GrCallExpressionTypeCalculator {
  @Override
  public PsiType calculateReturnType(@NotNull GrMethodCall callExpression) {
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
        if (resolved instanceof PsiMethod) {
          PsiMethod method = (PsiMethod) resolved;
          if (resolveResult.isInvokedOnProperty()) {
            final PsiType propertyType = PsiUtil.getSmartReturnType(method);
            returnType = extractReturnTypeFromType(propertyType, true, callExpression);
          } else {
            returnType = getClosureCallOrCurryReturnType(callExpression, refExpr, method);
            if (returnType == null) {
              returnType = PsiUtil.getSmartReturnType(method);
            }
          }
        } else if (resolved instanceof GrVariable) {
          PsiType refType = refExpr.getType();
          final PsiType type = refType == null ? ((GrVariable) resolved).getTypeGroovy() : refType;
          returnType = extractReturnTypeFromType(type, false, callExpression);
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
    else {
      return extractReturnTypeFromType(invoked.getType(), false, callExpression);
    }
  }

  @Nullable
  private static PsiType extractReturnTypeFromType(PsiType type, boolean returnTypeIfFail, GrMethodCall callExpression) {
    PsiType returnType = returnTypeIfFail ? type: null;
    if (type instanceof GrClosureType) {
      returnType = ((GrClosureType) type).getSignature().getReturnType();
    }
    else if (isPsiClassTypeToClosure(type)) {
      assert type instanceof PsiClassType;
      final PsiType[] parameters = ((PsiClassType)type).getParameters();
      if (parameters.length == 1) {
        returnType = parameters[0];
      }
    }
    else if (type instanceof PsiClassType) {
      final GroovyResolveResult[] calls = ResolveUtil
        .getMethodCandidates(type, "call", callExpression, PsiUtil.getArgumentTypes(callExpression.getInvokedExpression(), false));
      returnType = null;
      final PsiManager manager = callExpression.getManager();
      for (GroovyResolveResult call : calls) {
        final PsiType substituted = ResolveUtil.extractReturnTypeFromCandidate(call);
        returnType = TypesUtil.getLeastUpperBoundNullable(returnType, substituted, manager);
      }
    }
    return returnType;
  }


  private static boolean isPsiClassTypeToClosure(PsiType type) {
    if (!(type instanceof PsiClassType)) return false;

    final PsiClass psiClass = ((PsiClassType)type).resolve();
    if (psiClass == null) return false;

    return GroovyCommonClassNames.GROOVY_LANG_CLOSURE.equals(psiClass.getQualifiedName());
  }

  @Nullable
  private static PsiType getClosureCallOrCurryReturnType(GrMethodCall callExpression, GrReferenceExpression refExpr, PsiMethod resolved) {
    PsiClass clazz = resolved.getContainingClass();
    if (clazz != null && GroovyCommonClassNames.GROOVY_LANG_CLOSURE.equals(clazz.getQualifiedName())) {
      if ("call".equals(resolved.getName()) || "curry".equals(resolved.getName())) {
        GrExpression qualifier = refExpr.getQualifierExpression();
        if (qualifier != null) {
          PsiType qType = qualifier.getType();
          if (qType instanceof GrClosureType) {
            if ("call".equals(resolved.getName())) {
              return ((GrClosureType)qType).getSignature().getReturnType();
            }
            else if ("curry".equals(resolved.getName())) {
              final GrArgumentList argumentList = callExpression.getArgumentList();
              return ((GrClosureType)qType).curry(argumentList == null ? 0 : argumentList.getExpressionArguments().length);
            }
          }
        }
      }
    }
    return null;
  }

}
