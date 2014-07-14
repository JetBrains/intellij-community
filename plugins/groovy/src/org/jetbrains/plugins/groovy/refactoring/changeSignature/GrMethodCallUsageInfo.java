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
package org.jetbrains.plugins.groovy.refactoring.changeSignature;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.changeSignature.PossiblyIncorrectUsage;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

/**
 * @author Maxim.Medvedev
 */
public class GrMethodCallUsageInfo extends UsageInfo implements PossiblyIncorrectUsage {
  private final boolean myToChangeArguments;
  private final boolean myToCatchExceptions;
  private final GrClosureSignatureUtil.ArgInfo<PsiElement>[] myMapToArguments;
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
    else if (resolveResult.getElement() instanceof PsiMethod) {
      PsiMethod resolved = (PsiMethod)resolveResult.getElement();
      mySubstitutor = resolveResult.getSubstitutor();
      if (!element.getManager().areElementsEquivalent(method, resolved)) {
        final PsiClass baseClass = method.getContainingClass();
        final PsiClass derivedClass = resolved.getContainingClass();
        if (baseClass != null && derivedClass != null && InheritanceUtil.isInheritorOrSelf(derivedClass, baseClass, true)) {
          final PsiSubstitutor superClassSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(baseClass, derivedClass, mySubstitutor);
          final MethodSignature superMethodSignature = method.getSignature(superClassSubstitutor);
          final MethodSignature methodSignature = resolved.getSignature(PsiSubstitutor.EMPTY);
          final PsiSubstitutor superMethodSignatureSubstitutor =
            MethodSignatureUtil.getSuperMethodSignatureSubstitutor(methodSignature, superMethodSignature);
          mySubstitutor = TypesUtil.composeSubstitutors(superMethodSignatureSubstitutor, mySubstitutor);
        }
      }
    }
    GrClosureSignature signature = GrClosureSignatureUtil.createSignature(method, mySubstitutor);
    myToChangeArguments = isToChangeArguments;
    myToCatchExceptions = isToCatchExceptions;
    final GrCall call = GroovyRefactoringUtil.getCallExpressionByMethodReference(element);
    if (call == null) {
      myMapToArguments = GrClosureSignatureUtil.ArgInfo.empty_array();
    }
    else {
      myMapToArguments = GrClosureSignatureUtil.mapParametersToArguments(signature, call.getNamedArguments(), call.getExpressionArguments(),
                                                                         call.getClosureArguments(), call, false, false);
    }
  }

  @Nullable
  public PsiMethod getReferencedMethod() {
    final GroovyResolveResult result = resolveMethod(getElement());
    if (result == null) return null;

    final PsiElement element = result.getElement();
    return element instanceof PsiMethod ? (PsiMethod)element : null;
  }

  @Nullable
  private static GroovyResolveResult resolveMethod(final PsiElement ref) {
    if (ref instanceof GrEnumConstant) return ((GrEnumConstant)ref).advancedResolve();
    PsiElement parent = ref.getParent();
    if (parent instanceof GrMethodCall) {
      final GrExpression expression = ((GrMethodCall)parent).getInvokedExpression();
      if (expression instanceof GrReferenceExpression) {
        return ((GrReferenceExpression)expression).advancedResolve();
      }
    }
    else if (parent instanceof GrConstructorCall) {
      return ((GrConstructorCall)parent).advancedResolve();
    }

    return null;
  }


  public GrClosureSignatureUtil.ArgInfo<PsiElement>[] getMapToArguments() {
    return myMapToArguments;
  }

  public PsiSubstitutor getSubstitutor() {
    return mySubstitutor;
  }

  public boolean isPossibleUsage() {
    final GroovyResolveResult resolveResult = resolveMethod(getElement());
    return resolveResult == null || resolveResult.getElement() == null;
  }

  @Override
  public boolean isCorrect() {
    return myMapToArguments != null;
  }
}
