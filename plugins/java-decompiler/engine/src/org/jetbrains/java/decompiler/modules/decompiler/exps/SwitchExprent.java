/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.java.decompiler.modules.decompiler.exps;

import org.jetbrains.java.decompiler.main.TextBuffer;
import org.jetbrains.java.decompiler.main.collectors.BytecodeMappingTracer;
import org.jetbrains.java.decompiler.modules.decompiler.vars.CheckTypesResult;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SwitchExprent extends Exprent {

  private Exprent value;
  private List<List<ConstExprent>> caseValues = new ArrayList<>();

  public SwitchExprent(Exprent value, Set<Integer> bytecodeOffsets) {
    super(EXPRENT_SWITCH);
    this.value = value;

    addBytecodeOffsets(bytecodeOffsets);
  }

  @Override
  public Exprent copy() {
    SwitchExprent swExpr = new SwitchExprent(value.copy(), bytecode);

    List<List<ConstExprent>> lstCaseValues = new ArrayList<>();
    for (List<ConstExprent> lst : caseValues) {
      lstCaseValues.add(new ArrayList<>(lst));
    }
    swExpr.setCaseValues(lstCaseValues);

    return swExpr;
  }

  @Override
  public VarType getExprType() {
    return value.getExprType();
  }

  @Override
  public CheckTypesResult checkExprTypeBounds() {
    CheckTypesResult result = new CheckTypesResult();

    result.addMinTypeExprent(value, VarType.VARTYPE_BYTECHAR);
    result.addMaxTypeExprent(value, VarType.VARTYPE_INT);

    VarType valType = value.getExprType();
    for (List<ConstExprent> lst : caseValues) {
      for (ConstExprent expr : lst) {
        if (expr != null) {
          VarType caseType = expr.getExprType();
          if (!caseType.equals(valType)) {
            valType = VarType.getCommonSupertype(caseType, valType);
            result.addMinTypeExprent(value, valType);
          }
        }
      }
    }

    return result;
  }

  @Override
  public List<Exprent> getAllExprents() {
    List<Exprent> lst = new ArrayList<>();
    lst.add(value);
    return lst;
  }

  @Override
  public TextBuffer toJava(int indent, BytecodeMappingTracer tracer) {
    tracer.addMapping(bytecode);
    return value.toJava(indent, tracer).enclose("switch(", ")");
  }

  @Override
  public void replaceExprent(Exprent oldExpr, Exprent newExpr) {
    if (oldExpr == value) {
      value = newExpr;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }

    if (o == null || !(o instanceof SwitchExprent)) {
      return false;
    }

    SwitchExprent sw = (SwitchExprent)o;
    return InterpreterUtil.equalObjects(value, sw.getValue());
  }

  public Exprent getValue() {
    return value;
  }

  public void setCaseValues(List<List<ConstExprent>> caseValues) {
    this.caseValues = caseValues;
  }
}
