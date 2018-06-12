// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.changeToOperator.transformations;

import com.intellij.util.containers.ContainerUtil.ImmutableMapBuilder;

import java.util.Map;

import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.*;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.*;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.HardcodedGroovyMethodConstants.*;

public interface Transformations {

  Map<String, Transformation> TRANSFORMATIONS = new ImmutableMapBuilder<String, Transformation>()

    // unary
    .put(BITWISE_NEGATE, new UnaryTransformation(mBNOT))
    .put(NEGATIVE, new UnaryTransformation(mMINUS))
    .put(POSITIVE, new UnaryTransformation(mPLUS))
    .put(NEXT, new UnaryTransformation(mINC))
    .put(PREVIOUS, new UnaryTransformation(mDEC))
    .put(AS_BOOLEAN, new AsBooleanTransformation())

    // binary
    .put(PLUS, new SimpleBinaryTransformation(mPLUS))
    .put(MINUS, new SimpleBinaryTransformation(mMINUS))
    .put(MULTIPLY, new SimpleBinaryTransformation(mSTAR))
    .put(POWER, new SimpleBinaryTransformation(mSTAR_STAR))
    .put(DIV, new SimpleBinaryTransformation(mDIV))
    .put(MOD, new SimpleBinaryTransformation(mMOD))
    .put(OR, new SimpleBinaryTransformation(mBOR))
    .put(AND, new SimpleBinaryTransformation(mBAND))
    .put(XOR, new SimpleBinaryTransformation(mBXOR))
    .put(LEFT_SHIFT, new CompositeOperatorTransformation(COMPOSITE_LSHIFT_SIGN, "<<"))
    .put(RIGHT_SHIFT, new CompositeOperatorTransformation(COMPOSITE_RSHIFT_SIGN, ">>"))
    .put(RIGHT_SHIFT_UNSIGNED, new CompositeOperatorTransformation(COMPOSITE_TRIPLE_SHIFT_SIGN, ">>>"))
    .put(AS_TYPE, new AsTypeTransformation())
    .put(IS_CASE, new IsCaseTransformation())
    .put(EQUALS, new EqualsTransformation())
    .put(COMPARE_TO, new CompareToTransformation())

    // custom
    .put(GET_AT, new GetAtTransformation())
    .put(PUT_AT, new PutAtTransformation())

    .build();
}
