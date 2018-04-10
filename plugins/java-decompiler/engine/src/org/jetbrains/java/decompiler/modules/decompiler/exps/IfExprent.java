/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.java.decompiler.modules.decompiler.exps;

import org.jetbrains.java.decompiler.main.collectors.BytecodeMappingTracer;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.InterpreterUtil;
import org.jetbrains.java.decompiler.util.ListStack;
import org.jetbrains.java.decompiler.util.TextBuffer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class IfExprent extends Exprent {

  public static final int IF_EQ = 0;
  public static final int IF_NE = 1;
  public static final int IF_LT = 2;
  public static final int IF_GE = 3;
  public static final int IF_GT = 4;
  public static final int IF_LE = 5;

  public static final int IF_NULL = 6;
  public static final int IF_NONNULL = 7;

  public static final int IF_ICMPEQ = 8;
  public static final int IF_ICMPNE = 9;
  public static final int IF_ICMPLT = 10;
  public static final int IF_ICMPGE = 11;
  public static final int IF_ICMPGT = 12;
  public static final int IF_ICMPLE = 13;
  public static final int IF_ACMPEQ = 14;
  public static final int IF_ACMPNE = 15;

  //public static final int IF_CAND = 16;
  //public static final int IF_COR = 17;
  //public static final int IF_NOT = 18;
  public static final int IF_VALUE = 19;

  private static final int[] FUNC_TYPES = {
    FunctionExprent.FUNCTION_EQ,
    FunctionExprent.FUNCTION_NE,
    FunctionExprent.FUNCTION_LT,
    FunctionExprent.FUNCTION_GE,
    FunctionExprent.FUNCTION_GT,
    FunctionExprent.FUNCTION_LE,
    FunctionExprent.FUNCTION_EQ,
    FunctionExprent.FUNCTION_NE,
    FunctionExprent.FUNCTION_EQ,
    FunctionExprent.FUNCTION_NE,
    FunctionExprent.FUNCTION_LT,
    FunctionExprent.FUNCTION_GE,
    FunctionExprent.FUNCTION_GT,
    FunctionExprent.FUNCTION_LE,
    FunctionExprent.FUNCTION_EQ,
    FunctionExprent.FUNCTION_NE,
    FunctionExprent.FUNCTION_CADD,
    FunctionExprent.FUNCTION_COR,
    FunctionExprent.FUNCTION_BOOL_NOT,
    -1
  };

  private Exprent condition;

  public IfExprent(int ifType, ListStack<Exprent> stack, Set<Integer> bytecodeOffsets) {
    this(null, bytecodeOffsets);

    if (ifType <= IF_LE) {
      stack.push(new ConstExprent(0, true, null));
    }
    else if (ifType <= IF_NONNULL) {
      stack.push(new ConstExprent(VarType.VARTYPE_NULL, null, null));
    }

    if (ifType == IF_VALUE) {
      condition = stack.pop();
    }
    else {
      condition = new FunctionExprent(FUNC_TYPES[ifType], stack, bytecodeOffsets);
    }
  }

  private IfExprent(Exprent condition, Set<Integer> bytecodeOffsets) {
    super(EXPRENT_IF);
    this.condition = condition;

    addBytecodeOffsets(bytecodeOffsets);
  }

  @Override
  public Exprent copy() {
    return new IfExprent(condition.copy(), bytecode);
  }

  @Override
  public List<Exprent> getAllExprents() {
    List<Exprent> lst = new ArrayList<>();
    lst.add(condition);
    return lst;
  }

  @Override
  public TextBuffer toJava(int indent, BytecodeMappingTracer tracer) {
    tracer.addMapping(bytecode);
    return condition.toJava(indent, tracer).enclose("if (", ")");
  }

  @Override
  public void replaceExprent(Exprent oldExpr, Exprent newExpr) {
    if (oldExpr == condition) {
      condition = newExpr;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof IfExprent)) return false;

    IfExprent ie = (IfExprent)o;
    return InterpreterUtil.equalObjects(condition, ie.getCondition());
  }

  public IfExprent negateIf() {
    condition = new FunctionExprent(FunctionExprent.FUNCTION_BOOL_NOT, condition, condition.bytecode);
    return this;
  }

  public Exprent getCondition() {
    return condition;
  }

  public void setCondition(Exprent condition) {
    this.condition = condition;
  }
}