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
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.binaryCalculators;

import com.intellij.psi.PsiType;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrOperatorExpression;

import java.util.Map;

public class GrBinaryExpressionTypeCalculators {
  private static final Map<IElementType, Function<GrOperatorExpression, PsiType>> MAP = ContainerUtil.newLinkedHashMap();

  static {

    MAP.put(GroovyTokenTypes.mPLUS, GrNumericBinaryExpressionTypeCalculator.INSTANCE);
    MAP.put(GroovyTokenTypes.mPLUS_ASSIGN, GrNumericBinaryExpressionTypeCalculator.INSTANCE);
    MAP.put(GroovyTokenTypes.mMINUS, GrNumericBinaryExpressionTypeCalculator.INSTANCE);
    MAP.put(GroovyTokenTypes.mMINUS_ASSIGN, GrNumericBinaryExpressionTypeCalculator.INSTANCE);

    MAP.put(GroovyTokenTypes.mSTAR, GrMultiplicativeExpressionTypeCalculator.INSTANCE);
    MAP.put(GroovyTokenTypes.mSTAR_ASSIGN, GrMultiplicativeExpressionTypeCalculator.INSTANCE);
    MAP.put(GroovyTokenTypes.mDIV, GrDivExpressionTypeCalculator.INSTANCE);
    MAP.put(GroovyTokenTypes.mDIV_ASSIGN, GrDivExpressionTypeCalculator.INSTANCE);
    MAP.put(GroovyTokenTypes.mMOD, GrDecimalBinaryExpressionTypeCalculator.INSTANCE);
    MAP.put(GroovyTokenTypes.mMOD_ASSIGN, GrDecimalBinaryExpressionTypeCalculator.INSTANCE);

    MAP.put(GroovyTokenTypes.mSTAR_STAR, GrNumericBinaryExpressionTypeCalculator.INSTANCE);
    MAP.put(GroovyTokenTypes.mSTAR_STAR_ASSIGN, GrNumericBinaryExpressionTypeCalculator.INSTANCE);

    MAP.put(GroovyElementTypes.COMPOSITE_RSHIFT_SIGN, GrDecimalBinaryExpressionTypeCalculator.INSTANCE);
    MAP.put(GroovyTokenTypes.mSR_ASSIGN, GrDecimalBinaryExpressionTypeCalculator.INSTANCE);
    MAP.put(GroovyElementTypes.COMPOSITE_LSHIFT_SIGN, GrDecimalBinaryExpressionTypeCalculator.INSTANCE);
    MAP.put(GroovyTokenTypes.mSL_ASSIGN, GrDecimalBinaryExpressionTypeCalculator.INSTANCE);
    MAP.put(GroovyElementTypes.COMPOSITE_TRIPLE_SHIFT_SIGN, GrDecimalBinaryExpressionTypeCalculator.INSTANCE);
    MAP.put(GroovyTokenTypes.mBSR_ASSIGN, GrDecimalBinaryExpressionTypeCalculator.INSTANCE);

    MAP.put(GroovyTokenTypes.mRANGE_EXCLUSIVE, GrRangeExpressionTypeCalculator.INSTANCE);
    MAP.put(GroovyTokenTypes.mRANGE_INCLUSIVE, GrRangeExpressionTypeCalculator.INSTANCE);

    MAP.put(GroovyTokenTypes.mBAND, GrDecimalBinaryExpressionTypeCalculator.INSTANCE);
    MAP.put(GroovyTokenTypes.mBAND_ASSIGN, GrDecimalBinaryExpressionTypeCalculator.INSTANCE);
    MAP.put(GroovyTokenTypes.mBOR, GrDecimalBinaryExpressionTypeCalculator.INSTANCE);
    MAP.put(GroovyTokenTypes.mBOR_ASSIGN, GrDecimalBinaryExpressionTypeCalculator.INSTANCE);
    MAP.put(GroovyTokenTypes.mBXOR, GrDecimalBinaryExpressionTypeCalculator.INSTANCE);
    MAP.put(GroovyTokenTypes.mBXOR_ASSIGN, GrDecimalBinaryExpressionTypeCalculator.INSTANCE);
    MAP.put(GroovyTokenTypes.mBAND, GrDecimalBinaryExpressionTypeCalculator.INSTANCE);
    MAP.put(GroovyTokenTypes.mCOMPARE_TO, GrIntegerTypeCalculator.INSTANCE);

    MAP.put(GroovyTokenTypes.mLOR, GrBooleanExpressionTypeCalculator.INSTANCE);
    MAP.put(GroovyTokenTypes.mLAND, GrBooleanExpressionTypeCalculator.INSTANCE);
    MAP.put(GroovyTokenTypes.mEQUAL, GrBooleanExpressionTypeCalculator.INSTANCE);
    MAP.put(GroovyTokenTypes.mNOT_EQUAL, GrBooleanExpressionTypeCalculator.INSTANCE);
    MAP.put(GroovyTokenTypes.mGT, GrBooleanExpressionTypeCalculator.INSTANCE);
    MAP.put(GroovyTokenTypes.mGE, GrBooleanExpressionTypeCalculator.INSTANCE);
    MAP.put(GroovyTokenTypes.mLT, GrBooleanExpressionTypeCalculator.INSTANCE);
    MAP.put(GroovyTokenTypes.mLE, GrBooleanExpressionTypeCalculator.INSTANCE);
    MAP.put(GroovyTokenTypes.kIN, GrBooleanExpressionTypeCalculator.INSTANCE);

    MAP.put(GroovyTokenTypes.mREGEX_FIND, GrMatcherTypeCalculator.INSTANCE);
    MAP.put(GroovyTokenTypes.mREGEX_MATCH, GrBooleanExpressionTypeCalculator.INSTANCE);

    MAP.put(GroovyTokenTypes.mASSIGN, GrAssignTypeCalculator.INSTANCE);
  }

  @Nullable
  public static PsiType computeType(@NotNull GrOperatorExpression e) {
    final Function<GrOperatorExpression, PsiType> function = MAP.get(e.getOperationTokenType());
    assert function != null : e.getOperationTokenType();
    return function.fun(e);
  }
}
