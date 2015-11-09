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

  @Override
  public boolean isApplicableTo(@NotNull ApplicableTo position) {
    return position == ApplicableTo.EXPLICIT_CAST || position == ApplicableTo.ASSIGNMENT || position == ApplicableTo.RETURN_VALUE;
  }

  @Nullable
  @Override
  public ConversionResult isConvertibleEx(@NotNull PsiType lType,
                                          @NotNull PsiType rType,
                                          @NotNull GroovyPsiElement context,
                                          @NotNull ApplicableTo currentPosition) {
    if (!PsiType.CHAR.equals(TypesUtil.unboxPrimitiveTypeWrapper(lType))) return null;
    if (PsiType.CHAR.equals(TypesUtil.unboxPrimitiveTypeWrapper(rType))) return ConversionResult.OK;

    // can cast and assign numeric types to char
    if (TypesUtil.isNumericType(rType)) {
      if (rType instanceof PsiPrimitiveType ||
          currentPosition != ApplicableTo.EXPLICIT_CAST && TypesUtil.unboxPrimitiveTypeWrapper(rType) instanceof PsiPrimitiveType) {
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
      switch (currentPosition) {
        case EXPLICIT_CAST:
          return ConversionResult.ERROR;
        case ASSIGNMENT:
        case RETURN_VALUE:
          return ConversionResult.WARNING;
        default:
          return null;
      }
    }

    // one-symbol string-to-char conversion doesn't work with return value
    if (currentPosition == ApplicableTo.RETURN_VALUE) {
      return null;
    }

    // can cast and assign one-symbol strings to char
    if (!TypesUtil.isClassType(rType, CommonClassNames.JAVA_LANG_STRING)) return null;

    final GrLiteral literal = getLiteral(context);
    final Object value = literal == null ? null : literal.getValue();
    return value == null ? null : value.toString().length() == 1 ? ConversionResult.OK : ConversionResult.ERROR;
  }
}
