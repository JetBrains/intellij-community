// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.modules.decompiler.exps;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.ClassesProcessor;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.collectors.BytecodeMappingTracer;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.struct.StructField;
import org.jetbrains.java.decompiler.struct.StructMember;
import org.jetbrains.java.decompiler.struct.gen.FieldDescriptor;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.struct.match.MatchEngine;
import org.jetbrains.java.decompiler.struct.match.MatchNode;
import org.jetbrains.java.decompiler.struct.match.MatchNode.RuleValue;
import org.jetbrains.java.decompiler.util.TextBuffer;
import org.jetbrains.java.decompiler.util.TextUtil;

import java.util.*;
import java.util.Map.Entry;

public class ConstExprent extends Exprent {
  private static final String SHORT_SIG = "java/lang/Short";
  private static final String INT_SIG = "java/lang/Integer";
  private static final String LONG_SIG = "java/lang/Long";
  private static final String FLOAT_SIG = "java/lang/Float";
  private static final String DOUBLE_SIG = "java/lang/Double";
  private static final String MATH_SIG = "java/lang/Math";
  private static final String MIN_VAL = "MIN_VALUE";
  private static final String MAX_VAL = "MAX_VALUE";
  private static final String POS_INF = "POSITIVE_INFINITY";
  private static final String NEG_INF = "NEGATIVE_INFINITY";
  private static final String NAN = "NaN";
  private static final String MIN_NORM = "MIN_NORMAL";
  private static final String E = "E";
  private static final String PI = "PI";

  @SuppressWarnings("UnnecessaryUnicodeEscape")
  private static final Map<Integer, String> CHAR_ESCAPES = Map.of(
    0x8, "\\b",   /* \u0008: backspace BS */
    0x9, "\\t",   /* \u0009: horizontal tab HT */
    0xA, "\\n",   /* \u000a: linefeed LF */
    0xC, "\\f",   /* \u000c: form feed FF */
    0xD, "\\r",   /* \u000d: carriage return CR */
    0x27, "\\'",  /* \u0027: single quote ' */
    0x5C, "\\\\"  /* \u005c: backslash \ */
  );

  private StructMember parent;
  private VarType constType;
  private final Object value;
  private final boolean boolPermitted;

  public ConstExprent(int val, boolean boolPermitted, Set<Integer> bytecodeOffsets) {
    this(guessType(val, boolPermitted), val, boolPermitted, bytecodeOffsets);
  }

  public ConstExprent(VarType constType, Object value, Set<Integer> bytecodeOffsets) {
    this(constType, value, false, bytecodeOffsets);
  }

  public ConstExprent(VarType constType, Object value, Set<Integer> bytecodeOffsets, StructMember parent) {
    this(constType, value, bytecodeOffsets);
    this.parent = parent;
  }

  private ConstExprent(VarType constType, Object value, boolean boolPermitted, Set<Integer> bytecodeOffsets) {
    super(EXPRENT_CONST);
    this.constType = constType;
    this.value = value;
    this.boolPermitted = boolPermitted;
    addBytecodeOffsets(bytecodeOffsets);
  }

  private static VarType guessType(int val, boolean boolPermitted) {
    if (boolPermitted) {
      VarType constType = VarType.VARTYPE_BOOLEAN;
      if (val != 0 && val != 1) {
        constType = constType.copy(true);
      }
      return constType;
    }
    else if (0 <= val && val <= 127) {
      return VarType.VARTYPE_BYTECHAR;
    }
    else if (-128 <= val && val <= 127) {
      return VarType.VARTYPE_BYTE;
    }
    else if (0 <= val && val <= 32767) {
      return VarType.VARTYPE_SHORTCHAR;
    }
    else if (-32768 <= val && val <= 32767) {
      return VarType.VARTYPE_SHORT;
    }
    else if (0 <= val && val <= 0xFFFF) {
      return VarType.VARTYPE_CHAR;
    }
    else {
      return VarType.VARTYPE_INT;
    }
  }

  @Override
  public Exprent copy() {
    return new ConstExprent(constType, value, bytecode);
  }

  @Override
  public VarType getExprType() {
    return constType;
  }

  @Override
  public int getExprentUse() {
    return Exprent.MULTIPLE_USES | Exprent.SIDE_EFFECTS_FREE;
  }

  @Override
  public List<Exprent> getAllExprents() {
    return new ArrayList<>();
  }

  @Override
  public TextBuffer toJava(int indent, BytecodeMappingTracer tracer) {
    boolean literal = DecompilerContext.getOption(IFernflowerPreferences.LITERALS_AS_IS);
    boolean ascii = DecompilerContext.getOption(IFernflowerPreferences.ASCII_STRING_CHARACTERS);

    tracer.addMapping(bytecode);

    if (constType.getType() != CodeConstants.TYPE_NULL && value == null) {
      return new TextBuffer(ExprProcessor.getCastTypeName(constType, Collections.emptyList()));
    }

    return switch (constType.getType()) {
      case CodeConstants.TYPE_BOOLEAN -> new TextBuffer(Boolean.toString((Integer)value != 0));
      case CodeConstants.TYPE_CHAR -> {
        Integer val = (Integer)value;
        String ret = CHAR_ESCAPES.get(val);
        if (ret == null) {
          char c = (char)val.intValue();
          if (isPrintableAscii(c) || !ascii && TextUtil.isPrintableUnicode(c)) {
            ret = String.valueOf(c);
          }
          else {
            ret = TextUtil.charToUnicodeLiteral(c);
          }
        }
        yield new TextBuffer(ret).enclose("'", "'");
      }
      case CodeConstants.TYPE_BYTE -> new TextBuffer(value.toString());
      case CodeConstants.TYPE_BYTECHAR, CodeConstants.TYPE_SHORT -> {
        int shortVal = (Integer)value;
        if (!literal) {
          if (shortVal == Short.MAX_VALUE && !inConstantVariable(SHORT_SIG, MAX_VAL)) {
            yield new FieldExprent(MAX_VAL, SHORT_SIG, true, null, FieldDescriptor.SHORT_DESCRIPTOR, bytecode).toJava(0, tracer);
          }
          else if (shortVal == Short.MIN_VALUE && !inConstantVariable(SHORT_SIG, MIN_VAL)) {
            yield new FieldExprent(MIN_VAL, SHORT_SIG, true, null, FieldDescriptor.SHORT_DESCRIPTOR, bytecode).toJava(0, tracer);
          }
        }
        yield new TextBuffer(value.toString());
      }
      case CodeConstants.TYPE_SHORTCHAR, CodeConstants.TYPE_INT -> {
        int intVal = (Integer)value;
        if (!literal) {
          if (intVal == Integer.MAX_VALUE && !inConstantVariable(INT_SIG, MAX_VAL)) {
            yield new FieldExprent(MAX_VAL, INT_SIG, true, null, FieldDescriptor.INTEGER_DESCRIPTOR, bytecode).toJava(0, tracer);
          }
          else if (intVal == Integer.MIN_VALUE && !inConstantVariable(INT_SIG, MIN_VAL)) {
            yield new FieldExprent(MIN_VAL, INT_SIG, true, null, FieldDescriptor.INTEGER_DESCRIPTOR, bytecode).toJava(0, tracer);
          }
        }
        yield new TextBuffer(value.toString());
      }
      case CodeConstants.TYPE_LONG -> {
        long longVal = (Long)value;
        if (!literal) {
          if (longVal == Long.MAX_VALUE && !inConstantVariable(LONG_SIG, MAX_VAL)) {
            yield new FieldExprent(MAX_VAL, LONG_SIG, true, null, FieldDescriptor.LONG_DESCRIPTOR, bytecode).toJava(0, tracer);
          }
          else if (longVal == Long.MIN_VALUE && !inConstantVariable(LONG_SIG, MIN_VAL)) {
            yield new FieldExprent(MIN_VAL, LONG_SIG, true, null, FieldDescriptor.LONG_DESCRIPTOR, bytecode).toJava(0, tracer);
          }
        }
        yield new TextBuffer(value.toString()).append('L');
      }
      case CodeConstants.TYPE_FLOAT -> {
        float floatVal = (Float)value;
        if (!literal) {
          if (Float.isNaN(floatVal) && !inConstantVariable(FLOAT_SIG, NAN)) {
            yield new FieldExprent(NAN, FLOAT_SIG, true, null, FieldDescriptor.FLOAT_DESCRIPTOR, bytecode).toJava(0, tracer);
          }
          else if (floatVal == Float.POSITIVE_INFINITY && !inConstantVariable(FLOAT_SIG, POS_INF)) {
            yield new FieldExprent(POS_INF, FLOAT_SIG, true, null, FieldDescriptor.FLOAT_DESCRIPTOR, bytecode).toJava(0, tracer);
          }
          else if (floatVal == Float.NEGATIVE_INFINITY && !inConstantVariable(FLOAT_SIG, NEG_INF)) {
            yield new FieldExprent(NEG_INF, FLOAT_SIG, true, null, FieldDescriptor.FLOAT_DESCRIPTOR, bytecode).toJava(0, tracer);
          }
          else if (floatVal == Float.MAX_VALUE && !inConstantVariable(FLOAT_SIG, MAX_VAL)) {
            yield new FieldExprent(MAX_VAL, FLOAT_SIG, true, null, FieldDescriptor.FLOAT_DESCRIPTOR, bytecode).toJava(0, tracer);
          }
          else if (floatVal == Float.MIN_VALUE && !inConstantVariable(FLOAT_SIG, MIN_VAL)) {
            yield new FieldExprent(MIN_VAL, FLOAT_SIG, true, null, FieldDescriptor.FLOAT_DESCRIPTOR, bytecode).toJava(0, tracer);
          }
          else if (floatVal == Float.MIN_NORMAL && !inConstantVariable(FLOAT_SIG, MIN_NORM)) {
            yield new FieldExprent(MIN_NORM, FLOAT_SIG, true, null, FieldDescriptor.FLOAT_DESCRIPTOR, bytecode).toJava(0, tracer);
          }
        }
        else if (Float.isNaN(floatVal)) {
          yield new TextBuffer("0.0F / 0.0F");
        }
        else if (floatVal == Float.POSITIVE_INFINITY) {
          yield new TextBuffer("1.0F / 0.0F");
        }
        else if (floatVal == Float.NEGATIVE_INFINITY) {
          yield new TextBuffer("-1.0F / 0.0F");
        }
        yield new TextBuffer(value.toString()).append('F');
      }
      case CodeConstants.TYPE_DOUBLE -> {
        double doubleVal = (Double)value;
        if (!literal) {
          if (Double.isNaN(doubleVal) && !inConstantVariable(DOUBLE_SIG, NAN)) {
            yield new FieldExprent(NAN, DOUBLE_SIG, true, null, FieldDescriptor.DOUBLE_DESCRIPTOR, bytecode).toJava(0, tracer);
          }
          else if (doubleVal == Double.POSITIVE_INFINITY && !inConstantVariable(DOUBLE_SIG, POS_INF)) {
            yield new FieldExprent(POS_INF, DOUBLE_SIG, true, null, FieldDescriptor.DOUBLE_DESCRIPTOR, bytecode).toJava(0, tracer);
          }
          else if (doubleVal == Double.NEGATIVE_INFINITY && !inConstantVariable(DOUBLE_SIG, NEG_INF)) {
            yield new FieldExprent(NEG_INF, DOUBLE_SIG, true, null, FieldDescriptor.DOUBLE_DESCRIPTOR, bytecode).toJava(0, tracer);
          }
          else if (doubleVal == Double.MAX_VALUE && !inConstantVariable(DOUBLE_SIG, MAX_VAL)) {
            yield new FieldExprent(MAX_VAL, DOUBLE_SIG, true, null, FieldDescriptor.DOUBLE_DESCRIPTOR, bytecode).toJava(0, tracer);
          }
          else if (doubleVal == Double.MIN_VALUE && !inConstantVariable(DOUBLE_SIG, MIN_VAL)) {
            yield new FieldExprent(MIN_VAL, DOUBLE_SIG, true, null, FieldDescriptor.DOUBLE_DESCRIPTOR, bytecode).toJava(0, tracer);
          }
          else if (doubleVal == Double.MIN_NORMAL && !inConstantVariable(DOUBLE_SIG, MIN_NORM)) {
            yield new FieldExprent(MIN_NORM, DOUBLE_SIG, true, null, FieldDescriptor.DOUBLE_DESCRIPTOR, bytecode).toJava(0, tracer);
          }
          else if (doubleVal == Math.E && !inConstantVariable(MATH_SIG, E)) {
            yield new FieldExprent(E, MATH_SIG, true, null, FieldDescriptor.DOUBLE_DESCRIPTOR, bytecode).toJava(0, tracer);
          }
          else if (doubleVal == Math.PI && !inConstantVariable(MATH_SIG, PI)) {
            yield new FieldExprent(PI, MATH_SIG, true, null, FieldDescriptor.DOUBLE_DESCRIPTOR, bytecode).toJava(0, tracer);
          }
        }
        else if (Double.isNaN(doubleVal)) {
          yield new TextBuffer("0.0 / 0.0");
        }
        else if (doubleVal == Double.POSITIVE_INFINITY) {
          yield new TextBuffer("1.0 / 0.0");
        }
        else if (doubleVal == Double.NEGATIVE_INFINITY) {
          yield new TextBuffer("-1.0 / 0.0");
        }
        yield new TextBuffer(value.toString());
      }
      case CodeConstants.TYPE_NULL -> new TextBuffer("null");
      case CodeConstants.TYPE_OBJECT -> {
        if (constType.equals(VarType.VARTYPE_STRING)) {
          yield new TextBuffer(convertStringToJava(value.toString(), ascii)).enclose("\"", "\"");
        }
        else if (constType.equals(VarType.VARTYPE_CLASS)) {
          String stringVal = value.toString();
          VarType type = new VarType(stringVal, !stringVal.startsWith("["));
          yield new TextBuffer(ExprProcessor.getCastTypeName(type, Collections.emptyList())).append(".class");
        }
        throw new RuntimeException("invalid constant type: " + constType);
      }
      default -> throw new RuntimeException("invalid constant type: " + constType);
    };
  }

  private boolean inConstantVariable(String classSignature, String variableName) {
    ClassesProcessor.ClassNode node = (ClassesProcessor.ClassNode)DecompilerContext.getProperty(DecompilerContext.CURRENT_CLASS_NODE);
    return node.classStruct.qualifiedName.equals(classSignature) &&
           parent instanceof StructField &&
           ((StructField)parent).getName().equals(variableName);
  }

  public boolean isNull() {
    return CodeConstants.TYPE_NULL == constType.getType();
  }

  private static String convertStringToJava(String value, boolean ascii) {
    char[] arr = value.toCharArray();
    StringBuilder buffer = new StringBuilder(arr.length);

    for (char c : arr) {
      switch (c) {
        case '\\' -> //  u005c: backslash \
          buffer.append("\\\\");
        case 0x8 -> // "\\\\b");  //  u0008: backspace BS
          buffer.append("\\b");
        case 0x9 -> //"\\\\t");  //  u0009: horizontal tab HT
          buffer.append("\\t");
        case 0xA -> //"\\\\n");  //  u000a: linefeed LF
          buffer.append("\\n");
        case 0xC -> //"\\\\f");  //  u000c: form feed FF
          buffer.append("\\f");
        case 0xD -> //"\\\\r");  //  u000d: carriage return CR
          buffer.append("\\r");
        case 0x22 -> //"\\\\\""); // u0022: double quote "
          buffer.append("\\\"");

        default -> {
          if (isPrintableAscii(c) || !ascii && TextUtil.isPrintableUnicode(c)) {
            buffer.append(c);
          }
          else {
            buffer.append(TextUtil.charToUnicodeLiteral(c));
          }
        }
      }
    }

    return buffer.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof ConstExprent cn)) return false;

    return Objects.equals(constType, cn.getConstType()) &&
           Objects.equals(value, cn.getValue());
  }

  @Override
  public int hashCode() {
    int result = constType != null ? constType.hashCode() : 0;
    result = 31 * result + (value != null ? value.hashCode() : 0);
    return result;
  }

  public boolean hasBooleanValue() {
    switch (constType.getType()) {
      case CodeConstants.TYPE_BOOLEAN, CodeConstants.TYPE_CHAR, CodeConstants.TYPE_BYTE, CodeConstants.TYPE_BYTECHAR,
        CodeConstants.TYPE_SHORT, CodeConstants.TYPE_SHORTCHAR, CodeConstants.TYPE_INT -> {
        int value = (Integer)this.value;
        return value == 0 || (DecompilerContext.getOption(IFernflowerPreferences.BOOLEAN_TRUE_ONE) && value == 1);
      }
    }

    return false;
  }

  public boolean hasValueOne() {
    return switch (constType.getType()) {
      case CodeConstants.TYPE_BOOLEAN, CodeConstants.TYPE_CHAR, CodeConstants.TYPE_BYTE, CodeConstants.TYPE_BYTECHAR,
        CodeConstants.TYPE_SHORT, CodeConstants.TYPE_SHORTCHAR, CodeConstants.TYPE_INT ->
        (Integer)value == 1;
      case CodeConstants.TYPE_LONG -> ((Long)value).intValue() == 1;
      case CodeConstants.TYPE_DOUBLE -> ((Double)value).intValue() == 1;
      case CodeConstants.TYPE_FLOAT -> ((Float)value).intValue() == 1;
      default -> false;
    };
  }

  public static ConstExprent getZeroConstant(int type) {
    return switch (type) {
      case CodeConstants.TYPE_INT -> new ConstExprent(VarType.VARTYPE_INT, 0, null);
      case CodeConstants.TYPE_LONG -> new ConstExprent(VarType.VARTYPE_LONG, 0L, null);
      case CodeConstants.TYPE_DOUBLE -> new ConstExprent(VarType.VARTYPE_DOUBLE, 0d, null);
      case CodeConstants.TYPE_FLOAT -> new ConstExprent(VarType.VARTYPE_FLOAT, 0f, null);
      default -> throw new RuntimeException("Invalid argument: " + type);
    };
  }

  public VarType getConstType() {
    return constType;
  }

  public void setConstType(VarType constType) {
    this.constType = constType;
  }

  public void adjustConstType(VarType expectedType) {
    // BYTECHAR and SHORTCHAR => CHAR in the CHAR context
    if ((expectedType.equals(VarType.VARTYPE_CHAR) || expectedType.equals(VarType.VARTYPE_CHARACTER)) &&
            (constType.equals(VarType.VARTYPE_BYTECHAR) || constType.equals(VarType.VARTYPE_SHORTCHAR))) {
      int intValue = getIntValue();
      if (isPrintableAscii(intValue) || CHAR_ESCAPES.containsKey(intValue)) {
        setConstType(VarType.VARTYPE_CHAR);
      }
    }
    // BYTE, BYTECHAR, SHORTCHAR, SHORT, CHAR => INT in the INT context
    else if ((expectedType.equals(VarType.VARTYPE_INT) || expectedType.equals(VarType.VARTYPE_INTEGER)) &&
             constType.getTypeFamily() == CodeConstants.TYPE_FAMILY_INTEGER) {
      setConstType(VarType.VARTYPE_INT);
    }
  }

  private static boolean isPrintableAscii(int c) {
    return c >= 32 && c < 127;
  }


  public Object getValue() {
    return value;
  }

  public int getIntValue() {
    return (Integer)value;
  }

  public boolean isBoolPermitted() {
    return boolPermitted;
  }

  // *****************************************************************************
  // IMatchable implementation
  // *****************************************************************************

  @Override
  public boolean match(MatchNode matchNode, MatchEngine engine) {
    if (!super.match(matchNode, engine)) {
      return false;
    }

    for (Entry<MatchProperties, RuleValue> rule : matchNode.getRules().entrySet()) {
      RuleValue value = rule.getValue();
      MatchProperties key = rule.getKey();

      if (key == MatchProperties.EXPRENT_CONSTTYPE) {
        if (!value.value.equals(this.constType)) {
          return false;
        }
      }
      else if (key == MatchProperties.EXPRENT_CONSTVALUE) {
        if (value.isVariable() && !engine.checkAndSetVariableValue(value.value.toString(), this.value)) {
          return false;
        }
      }
    }

    return true;
  }
}