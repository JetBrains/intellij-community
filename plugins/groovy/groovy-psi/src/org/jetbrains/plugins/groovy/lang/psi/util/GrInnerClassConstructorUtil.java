// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.util;

import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightParameter;

/**
 * @author Max Medvedev
 */
public final class GrInnerClassConstructorUtil {

  public static GrParameter @NotNull [] addEnclosingInstanceParam(@NotNull GrMethod method,
                                                                  @NotNull PsiClass enclosingClass,
                                                                  GrParameter @NotNull [] originalParams,
                                                                  boolean isOptional) {
    final PsiClassType enclosingClassType = JavaPsiFacade.getElementFactory(method.getProject()).createType(enclosingClass, PsiSubstitutor.EMPTY);
    final GrLightParameter enclosing = new GrLightParameter("enclosing", enclosingClassType, method);
    if (isOptional) {
      enclosing.setOptional(true);
      enclosing.setInitializerGroovy(GroovyPsiElementFactory.getInstance(method.getProject()).createExpressionFromText("null"));
    }
    return ArrayUtil.prepend(enclosing, originalParams);
  }

  public static boolean isInnerClassConstructorUsedOutsideOfItParent(@NotNull PsiMethod method, PsiElement place) {
    if (method instanceof GrMethod && method.isConstructor()) {
      PsiClass aClass = method.getContainingClass();
      if (aClass != null && !aClass.hasModifierProperty(PsiModifier.STATIC)) {
        PsiClass containingClass = aClass.getContainingClass();
        if (containingClass != null &&
            PsiUtil.findEnclosingInstanceClassInScope(containingClass, place, true) == null) {
          return true;
        }
      }
    }

    return false;
  }

  @Nullable
  public static PsiClass enclosingClass(@NotNull PsiElement place, @NotNull PsiClass aClass) {
    if (!aClass.hasModifierProperty(PsiModifier.STATIC)) {
      PsiClass containingClass = aClass.getContainingClass();
      if (containingClass != null) {
        if (PsiUtil.hasEnclosingInstanceInScope(containingClass, place, true)) {
          return containingClass;
        }
      }
    }
    return null;
  }
}
