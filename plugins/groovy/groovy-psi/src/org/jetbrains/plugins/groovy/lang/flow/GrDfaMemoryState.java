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
package org.jetbrains.plugins.groovy.lang.flow;

import com.intellij.codeInspection.dataFlow.DfaMemoryStateImpl;
import com.intellij.codeInspection.dataFlow.DfaVariableState;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.psi.CommonClassNames;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

public class GrDfaMemoryState extends DfaMemoryStateImpl {

  public GrDfaMemoryState(DfaValueFactory factory) {
    super(factory);
  }

  protected GrDfaMemoryState(DfaMemoryStateImpl toCopy) {
    super(toCopy);
  }

  @Override
  public GrDfaMemoryState createCopy() {
    return new GrDfaMemoryState(this);
  }

  public boolean coerceTo(boolean to, DfaValue value) {
    if (value instanceof DfaConstValue) {
      return coercesTo(to, (DfaConstValue)value);
    }
    else if (value instanceof DfaBoxedValue) {
      return coerceTo(to, ((DfaBoxedValue)value).getWrappedValue());
    }
    else if (value instanceof DfaTypeValue) {
      return to ^ !((DfaTypeValue)value).isNotNull();
    }
    else if (value instanceof DfaVariableValue) {
      final DfaConstValue constantValue = getConstantValue((DfaVariableValue)value);
      if (constantValue != null) {
        return coerceTo(to, constantValue);
      }

      List<DfaValue> equivalentValues = getEquivalentValues(value);
      if (equivalentValues.isEmpty()) equivalentValues = Collections.singletonList(value);
      for (DfaValue equivalent : equivalentValues) {
        if (equivalent instanceof DfaVariableValue) {
          final DfaVariableValue variableValue = (DfaVariableValue)equivalent;
          applyBooleanRelations(to, variableValue);
          final GrDfaVariableState newState = getVariableState(variableValue).withTruth(
            to ? GrDfaVariableState.Truth.TRUE
               : GrDfaVariableState.Truth.FALSE
          );
          if (newState == null) return false;
          setVariableState(variableValue, newState);
        }
      }
    }
    return true;
  }

  private void applyBooleanRelations(boolean to, @NotNull DfaVariableValue variableValue) {
    final DfaPsiType type = variableValue.getTypeValue() != null ? variableValue.getTypeValue().getDfaType() : null;
    if (type != null && type.getPsiType().equalsToText(CommonClassNames.JAVA_LANG_BOOLEAN)) {
      final boolean notNull = isNotNull(variableValue);
      final DfaConstValue.Factory constFactory = myFactory.getConstFactory();
      final DfaRelationValue.Factory relationFactory = myFactory.getRelationFactory();
      final DfaValue dfaTrue = myFactory.getBoxedFactory().createBoxed(
        constFactory.getTrue()
      );
      applyRelationCondition(
        relationFactory.createRelation(variableValue, dfaTrue, DfaRelation.EQ, !to)
      );
      if (notNull) {
        final DfaValue dfaFalse = myFactory.getBoxedFactory().createBoxed(
          constFactory.getFalse()
        );
        applyRelationCondition(
          relationFactory.createRelation(variableValue, dfaFalse, DfaRelation.EQ, to)
        );
      }
    }
  }

  @Override
  public GrDfaVariableState getVariableState(DfaVariableValue dfaVar) {
    return (GrDfaVariableState)super.getVariableState(dfaVar);
  }

  @Override
  protected DfaVariableState createVariableState(DfaVariableValue var) {
    return new GrDfaVariableState(var);
  }

  private final static Object[] FALSE_SET = new Object[]{
    null,
    0,
    0l,
    0d,
    0f,
    BigInteger.ZERO,
    BigDecimal.ZERO,
    Boolean.FALSE,
  };

  public static boolean coercesTo(boolean to, DfaConstValue value) {
    final Object o = value.getValue();
    return to ^ ArrayUtil.contains(o, FALSE_SET);
  }
}
