/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.codeInspection.changeToOperator.transformations;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtil.ImmutableMapBuilder;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;

import java.util.Map;

import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.*;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.HardcodedGroovyMethodConstants.*;

public class Transformations {
  private final ImmutableMapBuilder<String, Transformation> result = ContainerUtil.immutableMapBuilder();

  public static Map<String, Transformation> get() {
    Transformations builder = new Transformations();
    return builder.build();
  }

  private Transformations() {
    addUnary();
    addBinary();
    addCustom();
  }

  private void addUnary() {
    result.put(BITWISE_NEGATE, new UnaryTransformation(mBNOT));
    result.put(NEGATIVE, new UnaryTransformation(mMINUS));
    result.put(POSITIVE, new UnaryTransformation(mPLUS));
    result.put(NEXT, new UnaryTransformation(mINC));
    result.put(PREVIOUS, new UnaryTransformation(mDEC));

    result.put(AS_BOOLEAN, new AsBooleanTransformation());
    //result.put(CALL, new CallTransformation());
  }

  private void addBinary() {
    result.put(PLUS, new BinaryTransformation(mPLUS));
    result.put(MINUS, new BinaryTransformation(mMINUS));
    result.put(MULTIPLY, new BinaryTransformation(mSTAR));
    result.put(POWER, new BinaryTransformation(mSTAR_STAR));
    result.put(DIV, new BinaryTransformation(mDIV));
    result.put(MOD, new BinaryTransformation(mMOD));
    result.put(OR, new BinaryTransformation(mBOR));
    result.put(AND, new BinaryTransformation(mBAND));
    result.put(XOR, new BinaryTransformation(mBXOR));
    result.put(LEFT_SHIFT, new BinaryTransformation(new GroovyElementType("<<")));
    result.put(RIGHT_SHIFT, new BinaryTransformation(new GroovyElementType(">>")));
    result.put(RIGHT_SHIFT_UNSIGNED, new BinaryTransformation(new GroovyElementType(">>>")));

    result.put(AS_TYPE, new BinaryTransformation(kAS));

    result.put(IS_CASE, new IsCaseTransformation());
    result.put(EQUALS, new EqualsTransformation());
    result.put(COMPARE_TO, new CompareToTransformation());
  }

  private void addCustom() {
    Transformation getAtTransformation = new GetAtTransformation();
    result.put(GET_AT, getAtTransformation);
    result.put(PUT_AT, new PutAtTransformation(getAtTransformation));
  }

  private Map<String, Transformation> build() {
    return result.build();
  }
}
