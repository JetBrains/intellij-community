/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.flow.value;

import com.intellij.codeInspection.dataFlow.value.DfaConstValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

import static com.intellij.codeInspection.dataFlow.value.java.DfaConstValueFactoryJava.computeJavaLangBooleanFieldReference;

public class GrDfaConstValueFactory extends DfaConstValue.Factory {

  private final GrDfaValueFactory myFactory;
  private final DfaConstValue myCoercedToTrue;
  private final DfaConstValue myCoercedToFalse;

  private class DfaCoercionValue extends DfaConstValue {

    private final boolean myTo;

    public DfaCoercionValue(GrDfaValueFactory factory, boolean to) {
      super(new Object(), factory, null);
      myTo = to;
    }

    @Override
    public DfaValue createNegated() {
      if (this == myCoercedToTrue) return myCoercedToFalse;
      if (this == myCoercedToFalse) return myCoercedToTrue;
      return super.createNegated(); // should never happen
    }

    @Override
    public String toString() {
      return "c/ " + myTo;
    }
  }

  GrDfaConstValueFactory(GrDfaValueFactory factory) {
    super(factory);
    myFactory = factory;
    myCoercedToTrue = new DfaCoercionValue(factory, true);
    myCoercedToFalse = new DfaCoercionValue(factory, false);
  }

  public DfaConstValue getCoercedToTrue() {
    return myCoercedToTrue;
  }

  public DfaValue create(GrLiteral literal) {
    return create(literal.getType(), literal.getValue());
  }

  @Override
  @Nullable
  public DfaValue create(PsiVariable variable) {
    Object value = variable.computeConstantValue();
    PsiType type = variable.getType();
    if (value == null) {
      Boolean boo = computeJavaLangBooleanFieldReference(variable);
      if (boo != null) {
        DfaConstValue unboxed = createFromValue(boo, PsiType.BOOLEAN, variable);
        return myFactory.getBoxedFactory().createBoxed(unboxed);
      }
      PsiExpression initializer = variable.getInitializer();
      if (initializer instanceof PsiLiteralExpression && initializer.textMatches(PsiKeyword.NULL)) {
        return dfaNull;
      }
      return null;
    }
    return createFromValue(value, type, variable);
  }
}
