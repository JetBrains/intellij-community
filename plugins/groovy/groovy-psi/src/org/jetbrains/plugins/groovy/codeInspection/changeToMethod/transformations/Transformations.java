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

import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kIN;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mBAND;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mBNOT;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mBOR;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mBXOR;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mCOMPARE_TO;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mDEC;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mDIV;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mEQUAL;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mGE;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mGT;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mINC;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mLE;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mLT;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mMINUS;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mMOD;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mNOT_EQUAL;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mPLUS;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mSTAR;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mSTAR_STAR;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.COMPOSITE_LSHIFT_SIGN;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.COMPOSITE_RSHIFT_SIGN;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.COMPOSITE_TRIPLE_SHIFT_SIGN;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.HardcodedGroovyMethodConstants.AND;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.HardcodedGroovyMethodConstants.BITWISE_NEGATE;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.HardcodedGroovyMethodConstants.DIV;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.HardcodedGroovyMethodConstants.EQUALS;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.HardcodedGroovyMethodConstants.LEFT_SHIFT;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.HardcodedGroovyMethodConstants.MINUS;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.HardcodedGroovyMethodConstants.MOD;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.HardcodedGroovyMethodConstants.MULTIPLY;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.HardcodedGroovyMethodConstants.NEGATIVE;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.HardcodedGroovyMethodConstants.NEXT;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.HardcodedGroovyMethodConstants.OR;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.HardcodedGroovyMethodConstants.PLUS;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.HardcodedGroovyMethodConstants.POSITIVE;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.HardcodedGroovyMethodConstants.POWER;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.HardcodedGroovyMethodConstants.PREVIOUS;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.HardcodedGroovyMethodConstants.RIGHT_SHIFT;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.HardcodedGroovyMethodConstants.RIGHT_SHIFT_UNSIGNED;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.HardcodedGroovyMethodConstants.XOR;

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
