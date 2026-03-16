// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.changeToOperator.transformations;

import java.util.Map;

import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mBAND;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mBNOT;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mBOR;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mBXOR;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mDEC;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mDIV;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mINC;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mMINUS;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mMOD;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mPLUS;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mSTAR;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mSTAR_STAR;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.COMPOSITE_LSHIFT_SIGN;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.COMPOSITE_RSHIFT_SIGN;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.COMPOSITE_TRIPLE_SHIFT_SIGN;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.HardcodedGroovyMethodConstants.AND;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.HardcodedGroovyMethodConstants.AS_BOOLEAN;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.HardcodedGroovyMethodConstants.AS_TYPE;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.HardcodedGroovyMethodConstants.BITWISE_NEGATE;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.HardcodedGroovyMethodConstants.COMPARE_TO;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.HardcodedGroovyMethodConstants.DIV;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.HardcodedGroovyMethodConstants.EQUALS;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.HardcodedGroovyMethodConstants.GET_AT;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.HardcodedGroovyMethodConstants.IS_CASE;
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
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.HardcodedGroovyMethodConstants.PUT_AT;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.HardcodedGroovyMethodConstants.RIGHT_SHIFT;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.HardcodedGroovyMethodConstants.RIGHT_SHIFT_UNSIGNED;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.HardcodedGroovyMethodConstants.XOR;

public interface Transformations {

  Map<String, Transformation> TRANSFORMATIONS = Map.ofEntries(

    // unary
    Map.entry(BITWISE_NEGATE, new UnaryTransformation(mBNOT)),
    Map.entry(NEGATIVE, new UnaryTransformation(mMINUS)),
    Map.entry(POSITIVE, new UnaryTransformation(mPLUS)),
    Map.entry(NEXT, new IncDecUnaryTransformation(mINC)),
    Map.entry(PREVIOUS, new IncDecUnaryTransformation(mDEC)),
    Map.entry(AS_BOOLEAN, new AsBooleanTransformation()),

    // binary
    Map.entry(PLUS, new SimpleBinaryTransformation(mPLUS)),
    Map.entry(MINUS, new SimpleBinaryTransformation(mMINUS)),
    Map.entry(MULTIPLY, new SimpleBinaryTransformation(mSTAR)),
    Map.entry(POWER, new SimpleBinaryTransformation(mSTAR_STAR)),
    Map.entry(DIV, new SimpleBinaryTransformation(mDIV)),
    Map.entry(MOD, new SimpleBinaryTransformation(mMOD)),
    Map.entry(OR, new SimpleBinaryTransformation(mBOR)),
    Map.entry(AND, new SimpleBinaryTransformation(mBAND)),
    Map.entry(XOR, new SimpleBinaryTransformation(mBXOR)),
    Map.entry(LEFT_SHIFT, new CompositeOperatorTransformation(COMPOSITE_LSHIFT_SIGN, "<<")),
    Map.entry(RIGHT_SHIFT, new CompositeOperatorTransformation(COMPOSITE_RSHIFT_SIGN, ">>")),
    Map.entry(RIGHT_SHIFT_UNSIGNED, new CompositeOperatorTransformation(COMPOSITE_TRIPLE_SHIFT_SIGN, ">>>")),
    Map.entry(AS_TYPE, new AsTypeTransformation()),
    Map.entry(IS_CASE, new IsCaseTransformation()),
    Map.entry(EQUALS, new EqualsTransformation()),
    Map.entry(COMPARE_TO, new CompareToTransformation()),

    // custom
    Map.entry(GET_AT, new GetAtTransformation()),
    Map.entry(PUT_AT, new PutAtTransformation()));
}
