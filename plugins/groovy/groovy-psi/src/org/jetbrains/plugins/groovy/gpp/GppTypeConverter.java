// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.gpp;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.findUsages.LiteralConstructorReference;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrSignature;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrTypeConverter;

import java.util.List;

import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil.canAssign;
import static org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.isInMethodCallContext;

/**
 * @author peter
 */
public class GppTypeConverter extends GrTypeConverter {

  @Override
  public boolean isApplicableTo(@NotNull Position position) {
    return position == Position.ASSIGNMENT;
  }

  @Nullable
  @Override
  public ConversionResult isConvertible(@NotNull PsiType targetType,
                                        @NotNull PsiType actualType,
                                        @NotNull Position position,
                                        @NotNull GroovyPsiElement context) {
    if (context instanceof GrListOrMap &&
        context.getReference() instanceof LiteralConstructorReference &&
        ((LiteralConstructorReference)context.getReference()).getConstructedClassType() != null) return null;

    if (actualType instanceof GrTupleType) {
      final GrTupleType tupleType = (GrTupleType)actualType;

      final PsiType expectedComponent = PsiUtil.extractIterableTypeParameter(targetType, false);
      if (expectedComponent != null && isInMethodCallContext(context)) {
        PsiType[] parameters = tupleType.getParameters();
        if (parameters.length == 1) {
          PsiType tupleComponent = parameters[0];
          if (tupleComponent != null &&
              canAssign(expectedComponent, tupleComponent, context, Position.ASSIGNMENT) == ConversionResult.OK &&
              hasDefaultConstructor(targetType)) {
            return ConversionResult.OK;
          }
        }
      }
    }

    return null;
  }

  public static boolean isClosureOverride(PsiType[] methodParameters, GrClosureType closureType, GroovyPsiElement context) {
    final List<GrSignature> signature = closureType.getSignatures();
    if (methodParameters != null && GrClosureSignatureUtil.isSignatureApplicable(signature, methodParameters, context)) {
      return true;
    }
    return false;
  }

  public static boolean hasDefaultConstructor(PsiType type) {
    final PsiClass psiClass = PsiUtil.resolveClassInType(type);
    return psiClass != null && PsiUtil.hasDefaultConstructor(psiClass, true, true);
  }
}
