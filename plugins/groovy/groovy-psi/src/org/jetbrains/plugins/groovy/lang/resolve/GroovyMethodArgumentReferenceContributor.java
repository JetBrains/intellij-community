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
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.extensions.GroovyMethodInfo;
import org.jetbrains.plugins.groovy.extensions.GroovyNamedArgumentReferenceProvider;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConditionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.patterns.GroovyPatterns;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author Sergey Evdokimov
 */
public final class GroovyMethodArgumentReferenceContributor extends PsiReferenceContributor {

  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(GroovyPatterns.stringLiteral(), new MyProvider());
  }

  private static class MyProvider extends PsiReferenceProvider {
    @NotNull
    @Override
    public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
      GrExpression argument = (GrExpression)element;
      PsiElement parent = element.getParent();

      if (parent instanceof GrConditionalExpression) {
        // support case: foo(a > b ? "aaa" : "bbb")

        if (((GrConditionalExpression)parent).getCondition() == parent) return PsiReference.EMPTY_ARRAY;
        argument = (GrConditionalExpression)parent;
        parent = parent.getParent();
      }

      if (parent instanceof GrListOrMap) {
        // support case: foo(["aaa", "bbb"])
        if (!((GrListOrMap)parent).isMap()) {
          argument = (GrListOrMap)parent;
          parent = parent.getParent();
        }
      }

      if (parent instanceof GrNamedArgument) {
        return createReferencesForNamedArgument(element, (GrNamedArgument)parent, context);
      }

      if (parent instanceof GrArgumentList) {
        GrArgumentList argumentList = (GrArgumentList)parent;
        int index = argumentList.getExpressionArgumentIndex(argument);

        PsiElement call = argumentList.getParent();
        if (!(call instanceof GrMethodCall)) return PsiReference.EMPTY_ARRAY;

        return PsiReference.EMPTY_ARRAY;
      }

      return PsiReference.EMPTY_ARRAY;
    }

    private static PsiReference[] createReferencesForNamedArgument(@NotNull PsiElement element,
                                                                   GrNamedArgument namedArgument,
                                                                   @NotNull ProcessingContext context) {
      String labelName = namedArgument.getLabelName();
      if (labelName == null) return PsiReference.EMPTY_ARRAY;

      if (!GroovyMethodInfo.getAllSupportedNamedArguments().contains(labelName)) {
        // Optimization: avoid unnecessary resolve.
        return PsiReference.EMPTY_ARRAY;
      }

      PsiElement call = PsiUtil.getCallByNamedParameter(namedArgument);

      if (!(call instanceof GrMethodCall)) return PsiReference.EMPTY_ARRAY;

      GrExpression invokedExpression = ((GrMethodCall)call).getInvokedExpression();
      if (!(invokedExpression instanceof GrReferenceExpression)) return PsiReference.EMPTY_ARRAY;

      for (GroovyResolveResult result : ((GrReferenceExpression)invokedExpression).multiResolve(false)) {
        PsiElement eMethod = result.getElement();
        if (!(eMethod instanceof PsiMethod)) continue;

        PsiMethod method = (PsiMethod)eMethod;

        for (GroovyMethodInfo info : GroovyMethodInfo.getInfos(method)) {
          Object referenceProvider = info.getNamedArgReferenceProvider(labelName);
          if (referenceProvider != null) {
            PsiReference[] refs;

            if (referenceProvider instanceof GroovyNamedArgumentReferenceProvider) {
              refs = ((GroovyNamedArgumentReferenceProvider)referenceProvider).createRef(element, namedArgument, result, context);
            }
            else {
              refs = ((PsiReferenceProvider)referenceProvider).getReferencesByElement(element, context);
            }

            if (refs.length > 0) {
              return refs;
            }
          }
        }
      }

      return PsiReference.EMPTY_ARRAY;
    }

    //@NotNull
    //private PsiReference[] createReferencesForArgument(@NotNull PsiElement element,
    //                                                   int index,
    //                                                   @NotNull GrMethodCall methodCall) {
    //  GrExpression invokedExpression = methodCall.getInvokedExpression();
    //  if (!(invokedExpression instanceof GrReferenceExpression)) return PsiReference.EMPTY_ARRAY;
    //
    //  String methodName = ((GrReferenceExpression)invokedExpression).getReferenceName();
    //  if (methodName == null) return PsiReference.EMPTY_ARRAY;
    //
    //  for (String key : new String[] {methodName, null}) {
    //    for (GroovyResolveResult result : ((GrReferenceExpression)invokedExpression).multiResolve(false)) {
    //      PsiElement eMethod = result.getElement();
    //      if (eMethod instanceof PsiMethod) {
    //        PsiMethod method = (PsiMethod)eMethod;
    //
    //        //noinspection ConstantConditions
    //        if (key != null && !key.equals(method.getName())) continue;
    //
    //        for (Pair<Contributor.Provider, Condition<PsiMethod>> pair : list) {
    //          if (pair.second.value(method)) {
    //            PsiReference[] res;
    //            if (attrNameOrParameterIndex instanceof Integer) {
    //              res = pair.first.createRef(element, methodCall, (Integer)attrNameOrParameterIndex, result);
    //            }
    //            else {
    //              assert namedArgument != null;
    //              res = pair.first.createRef(element, namedArgument, result);
    //            }
    //
    //            if (res.length > 0) {
    //              return res;
    //            }
    //          }
    //        }
    //      }
    //    }
    //  }
    //
    //  return PsiReference.EMPTY_ARRAY;
    //}
  }

}
