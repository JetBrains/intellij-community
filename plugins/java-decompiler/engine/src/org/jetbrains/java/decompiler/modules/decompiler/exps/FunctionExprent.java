/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.java.decompiler.modules.decompiler.exps;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.collectors.BytecodeMappingTracer;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.vars.CheckTypesResult;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.struct.match.MatchEngine;
import org.jetbrains.java.decompiler.struct.match.MatchNode;
import org.jetbrains.java.decompiler.util.InterpreterUtil;
import org.jetbrains.java.decompiler.util.ListStack;
import org.jetbrains.java.decompiler.util.TextBuffer;

import java.util.*;

public class FunctionExprent extends Exprent {

  public static final int FUNCTION_ADD = 0;
  public static final int FUNCTION_SUB = 1;
  public static final int FUNCTION_MUL = 2;
  public static final int FUNCTION_DIV = 3;

  public static final int FUNCTION_AND = 4;
  public static final int FUNCTION_OR = 5;
  public static final int FUNCTION_XOR = 6;

  public static final int FUNCTION_REM = 7;

  public static final int FUNCTION_SHL = 8;
  public static final int FUNCTION_SHR = 9;
  public static final int FUNCTION_USHR = 10;

  public static final int FUNCTION_BIT_NOT = 11;
  public static final int FUNCTION_BOOL_NOT = 12;
  public static final int FUNCTION_NEG = 13;

  public final static int FUNCTION_I2L = 14;
  public final static int FUNCTION_I2F = 15;
  public final static int FUNCTION_I2D = 16;
  public final static int FUNCTION_L2I = 17;
  public final static int FUNCTION_L2F = 18;
  public final static int FUNCTION_L2D = 19;
  public final static int FUNCTION_F2I = 20;
  public final static int FUNCTION_F2L = 21;
  public final static int FUNCTION_F2D = 22;
  public final static int FUNCTION_D2I = 23;
  public final static int FUNCTION_D2L = 24;
  public final static int FUNCTION_D2F = 25;
  public final static int FUNCTION_I2B = 26;
  public final static int FUNCTION_I2C = 27;
  public final static int FUNCTION_I2S = 28;

  public final static int FUNCTION_CAST = 29;
  public final static int FUNCTION_INSTANCEOF = 30;

  public final static int FUNCTION_ARRAY_LENGTH = 31;

  public final static int FUNCTION_IMM = 32;
  public final static int FUNCTION_MMI = 33;

  public final static int FUNCTION_IPP = 34;
  public final static int FUNCTION_PPI = 35;

  public final static int FUNCTION_IIF = 36;

  public final static int FUNCTION_LCMP = 37;
  public final static int FUNCTION_FCMPL = 38;
  public final static int FUNCTION_FCMPG = 39;
  public final static int FUNCTION_DCMPL = 40;
  public final static int FUNCTION_DCMPG = 41;

  public static final int FUNCTION_EQ = 42;
  public static final int FUNCTION_NE = 43;
  public static final int FUNCTION_LT = 44;
  public static final int FUNCTION_GE = 45;
  public static final int FUNCTION_GT = 46;
  public static final int FUNCTION_LE = 47;

  public static final int FUNCTION_CADD = 48;
  public static final int FUNCTION_COR = 49;

  public static final int FUNCTION_STR_CONCAT = 50;

  private static final VarType[] TYPES = {
    VarType.VARTYPE_LONG,
    VarType.VARTYPE_FLOAT,
    VarType.VARTYPE_DOUBLE,
    VarType.VARTYPE_INT,
    VarType.VARTYPE_FLOAT,
    VarType.VARTYPE_DOUBLE,
    VarType.VARTYPE_INT,
    VarType.VARTYPE_LONG,
    VarType.VARTYPE_DOUBLE,
    VarType.VARTYPE_INT,
    VarType.VARTYPE_LONG,
    VarType.VARTYPE_FLOAT,
    VarType.VARTYPE_BYTE,
    VarType.VARTYPE_CHAR,
    VarType.VARTYPE_SHORT
  };

  private static final String[] OPERATORS = {
    " + ",
    " - ",
    " * ",
    " / ",
    " & ",
    " | ",
    " ^ ",
    " % ",
    " << ",
    " >> ",
    " >>> ",
    " == ",
    " != ",
    " < ",
    " >= ",
    " > ",
    " <= ",
    " && ",
    " || ",
    " + "
  };

  private static final int[] PRECEDENCE = {
    3,   // FUNCTION_ADD
    3,   // FUNCTION_SUB
    2,   // FUNCTION_MUL
    2,   // FUNCTION_DIV
    7,   // FUNCTION_AND
    9,   // FUNCTION_OR
    8,   // FUNCTION_XOR
    2,   // FUNCTION_REM
    4,   // FUNCTION_SHL
    4,   // FUNCTION_SHR
    4,   // FUNCTION_USHR
    1,   // FUNCTION_BIT_NOT
    1,   // FUNCTION_BOOL_NOT
    1,   // FUNCTION_NEG
    1,   // FUNCTION_I2L
    1,   // FUNCTION_I2F
    1,   // FUNCTION_I2D
    1,   // FUNCTION_L2I
    1,   // FUNCTION_L2F
    1,   // FUNCTION_L2D
    1,   // FUNCTION_F2I
    1,   // FUNCTION_F2L
    1,   // FUNCTION_F2D
    1,   // FUNCTION_D2I
    1,   // FUNCTION_D2L
    1,   // FUNCTION_D2F
    1,   // FUNCTION_I2B
    1,   // FUNCTION_I2C
    1,   // FUNCTION_I2S
    1,   // FUNCTION_CAST
    6,   // FUNCTION_INSTANCEOF
    0,   // FUNCTION_ARRAY_LENGTH
    1,   // FUNCTION_IMM
    1,   // FUNCTION_MMI
    1,   // FUNCTION_IPP
    1,   // FUNCTION_PPI
    12,  // FUNCTION_IFF
    -1,  // FUNCTION_LCMP
    -1,  // FUNCTION_FCMPL
    -1,  // FUNCTION_FCMPG
    -1,  // FUNCTION_DCMPL
    -1,  // FUNCTION_DCMPG
    6,   // FUNCTION_EQ = 41;
    6,   // FUNCTION_NE = 42;
    5,   // FUNCTION_LT = 43;
    5,   // FUNCTION_GE = 44;
    5,   // FUNCTION_GT = 45;
    5,   // FUNCTION_LE = 46;
    10,  // FUNCTION_CADD = 47;
    11,  // FUNCTION_COR = 48;
    3    // FUNCTION_STR_CONCAT = 49;
  };

  private static final Set<Integer> ASSOCIATIVITY = new HashSet<>(Arrays.asList(
    FUNCTION_ADD, FUNCTION_MUL, FUNCTION_AND, FUNCTION_OR, FUNCTION_XOR, FUNCTION_CADD, FUNCTION_COR, FUNCTION_STR_CONCAT));

  private int funcType;
  private VarType implicitType;
  private final List<Exprent> lstOperands;

  public FunctionExprent(int funcType, ListStack<Exprent> stack, Set<Integer> bytecodeOffsets) {
    this(funcType, new ArrayList<>(), bytecodeOffsets);

    if (funcType >= FUNCTION_BIT_NOT && funcType <= FUNCTION_PPI && funcType != FUNCTION_CAST && funcType != FUNCTION_INSTANCEOF) {
      lstOperands.add(stack.pop());
    }
    else if (funcType == FUNCTION_IIF) {
      throw new RuntimeException("no direct instantiation possible");
    }
    else {
      Exprent expr = stack.pop();
      lstOperands.add(stack.pop());
      lstOperands.add(expr);
    }
  }

  public FunctionExprent(int funcType, List<Exprent> operands, Set<Integer> bytecodeOffsets) {
    super(EXPRENT_FUNCTION);
    this.funcType = funcType;
    this.lstOperands = operands;

    addBytecodeOffsets(bytecodeOffsets);
  }

  public FunctionExprent(int funcType, Exprent operand, Set<Integer> bytecodeOffsets) {
    this(funcType, new ArrayList<>(1), bytecodeOffsets);
    lstOperands.add(operand);
  }

  @Override
  public VarType getExprType() {
    VarType exprType = null;

    if (funcType <= FUNCTION_NEG || funcType == FUNCTION_IPP || funcType == FUNCTION_PPI || funcType == FUNCTION_IMM || funcType == FUNCTION_MMI) {
      VarType type1 = lstOperands.get(0).getExprType();
      VarType type2 = null;
      if (lstOperands.size() > 1) {
        type2 = lstOperands.get(1).getExprType();
      }

      switch (funcType) {
        case FUNCTION_IMM:
        case FUNCTION_MMI:
        case FUNCTION_IPP:
        case FUNCTION_PPI:
          exprType = implicitType;
          break;
        case FUNCTION_BOOL_NOT:
          exprType = VarType.VARTYPE_BOOLEAN;
          break;
        case FUNCTION_SHL:
        case FUNCTION_SHR:
        case FUNCTION_USHR:
        case FUNCTION_BIT_NOT:
        case FUNCTION_NEG:
          exprType = getMaxVarType(new VarType[]{type1});
          break;
        case FUNCTION_ADD:
        case FUNCTION_SUB:
        case FUNCTION_MUL:
        case FUNCTION_DIV:
        case FUNCTION_REM:
          exprType = getMaxVarType(new VarType[]{type1, type2});
          break;
        case FUNCTION_AND:
        case FUNCTION_OR:
        case FUNCTION_XOR:
          if (type1.type == CodeConstants.TYPE_BOOLEAN & type2.type == CodeConstants.TYPE_BOOLEAN) {
            exprType = VarType.VARTYPE_BOOLEAN;
          }
          else {
            exprType = getMaxVarType(new VarType[]{type1, type2});
          }
      }
    }
    else if (funcType == FUNCTION_CAST) {
      exprType = lstOperands.get(1).getExprType();
    }
    else if (funcType == FUNCTION_IIF) {
      Exprent param1 = lstOperands.get(1);
      Exprent param2 = lstOperands.get(2);
      VarType supertype = VarType.getCommonSupertype(param1.getExprType(), param2.getExprType());
      if (param1.type == Exprent.EXPRENT_CONST && param2.type == Exprent.EXPRENT_CONST &&
          supertype.type != CodeConstants.TYPE_BOOLEAN && VarType.VARTYPE_INT.isSuperset(supertype)) {
        exprType = VarType.VARTYPE_INT;
      }
      else {
        exprType = supertype;
      }
    }
    else if (funcType == FUNCTION_STR_CONCAT) {
      exprType = VarType.VARTYPE_STRING;
    }
    else if (funcType >= FUNCTION_EQ || funcType == FUNCTION_INSTANCEOF) {
      exprType = VarType.VARTYPE_BOOLEAN;
    }
    else if (funcType >= FUNCTION_ARRAY_LENGTH) {
      exprType = VarType.VARTYPE_INT;
    }
    else {
      exprType = TYPES[funcType - FUNCTION_I2L];
    }

    return exprType;
  }

  @Override
  public int getExprentUse() {
    if (funcType >= FUNCTION_IMM && funcType <= FUNCTION_PPI) {
      return 0;
    }
    else {
      int ret = Exprent.MULTIPLE_USES | Exprent.SIDE_EFFECTS_FREE;
      for (Exprent expr : lstOperands) {
        ret &= expr.getExprentUse();
      }
      return ret;
    }
  }

  @Override
  public CheckTypesResult checkExprTypeBounds() {
    CheckTypesResult result = new CheckTypesResult();

    Exprent param1 = lstOperands.get(0);
    VarType type1 = param1.getExprType();
    Exprent param2 = null;
    VarType type2 = null;

    if (lstOperands.size() > 1) {
      param2 = lstOperands.get(1);
      type2 = param2.getExprType();
    }

    switch (funcType) {
      case FUNCTION_IIF:
        VarType supertype = getExprType();
        result.addMinTypeExprent(param1, VarType.VARTYPE_BOOLEAN);
        result.addMinTypeExprent(param2, VarType.getMinTypeInFamily(supertype.typeFamily));
        result.addMinTypeExprent(lstOperands.get(2), VarType.getMinTypeInFamily(supertype.typeFamily));
        break;
      case FUNCTION_I2L:
      case FUNCTION_I2F:
      case FUNCTION_I2D:
      case FUNCTION_I2B:
      case FUNCTION_I2C:
      case FUNCTION_I2S:
        result.addMinTypeExprent(param1, VarType.VARTYPE_BYTECHAR);
        result.addMaxTypeExprent(param1, VarType.VARTYPE_INT);
        break;
      case FUNCTION_IMM:
      case FUNCTION_IPP:
      case FUNCTION_MMI:
      case FUNCTION_PPI:
        result.addMinTypeExprent(param1, implicitType);
        result.addMaxTypeExprent(param1, implicitType);
        break;
      case FUNCTION_ADD:
      case FUNCTION_SUB:
      case FUNCTION_MUL:
      case FUNCTION_DIV:
      case FUNCTION_REM:
      case FUNCTION_SHL:
      case FUNCTION_SHR:
      case FUNCTION_USHR:
      case FUNCTION_LT:
      case FUNCTION_GE:
      case FUNCTION_GT:
      case FUNCTION_LE:
        result.addMinTypeExprent(param2, VarType.VARTYPE_BYTECHAR);
      case FUNCTION_BIT_NOT:
        // case FUNCTION_BOOL_NOT:
      case FUNCTION_NEG:
        result.addMinTypeExprent(param1, VarType.VARTYPE_BYTECHAR);
        break;
      case FUNCTION_AND:
      case FUNCTION_OR:
      case FUNCTION_XOR:
      case FUNCTION_EQ:
      case FUNCTION_NE: {
        if (type1.type == CodeConstants.TYPE_BOOLEAN) {
          if (type2.isStrictSuperset(type1)) {
            result.addMinTypeExprent(param1, VarType.VARTYPE_BYTECHAR);
          }
          else { // both are booleans
            boolean param1_false_boolean =
              type1.isFalseBoolean() || (param1.type == Exprent.EXPRENT_CONST && !((ConstExprent)param1).hasBooleanValue());
            boolean param2_false_boolean =
              type1.isFalseBoolean() || (param2.type == Exprent.EXPRENT_CONST && !((ConstExprent)param2).hasBooleanValue());

            if (param1_false_boolean || param2_false_boolean) {
              result.addMinTypeExprent(param1, VarType.VARTYPE_BYTECHAR);
              result.addMinTypeExprent(param2, VarType.VARTYPE_BYTECHAR);
            }
          }
        }
        else if (type2.type == CodeConstants.TYPE_BOOLEAN) {
          if (type1.isStrictSuperset(type2)) {
            result.addMinTypeExprent(param2, VarType.VARTYPE_BYTECHAR);
          }
        }
      }
    }

    return result;
  }

  @Override
  public List<Exprent> getAllExprents() {
    return new ArrayList<>(lstOperands);
  }

  @Override
  public Exprent copy() {
    List<Exprent> lst = new ArrayList<>();
    for (Exprent expr : lstOperands) {
      lst.add(expr.copy());
    }
    FunctionExprent func = new FunctionExprent(funcType, lst, bytecode);
    func.setImplicitType(implicitType);

    return func;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof FunctionExprent)) return false;

    FunctionExprent fe = (FunctionExprent)o;
    return funcType == fe.getFuncType() &&
           InterpreterUtil.equalLists(lstOperands, fe.getLstOperands()); // TODO: order of operands insignificant
  }

  @Override
  public void replaceExprent(Exprent oldExpr, Exprent newExpr) {
    for (int i = 0; i < lstOperands.size(); i++) {
      if (oldExpr == lstOperands.get(i)) {
        lstOperands.set(i, newExpr);
      }
    }
  }

  @Override
  public TextBuffer toJava(int indent, BytecodeMappingTracer tracer) {
    tracer.addMapping(bytecode);

    if (funcType <= FUNCTION_USHR) {
      return wrapOperandString(lstOperands.get(0), false, indent, tracer)
        .append(OPERATORS[funcType])
        .append(wrapOperandString(lstOperands.get(1), true, indent, tracer));
    }

      // try to determine more accurate type for 'char' literals
    if (funcType >= FUNCTION_EQ) {
      if (funcType <= FUNCTION_LE) {
        Exprent left = lstOperands.get(0);
        Exprent right = lstOperands.get(1);

        if (right.type == EXPRENT_CONST) {
          ((ConstExprent) right).adjustConstType(left.getExprType());
        }
        else if (left.type == EXPRENT_CONST) {
          ((ConstExprent) left).adjustConstType(right.getExprType());
        }
      }

      return wrapOperandString(lstOperands.get(0), false, indent, tracer)
        .append(OPERATORS[funcType - FUNCTION_EQ + 11])
        .append(wrapOperandString(lstOperands.get(1), true, indent, tracer));
    }

    switch (funcType) {
      case FUNCTION_BIT_NOT:
        return wrapOperandString(lstOperands.get(0), true, indent, tracer).prepend("~");
      case FUNCTION_BOOL_NOT:
        return wrapOperandString(lstOperands.get(0), true, indent, tracer).prepend("!");
      case FUNCTION_NEG:
        return wrapOperandString(lstOperands.get(0), true, indent, tracer).prepend("-");
      case FUNCTION_CAST:
        return lstOperands.get(1).toJava(indent, tracer).enclose("(", ")").append(wrapOperandString(lstOperands.get(0), true, indent, tracer));
      case FUNCTION_ARRAY_LENGTH:
        Exprent arr = lstOperands.get(0);

        TextBuffer res = wrapOperandString(arr, false, indent, tracer);
        if (arr.getExprType().arrayDim == 0) {
          VarType objArr = VarType.VARTYPE_OBJECT.resizeArrayDim(1); // type family does not change
          res.enclose("((" + ExprProcessor.getCastTypeName(objArr) + ")", ")");
        }
        return res.append(".length");
      case FUNCTION_IIF:
        return wrapOperandString(lstOperands.get(0), true, indent, tracer)
          .append(" ? ")
          .append(wrapOperandString(lstOperands.get(1), true, indent, tracer))
          .append(" : ")
          .append(wrapOperandString(lstOperands.get(2), true, indent, tracer));
      case FUNCTION_IPP:
        return wrapOperandString(lstOperands.get(0), true, indent, tracer).append("++");
      case FUNCTION_PPI:
        return wrapOperandString(lstOperands.get(0), true, indent, tracer).prepend("++");
      case FUNCTION_IMM:
        return wrapOperandString(lstOperands.get(0), true, indent, tracer).append("--");
      case FUNCTION_MMI:
        return wrapOperandString(lstOperands.get(0), true, indent, tracer).prepend("--");
      case FUNCTION_INSTANCEOF:
        return wrapOperandString(lstOperands.get(0), true, indent, tracer).append(" instanceof ").append(wrapOperandString(lstOperands.get(1), true, indent, tracer));
      case FUNCTION_LCMP: // shouldn't appear in the final code
        return wrapOperandString(lstOperands.get(0), true, indent, tracer).prepend("__lcmp__(")
                 .append(", ")
                 .append(wrapOperandString(lstOperands.get(1), true, indent, tracer))
                 .append(")");
      case FUNCTION_FCMPL: // shouldn't appear in the final code
        return wrapOperandString(lstOperands.get(0), true, indent, tracer).prepend("__fcmpl__(")
                 .append(", ")
                 .append(wrapOperandString(lstOperands.get(1), true, indent, tracer))
                 .append(")");
      case FUNCTION_FCMPG: // shouldn't appear in the final code
        return wrapOperandString(lstOperands.get(0), true, indent, tracer).prepend("__fcmpg__(")
                 .append(", ")
                 .append(wrapOperandString(lstOperands.get(1), true, indent, tracer))
                 .append(")");
      case FUNCTION_DCMPL: // shouldn't appear in the final code
        return wrapOperandString(lstOperands.get(0), true, indent, tracer).prepend("__dcmpl__(")
                 .append(", ")
                 .append(wrapOperandString(lstOperands.get(1), true, indent, tracer))
                 .append(")");
      case FUNCTION_DCMPG: // shouldn't appear in the final code
        return wrapOperandString(lstOperands.get(0), true, indent, tracer).prepend("__dcmpg__(")
                 .append(", ")
                 .append(wrapOperandString(lstOperands.get(1), true, indent, tracer))
                 .append(")");
    }

    if (funcType <= FUNCTION_I2S) {
      return wrapOperandString(lstOperands.get(0), true, indent, tracer).prepend("(" + ExprProcessor.getTypeName(
        TYPES[funcType - FUNCTION_I2L]) + ")");
    }

    //		return "<unknown function>";
    throw new RuntimeException("invalid function");
  }

  @Override
  public int getPrecedence() {
    return getPrecedence(funcType);
  }

  public static int getPrecedence(int func) {
    return PRECEDENCE[func];
  }

  public VarType getSimpleCastType() {
    return TYPES[funcType - FUNCTION_I2L];
  }

  private TextBuffer wrapOperandString(Exprent expr, boolean eq, int indent, BytecodeMappingTracer tracer) {
    int myprec = getPrecedence();
    int exprprec = expr.getPrecedence();

    boolean parentheses = exprprec > myprec;
    if (!parentheses && eq) {
      parentheses = (exprprec == myprec);
      if (parentheses) {
        if (expr.type == Exprent.EXPRENT_FUNCTION &&
            ((FunctionExprent)expr).getFuncType() == funcType) {
          parentheses = !ASSOCIATIVITY.contains(funcType);
        }
      }
    }

    TextBuffer res = expr.toJava(indent, tracer);

    if (parentheses) {
      res.enclose("(", ")");
    }

    return res;
  }

  private static VarType getMaxVarType(VarType[] arr) {
    int[] types = new int[]{CodeConstants.TYPE_DOUBLE, CodeConstants.TYPE_FLOAT, CodeConstants.TYPE_LONG};
    VarType[] vartypes = new VarType[]{VarType.VARTYPE_DOUBLE, VarType.VARTYPE_FLOAT, VarType.VARTYPE_LONG};

    for (int i = 0; i < types.length; i++) {
      for (int j = 0; j < arr.length; j++) {
        if (arr[j].type == types[i]) {
          return vartypes[i];
        }
      }
    }

    return VarType.VARTYPE_INT;
  }

  // *****************************************************************************
  // getter and setter methods
  // *****************************************************************************

  public int getFuncType() {
    return funcType;
  }

  public void setFuncType(int funcType) {
    this.funcType = funcType;
  }

  public List<Exprent> getLstOperands() {
    return lstOperands;
  }

  public void setImplicitType(VarType implicitType) {
    this.implicitType = implicitType;
  }

  // *****************************************************************************
  // IMatchable implementation
  // *****************************************************************************

  @Override
  public boolean match(MatchNode matchNode, MatchEngine engine) {
    if (!super.match(matchNode, engine)) {
      return false;
    }

    Integer type = (Integer)matchNode.getRuleValue(MatchProperties.EXPRENT_FUNCTYPE);
    return type == null || this.funcType == type;
  }
}