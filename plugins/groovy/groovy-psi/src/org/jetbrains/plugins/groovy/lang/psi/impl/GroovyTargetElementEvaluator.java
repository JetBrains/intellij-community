// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.codeInsight.JavaTargetElementEvaluator;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMirrorElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.compiled.ClsMethodImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.DefaultConstructor;
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

      PsiElement parent = sourceElement.getParent();
      if (parent instanceof GrNewExpression) {
        newExpr = (GrNewExpression)parent;
      }
      else if (parent instanceof GrAnonymousClassDefinition) {
        PsiElement grandParent = parent.getParent();
        if (grandParent instanceof GrNewExpression) {
          newExpr = (GrNewExpression)grandParent;
        }
        else {
          return null;
        }
      }
      else {
        return null;
      }

      final PsiMethod constructor = newExpr.resolveMethod();
      if (constructor instanceof DefaultConstructor) return constructor.getContainingClass();

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
      if (target instanceof PsiMirrorElement) {
        return ((PsiMirrorElement)target).getPrototype();
      }
      else {
        return target.getNavigationElement();
      }
    }
    return target;
  }
}
