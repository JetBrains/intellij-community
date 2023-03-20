// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.changeToOperator.transformations;

import java.util.Map;

import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.*;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.*;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.HardcodedGroovyMethodConstants.*;

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
