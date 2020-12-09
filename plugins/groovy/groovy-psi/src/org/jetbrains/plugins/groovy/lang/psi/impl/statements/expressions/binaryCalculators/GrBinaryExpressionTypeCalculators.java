// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.binaryCalculators;

import com.intellij.psi.PsiType;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrOperatorExpression;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.*;
import static org.jetbrains.plugins.groovy.lang.psi.util.PsiUtilKt.isFake;

public final class GrBinaryExpressionTypeCalculators {
  private static final Map<IElementType, Function<GrOperatorExpression, PsiType>> MAP = new LinkedHashMap<>();

  static {
    MAP.put(GroovyTokenTypes.mPLUS, GrNumericBinaryExpressionTypeCalculator.INSTANCE);
    MAP.put(GroovyTokenTypes.mMINUS, GrNumericBinaryExpressionTypeCalculator.INSTANCE);

    MAP.put(GroovyTokenTypes.mSTAR, GrMultiplicativeExpressionTypeCalculator.INSTANCE);
    MAP.put(GroovyTokenTypes.mDIV, GrDivExpressionTypeCalculator.INSTANCE);
    MAP.put(GroovyTokenTypes.mMOD, GrDecimalBinaryExpressionTypeCalculator.INSTANCE);

    MAP.put(GroovyTokenTypes.mSTAR_STAR, GrNumericBinaryExpressionTypeCalculator.INSTANCE);

    MAP.put(GroovyElementTypes.COMPOSITE_RSHIFT_SIGN, GrDecimalBinaryExpressionTypeCalculator.INSTANCE);
    MAP.put(GroovyElementTypes.COMPOSITE_LSHIFT_SIGN, GrDecimalBinaryExpressionTypeCalculator.INSTANCE);
    MAP.put(GroovyElementTypes.COMPOSITE_TRIPLE_SHIFT_SIGN, GrDecimalBinaryExpressionTypeCalculator.INSTANCE);

    MAP.put(GroovyTokenTypes.mBAND, GrDecimalBinaryExpressionTypeCalculator.INSTANCE);
    MAP.put(GroovyTokenTypes.mBOR, GrDecimalBinaryExpressionTypeCalculator.INSTANCE);
    MAP.put(GroovyTokenTypes.mBXOR, GrDecimalBinaryExpressionTypeCalculator.INSTANCE);
    MAP.put(GroovyTokenTypes.mCOMPARE_TO, GrIntegerTypeCalculator.INSTANCE);

    MAP.put(GroovyTokenTypes.mLOR, GrBooleanExpressionTypeCalculator.INSTANCE);
    MAP.put(GroovyTokenTypes.mLAND, GrBooleanExpressionTypeCalculator.INSTANCE);
    MAP.put(GroovyTokenTypes.mEQUAL, GrBooleanExpressionTypeCalculator.INSTANCE);
    MAP.put(GroovyTokenTypes.mNOT_EQUAL, GrBooleanExpressionTypeCalculator.INSTANCE);
    MAP.put(T_ID, GrBooleanExpressionTypeCalculator.INSTANCE);
    MAP.put(T_NID, GrBooleanExpressionTypeCalculator.INSTANCE);
    MAP.put(GroovyTokenTypes.mGT, GrBooleanExpressionTypeCalculator.INSTANCE);
    MAP.put(GroovyTokenTypes.mGE, GrBooleanExpressionTypeCalculator.INSTANCE);
    MAP.put(GroovyTokenTypes.mLT, GrBooleanExpressionTypeCalculator.INSTANCE);
    MAP.put(GroovyTokenTypes.mLE, GrBooleanExpressionTypeCalculator.INSTANCE);
    MAP.put(KW_IN, GrBooleanExpressionTypeCalculator.INSTANCE);
    MAP.put(T_NOT_IN, GrBooleanExpressionTypeCalculator.INSTANCE);

    MAP.put(GroovyTokenTypes.mREGEX_FIND, GrMatcherTypeCalculator.INSTANCE);
    MAP.put(GroovyTokenTypes.mREGEX_MATCH, GrBooleanExpressionTypeCalculator.INSTANCE);
  }

  @Nullable
  public static PsiType computeType(@NotNull GrOperatorExpression e) {
    if (isFake(e)) return null;
    final Function<GrOperatorExpression, PsiType> function = MAP.get(e.getOperator());
    assert function != null : e.getOperator();
    return function.fun(e);
  }
}
