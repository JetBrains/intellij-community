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
package org.jetbrains.plugins.groovy.refactoring.changeSignature;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.changeSignature.PossiblyIncorrectUsage;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author Maxim.Medvedev
 */
public class GrMethodCallUsageInfo extends UsageInfo implements PossiblyIncorrectUsage {
  private final boolean myToChangeArguments;
  private final boolean myToCatchExceptions;
  private GrClosureSignatureUtil.ArgInfo[] myMapToArguments;
  private PsiSubstitutor mySubstitutor;

  public boolean isToCatchExceptions() {
    return myToCatchExceptions;
  }

  public boolean isToChangeArguments() {
    return myToChangeArguments;
  }

  public GrMethodCallUsageInfo(PsiElement element, boolean isToChangeArguments, boolean isToCatchExceptions, PsiMethod method) {
    super(element);
    final GroovyResolveResult resolveResult = resolveMethod(element);
    if (resolveResult == null || resolveResult.getElement() == null) {
      mySubstitutor = PsiSubstitutor.EMPTY;
    }
    else {
      mySubstitutor = resolveResult.getSubstitutor();
    }
    GrClosureSignature signature = GrClosureSignatureUtil.createSignature(method, mySubstitutor);
    myToChangeArguments = isToChangeArguments;
    myToCatchExceptions = isToCatchExceptions;
    final GrArgumentList list = PsiUtil.getArgumentsList(element);
    if (list == null) {
      myMapToArguments = GrClosureSignatureUtil.ArgInfo.EMPTY_ARRAY;
    }
    else {
      myMapToArguments =
        GrClosureSignatureUtil.mapParametersToArguments(signature, list, element.getManager(), GlobalSearchScope.allScope(getProject()));
    }
  }

  @Nullable
  public static GroovyResolveResult resolveMethod(final PsiElement ref) {
    if (ref instanceof GrEnumConstant) return ((GrEnumConstant)ref).resolveConstructorGenerics();
    PsiElement parent = ref.getParent();
    if (parent instanceof GrCallExpression) {
      final GroovyResolveResult[] variants = ((GrCallExpression)parent).getMethodVariants();
      return variants.length == 1 ? variants[0] : null;
    }
    else if (parent instanceof GrApplicationStatement) {
      final GrExpression expression = ((GrApplicationStatement)parent).getFunExpression();
      if (expression instanceof GrReferenceExpression) {
        return ((GrReferenceExpression)expression).advancedResolve();
      }
    }
    else if (parent instanceof GrConstructorInvocation) {
      return ((GrConstructorInvocation)parent).resolveConstructorGenerics();
    }

    return null;
  }


  public GrClosureSignatureUtil.ArgInfo[] getMapToArguments() {
    return myMapToArguments;
  }

  public PsiSubstitutor getSubstitutor() {
    return mySubstitutor;
  }

  public boolean isPossibleUsage() {
    final GroovyResolveResult resolveResult = resolveMethod(getElement());
    return resolveResult == null || resolveResult.getElement() == null;
  }

  public boolean isCorrect() {
    return myMapToArguments != null;
  }
}
