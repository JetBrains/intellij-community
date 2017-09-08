/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import java.util.Iterator;

import static org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.isCompileStatic;

/**
 * @author Max Medvedev
 */
public class GrGenericTypeConverter extends GrTypeConverter {

  @Override
  public boolean isApplicableTo(@NotNull ApplicableTo position) {
    switch (position) {
      case METHOD_PARAMETER:
      case GENERIC_PARAMETER:
      case ASSIGNMENT:
      case RETURN_VALUE:
        return true;
      default:
        return false;
    }
  }

  @Override
  @Nullable
  public ConversionResult isConvertibleEx(@NotNull PsiType ltype,
                                          @NotNull PsiType rtype,
                                          @NotNull GroovyPsiElement context,
                                          @NotNull ApplicableTo position) {
    if (!(ltype instanceof PsiClassType && rtype instanceof PsiClassType)) {
      return null;
    }
    if (isCompileStatic(context) ) return null;
    PsiClass lclass = ((PsiClassType)ltype).resolve();
    PsiClass rclass = ((PsiClassType)rtype).resolve();

    if (lclass == null || rclass == null) return null;

    if (lclass.getTypeParameters().length == 0) return null;

    if (!InheritanceUtil.isInheritorOrSelf(rclass, lclass, true)) return null;

    PsiClassType.ClassResolveResult lresult = ((PsiClassType)ltype).resolveGenerics();
    PsiClassType.ClassResolveResult rresult = ((PsiClassType)rtype).resolveGenerics();

    if (typeParametersAgree(lclass, rclass, lresult.getSubstitutor(), rresult.getSubstitutor(), context)) return ConversionResult.OK;

    return null;
  }

  private static boolean typeParametersAgree(@NotNull PsiClass leftClass,
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
