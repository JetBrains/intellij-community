/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.java.decompiler.modules.decompiler.exps;

import org.jetbrains.java.decompiler.main.collectors.BytecodeMappingTracer;
import org.jetbrains.java.decompiler.modules.decompiler.vars.CheckTypesResult;
import org.jetbrains.java.decompiler.struct.consts.PooledConstant;
import org.jetbrains.java.decompiler.struct.consts.PrimitiveConstant;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.TextBuffer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.jetbrains.java.decompiler.modules.decompiler.SwitchPatternHelper.isBootstrapSwitch;

public class SwitchExprent extends Exprent {

  private Exprent value;
  private List<List<Exprent>> caseValues = new ArrayList<>();

  public SwitchExprent(Exprent value, Set<Integer> bytecodeOffsets) {
    super(EXPRENT_SWITCH);
    this.value = value;

    addBytecodeOffsets(bytecodeOffsets);
  }

  @Override
  public Exprent copy() {
    SwitchExprent swExpr = new SwitchExprent(value.copy(), bytecode);

    List<List<Exprent>> lstCaseValues = new ArrayList<>();
    for (List<Exprent> lst : caseValues) {
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
    for (List<Exprent> lst : caseValues) {
      for (Exprent expr : lst) {
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
    //if it is impossible to process
    TextBuffer buf = new TextBuffer();
    if (isBootstrapSwitch(this)) {
      InvocationExprent invocationExprent = (InvocationExprent)value;
      List<Exprent> parameters = invocationExprent.getParameters();
      if (parameters.size() == 2) {
        Exprent exprent = parameters.get(1);
        buf.append("//").append(exprent.toJava(indent, tracer)).append("->").append("value").appendLineSeparator();
        tracer.incrementCurrentSourceLine();
        List<PooledConstant> arguments = invocationExprent.getBootstrapArguments();
        for (int i = 0; i < arguments.size(); i++) {
          PooledConstant argument = arguments.get(i);
          if (argument instanceof PrimitiveConstant primitiveConstant && primitiveConstant.value != null) {
            buf.appendIndent(indent).append("//").append(i).append("->").append(primitiveConstant.value.toString()).appendLineSeparator();
            tracer.incrementCurrentSourceLine();
          }
        }
        buf.appendIndent(indent);
      }
    }
    buf.append(value.toJava(indent, tracer).enclose("switch (", ")"));
    return buf;
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

    if (!(o instanceof SwitchExprent sw)) {
      return false;
    }

    return Objects.equals(value, sw.getValue());
  }

  public Exprent getValue() {
    return value;
  }

  public void setCaseValues(List<List<Exprent>> caseValues) {
    this.caseValues = caseValues;
  }
}
