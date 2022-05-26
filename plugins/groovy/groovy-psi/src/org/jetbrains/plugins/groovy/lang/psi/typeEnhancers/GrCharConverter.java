// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

public class GrCharConverter extends GrTypeConverter {

  @Nullable
  @Override
  public ConversionResult isConvertible(@NotNull PsiType lType,
                                        @NotNull PsiType rType,
                                        @NotNull Position position,
                                        @NotNull GroovyPsiElement context) {
    if (!PsiType.CHAR.equals(TypesUtil.unboxPrimitiveTypeWrapper(lType))) return null;
    if (PsiType.CHAR.equals(TypesUtil.unboxPrimitiveTypeWrapper(rType))) return ConversionResult.OK;

    // can assign numeric types to char
    if (TypesUtil.isNumericType(rType)) {
      if (rType instanceof PsiPrimitiveType || TypesUtil.unboxPrimitiveTypeWrapper(rType) instanceof PsiPrimitiveType) {
        return PsiType.CHAR.equals(lType) ? ConversionResult.OK : ConversionResult.ERROR;
      }
      else {
        // BigDecimal && BigInteger
        return ConversionResult.ERROR;
      }
    }


    { // special case 'c = []' will throw RuntimeError
      final GrExpression rValue;
      if (context instanceof GrAssignmentExpression) {
        final GrAssignmentExpression assignmentExpression = (GrAssignmentExpression)context;
        rValue = assignmentExpression.getRValue();
      }
      else if (context instanceof GrVariable) {
        final GrVariable assignmentExpression = (GrVariable)context;
        rValue = assignmentExpression.getInitializerGroovy();
      }
      else {
        rValue = null;
      }
      if (rValue instanceof GrListOrMap && ((GrListOrMap)rValue).isEmpty()) {
        return ConversionResult.WARNING;
      }
    }

    if (PsiType.BOOLEAN.equals(TypesUtil.unboxPrimitiveTypeWrapper(rType))) {
      switch (position) {
        case ASSIGNMENT:
        case RETURN_VALUE:
          return ConversionResult.WARNING;
        default:
          return null;
      }
    }

    // one-symbol string-to-char conversion doesn't work with return value
    if (position == Position.RETURN_VALUE) {
      return null;
    }

    // can cast and assign one-symbol strings to char
    if (!TypesUtil.isClassType(rType, CommonClassNames.JAVA_LANG_STRING)) return null;

    return checkSingleSymbolLiteral(context) ? ConversionResult.OK : ConversionResult.ERROR;
  }

  public static boolean checkSingleSymbolLiteral(GroovyPsiElement context) {
    final GrLiteral literal = getLiteral(context);
    final Object value = literal == null ? null : literal.getValue();
    return value != null && value.toString().length() == 1;
  }
}
