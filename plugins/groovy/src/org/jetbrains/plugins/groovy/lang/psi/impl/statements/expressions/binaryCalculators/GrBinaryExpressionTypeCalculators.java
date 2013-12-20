/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import java.util.Map;

import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.*;

/**
 * Created by Max Medvedev on 12/20/13
 */
public class GrBinaryExpressionTypeCalculators {
  private static final Map<IElementType, Function<GrBinaryFacade, PsiType>> MAP = ContainerUtil.newLinkedHashMap();

  static {

    MAP.put(mPLUS, GrNumericBinaryExpressionTypeCalculator.INSTANCE);
    MAP.put(mPLUS_ASSIGN, GrNumericBinaryExpressionTypeCalculator.INSTANCE);
    MAP.put(mMINUS, GrNumericBinaryExpressionTypeCalculator.INSTANCE);
    MAP.put(mMINUS_ASSIGN, GrNumericBinaryExpressionTypeCalculator.INSTANCE);

    MAP.put(mSTAR, GrMultiplicativeExpressionTypeCalculator.INSTANCE);
    MAP.put(mSTAR_ASSIGN, GrMultiplicativeExpressionTypeCalculator.INSTANCE);
    MAP.put(mDIV, GrDivExpressionTypeCalculator.INSTANCE);
    MAP.put(mDIV_ASSIGN, GrDivExpressionTypeCalculator.INSTANCE);
    MAP.put(mMOD, GrDecimalBinaryExpressionTypeCalculator.INSTANCE);
    MAP.put(mMOD_ASSIGN, GrDecimalBinaryExpressionTypeCalculator.INSTANCE);

    MAP.put(mSTAR_STAR, GrNumericBinaryExpressionTypeCalculator.INSTANCE);
    MAP.put(mSTAR_STAR_ASSIGN, GrNumericBinaryExpressionTypeCalculator.INSTANCE);

    MAP.put(COMPOSITE_RSHIFT_SIGN, GrDecimalBinaryExpressionTypeCalculator.INSTANCE);
    MAP.put(mSR_ASSIGN, GrDecimalBinaryExpressionTypeCalculator.INSTANCE);
    MAP.put(COMPOSITE_LSHIFT_SIGN, GrDecimalBinaryExpressionTypeCalculator.INSTANCE);
    MAP.put(mSL_ASSIGN, GrDecimalBinaryExpressionTypeCalculator.INSTANCE);
    MAP.put(COMPOSITE_TRIPLE_SHIFT_SIGN, GrDecimalBinaryExpressionTypeCalculator.INSTANCE);
    MAP.put(mBSR_ASSIGN, GrDecimalBinaryExpressionTypeCalculator.INSTANCE);

    MAP.put(mRANGE_EXCLUSIVE, GrRangeExpressionTypeCalculator.INSTANCE);
    MAP.put(mRANGE_INCLUSIVE, GrRangeExpressionTypeCalculator.INSTANCE);

    MAP.put(mBAND, GrDecimalBinaryExpressionTypeCalculator.INSTANCE);
    MAP.put(mBAND_ASSIGN, GrDecimalBinaryExpressionTypeCalculator.INSTANCE);
    MAP.put(mBOR, GrDecimalBinaryExpressionTypeCalculator.INSTANCE);
    MAP.put(mBOR_ASSIGN, GrDecimalBinaryExpressionTypeCalculator.INSTANCE);
    MAP.put(mBXOR, GrDecimalBinaryExpressionTypeCalculator.INSTANCE);
    MAP.put(mBXOR_ASSIGN, GrDecimalBinaryExpressionTypeCalculator.INSTANCE);
    MAP.put(mBAND, GrDecimalBinaryExpressionTypeCalculator.INSTANCE);
    MAP.put(mCOMPARE_TO, GrIntegerTypeCalculator.INSTANCE);

    MAP.put(mLOR, GrBooleanExpressionTypeCalculator.INSTANCE);
    MAP.put(mLAND, GrBooleanExpressionTypeCalculator.INSTANCE);
    MAP.put(mEQUAL, GrBooleanExpressionTypeCalculator.INSTANCE);
    MAP.put(mNOT_EQUAL, GrBooleanExpressionTypeCalculator.INSTANCE);
    MAP.put(mGT, GrBooleanExpressionTypeCalculator.INSTANCE);
    MAP.put(mGE, GrBooleanExpressionTypeCalculator.INSTANCE);
    MAP.put(mLT, GrBooleanExpressionTypeCalculator.INSTANCE);
    MAP.put(mLE, GrBooleanExpressionTypeCalculator.INSTANCE);
    MAP.put(kIN, GrBooleanExpressionTypeCalculator.INSTANCE);

    MAP.put(mREGEX_FIND, GrMatcherTypeCalculator.INSTANCE);
    MAP.put(mREGEX_MATCH, GrBooleanExpressionTypeCalculator.INSTANCE);

    MAP.put(mASSIGN, GrAssignTypeCalculator.INSTANCE);
  }

  @NotNull
  public static Function<GrBinaryFacade, PsiType> getTypeCalculator(GrBinaryFacade e) {
    final Function<GrBinaryFacade, PsiType> function = MAP.get(e.getOperationTokenType());
    assert function != null : e.getOperationTokenType();
    return function;
  }
}
