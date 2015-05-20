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
package org.jetbrains.plugins.groovy.gpp;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.findUsages.LiteralConstructorReference;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrSignature;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrTypeConverter;

/**
 * @author peter
 */
public class GppTypeConverter extends GrTypeConverter {

  @Override
  public boolean isAllowedInMethodCall() {
    return true;
  }

  @Override
  public Boolean isConvertible(@NotNull PsiType lType, @NotNull PsiType rType, @NotNull GroovyPsiElement context) {
    if (context instanceof GrListOrMap &&
        context.getReference() instanceof LiteralConstructorReference &&
        ((LiteralConstructorReference)context.getReference()).getConstructedClassType() != null) return null;

    if (rType instanceof GrTupleType) {
      final GrTupleType tupleType = (GrTupleType)rType;

      final PsiType expectedComponent = PsiUtil.extractIterableTypeParameter(lType, false);
      if (expectedComponent != null && isMethodCallConversion(context)) {
        PsiType[] parameters = tupleType.getParameters();
        if (parameters.length == 1) {
          PsiType tupleComponent = parameters[0];
          if (tupleComponent != null &&
              TypesUtil.isAssignable(expectedComponent, tupleComponent, context) && hasDefaultConstructor(lType)) {
            return true;
          }
        }
      }
    }

    return null;
  }

  public static boolean isClosureOverride(PsiType[] methodParameters, GrClosureType closureType, GroovyPsiElement context) {
    final GrSignature signature = closureType.getSignature();
    if (methodParameters != null && GrClosureSignatureUtil.isSignatureApplicable(signature, methodParameters, context)) {
      return true;
    }
    return false;
  }

  public static boolean hasDefaultConstructor(PsiType type) {
    final PsiClass psiClass = PsiUtil.resolveClassInType(type);
    return psiClass != null && PsiUtil.hasDefaultConstructor(psiClass, true, false);

  }

}
