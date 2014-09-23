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

import org.jetbrains.java.decompiler.modules.decompiler.vars.CheckTypesResult;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

import java.util.ArrayList;
import java.util.List;


public class SwitchExprent extends Exprent {

  private Exprent value;

  private List<List<ConstExprent>> caseValues = new ArrayList<List<ConstExprent>>();

  {
    this.type = EXPRENT_SWITCH;
  }

  public SwitchExprent(Exprent value) {
    this.value = value;
  }

  public Exprent copy() {
    SwitchExprent swexpr = new SwitchExprent(value.copy());

    List<List<ConstExprent>> lstCaseValues = new ArrayList<List<ConstExprent>>();
    for (List<ConstExprent> lst : caseValues) {
      lstCaseValues.add(new ArrayList<ConstExprent>(lst));
    }
    swexpr.setCaseValues(lstCaseValues);

    return swexpr;
  }

  public VarType getExprType() {
    return value.getExprType();
  }

  public CheckTypesResult checkExprTypeBounds() {
    CheckTypesResult result = new CheckTypesResult();

    result.addMinTypeExprent(value, VarType.VARTYPE_BYTECHAR);
    result.addMaxTypeExprent(value, VarType.VARTYPE_INT);

    VarType valtype = value.getExprType();
    for (List<ConstExprent> lst : caseValues) {
      for (ConstExprent expr : lst) {
        if (expr != null) {
          VarType casetype = expr.getExprType();
          if (!casetype.equals(valtype)) {
            valtype = VarType.getCommonSupertype(casetype, valtype);
            result.addMinTypeExprent(value, valtype);
          }
        }
      }
    }

    return result;
  }

  public List<Exprent> getAllExprents() {
    List<Exprent> lst = new ArrayList<Exprent>();
    lst.add(value);
    return lst;
  }

  public String toJava(int indent) {
    return "switch(" + value.toJava(indent) + ")";
  }

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

  public void replaceExprent(Exprent oldexpr, Exprent newexpr) {
    if (oldexpr == value) {
      value = newexpr;
    }
  }

  public Exprent getValue() {
    return value;
  }

  public void setCaseValues(List<List<ConstExprent>> caseValues) {
    this.caseValues = caseValues;
  }
}
