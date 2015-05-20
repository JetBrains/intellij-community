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

package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.codeInsight.JavaTargetElementEvaluator;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.compiled.ClsMethodImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrRenameableLightElement;

/**
 * @author Maxim.Medvedev
 */
public class GroovyTargetElementEvaluator extends JavaTargetElementEvaluator {

  public static final Key<Object> NAVIGATION_ELEMENT_IS_NOT_TARGET = Key.create("GroovyTargetElementEvaluator.DONT_FOLLOW_NAVIGATION_ELEMENT");


  @Override
  public PsiElement getElementByReference(@NotNull PsiReference ref, int flags) {
    PsiElement sourceElement = ref.getElement();

    if (sourceElement instanceof GrCodeReferenceElement) {
      GrNewExpression newExpr;

      if (sourceElement.getParent() instanceof GrNewExpression) {
        newExpr = (GrNewExpression)sourceElement.getParent();
      }
      else if (sourceElement.getParent().getParent() instanceof GrNewExpression) {//anonymous class declaration
        newExpr = (GrNewExpression)sourceElement.getParent().getParent();
      }
      else {
        return null;
      }

      final PsiMethod constructor = newExpr.resolveMethod();
      final GrArgumentList argumentList = newExpr.getArgumentList();
      if (constructor != null &&
          argumentList != null &&
          PsiImplUtil.hasNamedArguments(argumentList) &&
          !PsiImplUtil.hasExpressionArguments(argumentList)) {
        if (constructor.getParameterList().getParametersCount() == 0) return constructor.getContainingClass();
      }

      return constructor;
    }

    if (sourceElement instanceof GrReferenceExpression) {
      PsiElement resolved = ((GrReferenceExpression)sourceElement).resolve();
       if (resolved instanceof GrGdkMethod || !(resolved instanceof GrRenameableLightElement)) {
        return correctSearchTargets(resolved);
      }
      return resolved;
    }

    return null;
  }

  @Nullable
  public static PsiElement correctSearchTargets(@Nullable PsiElement target) {
    if (target instanceof ClsMethodImpl) {
      PsiElement mirror = ((ClsMethodImpl)target).getSourceMirrorMethod();
      if (mirror != null) {
        return mirror.getNavigationElement();
      }
    }
    if (target != null && !(target instanceof GrAccessorMethod) && target.getUserData(NAVIGATION_ELEMENT_IS_NOT_TARGET) == null) {
      return target.getNavigationElement();
    }
    return target;
  }
}
