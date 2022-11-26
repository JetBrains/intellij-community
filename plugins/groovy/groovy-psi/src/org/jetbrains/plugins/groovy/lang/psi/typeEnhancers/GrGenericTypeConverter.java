// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.psi.*;
import com.intellij.psi.PsiClassType.ClassResolveResult;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import java.util.Iterator;

import static org.jetbrains.plugins.groovy.lang.psi.util.CompileStaticUtil.isCompileStatic;

/**
 * @author Max Medvedev
 */
public class GrGenericTypeConverter extends GrTypeConverter {

  @Override
  public boolean isApplicableTo(@NotNull Position position) {
    return switch (position) {
      case METHOD_PARAMETER, GENERIC_PARAMETER, ASSIGNMENT, RETURN_VALUE -> true;
      default -> false;
    };
  }

  @Override
  @Nullable
  public ConversionResult isConvertible(@NotNull PsiType ltype,
                                        @NotNull PsiType rtype,
                                        @NotNull Position position,
                                        @NotNull GroovyPsiElement context) {
    if (!(ltype instanceof PsiClassType && rtype instanceof PsiClassType)) {
      return null;
    }
    if (isCompileStatic(context)) return null;

    ClassResolveResult lResult = ((PsiClassType)ltype).resolveGenerics();
    PsiClass lClass = lResult.getElement();
    if (lClass == null) return null;
    if (lClass.getTypeParameters().length == 0) return null;

    ClassResolveResult rResult = ((PsiClassType)rtype).resolveGenerics();
    PsiClass rClass = rResult.getElement();
    if (rClass == null) return null;

    if (!InheritanceUtil.isInheritorOrSelf(rClass, lClass, true)) return null;
    if (!((PsiClassType)ltype).hasParameters()) return ConversionResult.OK;
    if (typeParametersAgree(lClass, rClass, lResult.getSubstitutor(), rResult.getSubstitutor(), context)) return ConversionResult.OK;

    return null;
  }

  public static boolean typeParametersAgree(@NotNull PsiClass leftClass,
                                             @NotNull PsiClass rightClass,
                                             @NotNull PsiSubstitutor leftSubstitutor,
                                             @NotNull PsiSubstitutor rightSubstitutor,
                                             @NotNull PsiElement context) {

    if (!leftClass.hasTypeParameters()) return true;

    if (!leftClass.getManager().areElementsEquivalent(leftClass, rightClass)) {
      rightSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(leftClass, rightClass, rightSubstitutor);
      rightClass = leftClass;
    }
    else if (!rightClass.hasTypeParameters()) return true;

    Iterator<PsiTypeParameter> li = PsiUtil.typeParametersIterator(leftClass);
    Iterator<PsiTypeParameter> ri = PsiUtil.typeParametersIterator(rightClass);

    while (li.hasNext()) {
      if (!ri.hasNext()) return false;
      PsiTypeParameter lp = li.next();
      PsiTypeParameter rp = ri.next();
      final PsiType typeLeft = leftSubstitutor.substitute(lp);
      if (typeLeft == null) continue;
      final PsiType typeRight = rightSubstitutor.substituteWithBoundsPromotion(rp);
      if (typeRight == null) {
        // compatibility feature: allow to assign raw types to generic ones
        return true;
      }

      if (typeLeft instanceof PsiClassType && typeRight instanceof PsiClassType) {
        if (!TypesUtil.isAssignableByMethodCallConversion(typeLeft, typeRight, context)) {
          return false;
        }
      }
      else if (!TypeConversionUtil.typesAgree(typeLeft, typeRight, true)) {
        return false;
      }
    }

    return true;
  }
}
