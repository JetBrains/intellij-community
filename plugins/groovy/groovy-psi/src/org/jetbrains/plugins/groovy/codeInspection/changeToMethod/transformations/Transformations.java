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
import com.intellij.util.containers.ContainerUtil.ImmutableMapBuilder;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

import java.util.Map;

import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.*;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.COMPOSITE_LSHIFT_SIGN;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.COMPOSITE_RSHIFT_SIGN;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.COMPOSITE_TRIPLE_SHIFT_SIGN;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.HardcodedGroovyMethodConstants.*;

public interface Transformations {
  Transformation<? extends GrExpression> AS_TYPE_TRANSFORMATION = new AsTypeTransformation();

  Map<IElementType, Transformation<? extends GrExpression>> UNARY_TRANSFORMATIONS = new ImmutableMapBuilder<IElementType, Transformation<? extends GrExpression>>()
    .put(mBNOT, new UnaryTransformation(BITWISE_NEGATE))
    .put(mMINUS, new UnaryTransformation(NEGATIVE))
    .put(mPLUS, new UnaryTransformation(POSITIVE))
    .put(mINC, new UnaryTransformation(NEXT))
    .put(mDEC, new UnaryTransformation(PREVIOUS))
    .build();

  Map<IElementType, Transformation<? extends GrExpression>> BINARY_TRANSFORMATIONS = new ImmutableMapBuilder<IElementType, Transformation<? extends GrExpression>>()
    .put(mPLUS, new SimpleBinaryTransformation(PLUS))
    .put(mMINUS, new SimpleBinaryTransformation(MINUS))
    .put(mSTAR, new SimpleBinaryTransformation(MULTIPLY))
    .put(mSTAR_STAR, new SimpleBinaryTransformation(POWER))
    .put(mDIV, new SimpleBinaryTransformation(DIV))
    .put(mMOD, new SimpleBinaryTransformation(MOD))
    .put(mBOR, new SimpleBinaryTransformation(OR))
    .put(mBAND, new SimpleBinaryTransformation(AND))
    .put(mBXOR, new SimpleBinaryTransformation(XOR))
    .put(COMPOSITE_LSHIFT_SIGN, new SimpleBinaryTransformation(LEFT_SHIFT))
    .put(COMPOSITE_RSHIFT_SIGN, new SimpleBinaryTransformation(RIGHT_SHIFT))
    .put(COMPOSITE_TRIPLE_SHIFT_SIGN, new SimpleBinaryTransformation(RIGHT_SHIFT_UNSIGNED))
    .put(kIN, new IsCaseTransformation())
    .put(mEQUAL, new SimpleBinaryTransformation(EQUALS))
    .put(mNOT_EQUAL, new NotEqualTransformation())
    .put(mCOMPARE_TO, new CompareToTransformation(mCOMPARE_TO))
    .put(mGE, new CompareToTransformation(mGE))
    .put(mGT, new CompareToTransformation(mGT))
    .put(mLE, new CompareToTransformation(mLE))
    .put(mLT, new CompareToTransformation(mLT))

    .build();
}
