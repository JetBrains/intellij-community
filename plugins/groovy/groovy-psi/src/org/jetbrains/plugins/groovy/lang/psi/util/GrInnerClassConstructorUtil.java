// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.util;

import com.intellij.psi.*;
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
    final GrParameter[] parameters = new GrParameter[originalParams.length + 1];
    final PsiClassType enclosingClassType = JavaPsiFacade.getElementFactory(method.getProject()).createType(enclosingClass, PsiSubstitutor.EMPTY);
    final GrLightParameter enclosing = new GrLightParameter("enclosing", enclosingClassType, method);
    if (isOptional) {
      enclosing.setOptional(true);
      enclosing.setInitializerGroovy(GroovyPsiElementFactory.getInstance(method.getProject()).createExpressionFromText("null"));
    }
    parameters[0] = enclosing;
    System.arraycopy(originalParams, 0, parameters, 1, originalParams.length);
    return parameters;
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

  public static PsiType @NotNull [] addEnclosingArgIfNeeded(PsiType @NotNull [] types, @NotNull PsiElement place, @NotNull PsiClass aClass) {
    if (!aClass.hasModifierProperty(PsiModifier.STATIC)) {
      PsiClass containingClass = aClass.getContainingClass();
      if (containingClass != null) {
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(place.getProject());
        if (PsiUtil.hasEnclosingInstanceInScope(containingClass, place, true)) {
          PsiType[] newTypes = PsiType.createArray(types.length + 1);
          newTypes[0] = factory.createType(containingClass);
          System.arraycopy(types, 0, newTypes, 1, types.length);
          types = newTypes;
        }
      }
    }
    return types;
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
