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
package org.jetbrains.plugins.groovy.codeInspection.changeToMethod.transformations;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

import java.util.Map;

import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.*;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.*;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.HardcodedGroovyMethodConstants.*;

public interface Transformations {
  Transformation<? extends GrExpression> AS_TYPE_TRANSFORMATION = new AsTypeTransformation();

  Map<IElementType, Transformation<? extends GrExpression>> UNARY_TRANSFORMATIONS = Map.of(
    mBNOT, new UnaryTransformation(BITWISE_NEGATE),
    mMINUS, new UnaryTransformation(NEGATIVE),
    mPLUS, new UnaryTransformation(POSITIVE),
    mINC, new UnaryTransformation(NEXT),
    mDEC, new UnaryTransformation(PREVIOUS));

  Map<IElementType, Transformation<? extends GrExpression>> BINARY_TRANSFORMATIONS = Map.ofEntries(
    Map.entry(mPLUS, new SimpleBinaryTransformation(PLUS)),
    Map.entry(mMINUS, new SimpleBinaryTransformation(MINUS)),
    Map.entry(mSTAR, new SimpleBinaryTransformation(MULTIPLY)),
    Map.entry(mSTAR_STAR, new SimpleBinaryTransformation(POWER)),
    Map.entry(mDIV, new SimpleBinaryTransformation(DIV)),
    Map.entry(mMOD, new SimpleBinaryTransformation(MOD)),
    Map.entry(mBOR, new SimpleBinaryTransformation(OR)),
    Map.entry(mBAND, new SimpleBinaryTransformation(AND)),
    Map.entry(mBXOR, new SimpleBinaryTransformation(XOR)),
    Map.entry(COMPOSITE_LSHIFT_SIGN, new SimpleBinaryTransformation(LEFT_SHIFT)),
    Map.entry(COMPOSITE_RSHIFT_SIGN, new SimpleBinaryTransformation(RIGHT_SHIFT)),
    Map.entry(COMPOSITE_TRIPLE_SHIFT_SIGN, new SimpleBinaryTransformation(RIGHT_SHIFT_UNSIGNED)),
    Map.entry(kIN, new IsCaseTransformation()),
    Map.entry(mEQUAL, new SimpleBinaryTransformation(EQUALS)),
    Map.entry(mNOT_EQUAL, new NotEqualTransformation()),
    Map.entry(mCOMPARE_TO, new CompareToTransformation(mCOMPARE_TO)),
    Map.entry(mGE, new CompareToTransformation(mGE)),
    Map.entry(mGT, new CompareToTransformation(mGT)),
    Map.entry(mLE, new CompareToTransformation(mLE)),
    Map.entry(mLT, new CompareToTransformation(mLT)));
}
