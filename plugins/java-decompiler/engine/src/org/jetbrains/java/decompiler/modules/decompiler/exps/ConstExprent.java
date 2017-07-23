/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.TextBuffer;
import org.jetbrains.java.decompiler.main.collectors.BytecodeMappingTracer;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.struct.gen.FieldDescriptor;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.struct.match.MatchEngine;
import org.jetbrains.java.decompiler.struct.match.MatchNode;
import org.jetbrains.java.decompiler.struct.match.MatchNode.RuleValue;
import org.jetbrains.java.decompiler.util.InterpreterUtil;
import org.jetbrains.java.decompiler.util.TextUtil;

import java.util.*;
import java.util.Map.Entry;

public class ConstExprent extends Exprent {
  private static final Map<Integer, String> CHAR_ESCAPES;
  static {
    CHAR_ESCAPES = new HashMap<>();
    CHAR_ESCAPES.put(0x8, "\\b");   /* \u0008: backspace BS */
    CHAR_ESCAPES.put(0x9, "\\t");   /* \u0009: horizontal tab HT */
    CHAR_ESCAPES.put(0xA, "\\n");   /* \u000a: linefeed LF */
    CHAR_ESCAPES.put(0xC, "\\f");   /* \u000c: form feed FF */
    CHAR_ESCAPES.put(0xD, "\\r");   /* \u000d: carriage return CR */
    //CHAR_ESCAPES.put(0x22, "\\\""); /* \u0022: double quote " */
    CHAR_ESCAPES.put(0x27, "\\\'"); /* \u0027: single quote ' */
    CHAR_ESCAPES.put(0x5C, "\\\\"); /* \u005c: backslash \ */
  }

  private VarType constType;
  private final Object value;
  private final boolean boolPermitted;

  public ConstExprent(int val, boolean boolPermitted, Set<Integer> bytecodeOffsets) {
    this(guessType(val, boolPermitted), Integer.valueOf(val), boolPermitted, bytecodeOffsets);
  }

  public ConstExprent(VarType constType, Object value, Set<Integer> bytecodeOffsets) {
    this(constType, value, false, bytecodeOffsets);
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

  public List<Exprent> getAllExprents() {
    return new ArrayList<>();
  }

  @Override
  public TextBuffer toJava(int indent, BytecodeMappingTracer tracer) {
    boolean literal = DecompilerContext.getOption(IFernflowerPreferences.LITERALS_AS_IS);
    boolean ascii = DecompilerContext.getOption(IFernflowerPreferences.ASCII_STRING_CHARACTERS);

    tracer.addMapping(bytecode);

    if (constType.type != CodeConstants.TYPE_NULL && value == null) {
      return new TextBuffer(ExprProcessor.getCastTypeName(constType));
    }

    switch (constType.type) {
      case CodeConstants.TYPE_BOOLEAN:
        return new TextBuffer(Boolean.toString(((Integer)value).intValue() != 0));

      case CodeConstants.TYPE_CHAR:
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
        return new TextBuffer(ret).enclose("'", "'");

      case CodeConstants.TYPE_BYTE:
      case CodeConstants.TYPE_BYTECHAR:
      case CodeConstants.TYPE_SHORT:
      case CodeConstants.TYPE_SHORTCHAR:
      case CodeConstants.TYPE_INT:
        int intVal = ((Integer)value).intValue();
        if (!literal) {
          if (intVal == Integer.MAX_VALUE) {
            return new FieldExprent("MAX_VALUE", "java/lang/Integer", true, null, FieldDescriptor.INTEGER_DESCRIPTOR, bytecode).toJava(0, tracer);
          }
          else if (intVal == Integer.MIN_VALUE) {
            return new FieldExprent("MIN_VALUE", "java/lang/Integer", true, null, FieldDescriptor.INTEGER_DESCRIPTOR, bytecode).toJava(0, tracer);
          }
        }
        return new TextBuffer(value.toString());

      case CodeConstants.TYPE_LONG:
        long longVal = ((Long)value).longValue();
        if (!literal) {
          if (longVal == Long.MAX_VALUE) {
            return new FieldExprent("MAX_VALUE", "java/lang/Long", true, null, FieldDescriptor.LONG_DESCRIPTOR, bytecode).toJava(0, tracer);
          }
          else if (longVal == Long.MIN_VALUE) {
            return new FieldExprent("MIN_VALUE", "java/lang/Long", true, null, FieldDescriptor.LONG_DESCRIPTOR, bytecode).toJava(0, tracer);
          }
        }
        return new TextBuffer(value.toString()).append('L');

      case CodeConstants.TYPE_FLOAT:
        float floatVal = ((Float)value).floatValue();
        if (!literal) {
          if (Float.isNaN(floatVal)) {
            return new FieldExprent("NaN", "java/lang/Float", true, null, FieldDescriptor.FLOAT_DESCRIPTOR, bytecode).toJava(0, tracer);
          }
          else if (floatVal == Float.POSITIVE_INFINITY) {
            return new FieldExprent("POSITIVE_INFINITY", "java/lang/Float", true, null, FieldDescriptor.FLOAT_DESCRIPTOR, bytecode).toJava(0, tracer);
          }
          else if (floatVal == Float.NEGATIVE_INFINITY) {
            return new FieldExprent("NEGATIVE_INFINITY", "java/lang/Float", true, null, FieldDescriptor.FLOAT_DESCRIPTOR, bytecode).toJava(0, tracer);
          }
          else if (floatVal == Float.MAX_VALUE) {
            return new FieldExprent("MAX_VALUE", "java/lang/Float", true, null, FieldDescriptor.FLOAT_DESCRIPTOR, bytecode).toJava(0, tracer);
          }
          else if (floatVal == Float.MIN_VALUE) {
            return new FieldExprent("MIN_VALUE", "java/lang/Float", true, null, FieldDescriptor.FLOAT_DESCRIPTOR, bytecode).toJava(0, tracer);
          }
        }
        else if (Float.isNaN(floatVal)) {
          return new TextBuffer("0.0F / 0.0");
        }
        else if (floatVal == Float.POSITIVE_INFINITY) {
          return new TextBuffer("1.0F / 0.0");
        }
        else if (floatVal == Float.NEGATIVE_INFINITY) {
          return new TextBuffer("-1.0F / 0.0");
        }
        return new TextBuffer(value.toString()).append('F');

      case CodeConstants.TYPE_DOUBLE:
        double doubleVal = ((Double)value).doubleValue();
        if (!literal) {
          if (Double.isNaN(doubleVal)) {
            return new FieldExprent("NaN", "java/lang/Double", true, null, FieldDescriptor.DOUBLE_DESCRIPTOR, bytecode).toJava(0, tracer);
          }
          else if (doubleVal == Double.POSITIVE_INFINITY) {
            return new FieldExprent("POSITIVE_INFINITY", "java/lang/Double", true, null, FieldDescriptor.DOUBLE_DESCRIPTOR, bytecode).toJava(0, tracer);
          }
          else if (doubleVal == Double.NEGATIVE_INFINITY) {
            return new FieldExprent("NEGATIVE_INFINITY", "java/lang/Double", true, null, FieldDescriptor.DOUBLE_DESCRIPTOR, bytecode).toJava(0, tracer);
          }
          else if (doubleVal == Double.MAX_VALUE) {
            return new FieldExprent("MAX_VALUE", "java/lang/Double", true, null, FieldDescriptor.DOUBLE_DESCRIPTOR, bytecode).toJava(0, tracer);
          }
          else if (doubleVal == Double.MIN_VALUE) {
            return new FieldExprent("MIN_VALUE", "java/lang/Double", true, null, FieldDescriptor.DOUBLE_DESCRIPTOR, bytecode).toJava(0, tracer);
          }
        }
        else if (Double.isNaN(doubleVal)) {
          return new TextBuffer("0.0D / 0.0");
        }
        else if (doubleVal == Double.POSITIVE_INFINITY) {
          return new TextBuffer("1.0D / 0.0");
        }
        else if (doubleVal == Double.NEGATIVE_INFINITY) {
          return new TextBuffer("-1.0D / 0.0");
        }
        return new TextBuffer(value.toString()).append('D');

      case CodeConstants.TYPE_NULL:
        return new TextBuffer("null");

      case CodeConstants.TYPE_OBJECT:
        if (constType.equals(VarType.VARTYPE_STRING)) {
          return new TextBuffer(convertStringToJava(value.toString(), ascii)).enclose("\"", "\"");
        }
        else if (constType.equals(VarType.VARTYPE_CLASS)) {
          String stringVal = value.toString();
          VarType type = new VarType(stringVal, !stringVal.startsWith("["));
          return new TextBuffer(ExprProcessor.getCastTypeName(type)).append(".class");
        }
    }

    throw new RuntimeException("invalid constant type: " + constType);
  }

  private static String convertStringToJava(String value, boolean ascii) {
    char[] arr = value.toCharArray();
    StringBuilder buffer = new StringBuilder(arr.length);

    for (char c : arr) {
      switch (c) {
        case '\\': //  u005c: backslash \
          buffer.append("\\\\");
          break;
        case 0x8: // "\\\\b");  //  u0008: backspace BS
          buffer.append("\\b");
          break;
        case 0x9: //"\\\\t");  //  u0009: horizontal tab HT
          buffer.append("\\t");
          break;
        case 0xA: //"\\\\n");  //  u000a: linefeed LF
          buffer.append("\\n");
          break;
        case 0xC: //"\\\\f");  //  u000c: form feed FF
          buffer.append("\\f");
          break;
        case 0xD: //"\\\\r");  //  u000d: carriage return CR
          buffer.append("\\r");
          break;
        case 0x22: //"\\\\\""); // u0022: double quote "
          buffer.append("\\\"");
          break;
        //case 0x27: //"\\\\'");  // u0027: single quote '
        //  buffer.append("\\\'");
        //  break;
        default:
          if (isPrintableAscii(c) || !ascii && TextUtil.isPrintableUnicode(c)) {
            buffer.append(c);
          }
          else {
            buffer.append(TextUtil.charToUnicodeLiteral(c));
          }
      }
    }

    return buffer.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (o == null || !(o instanceof ConstExprent)) return false;

    ConstExprent cn = (ConstExprent)o;
    return InterpreterUtil.equalObjects(constType, cn.getConstType()) &&
           InterpreterUtil.equalObjects(value, cn.getValue());
  }

  @Override
  public int hashCode() {
    int result = constType != null ? constType.hashCode() : 0;
    result = 31 * result + (value != null ? value.hashCode() : 0);
    return result;
  }

  public boolean hasBooleanValue() {
    switch (constType.type) {
      case CodeConstants.TYPE_BOOLEAN:
      case CodeConstants.TYPE_CHAR:
      case CodeConstants.TYPE_BYTE:
      case CodeConstants.TYPE_BYTECHAR:
      case CodeConstants.TYPE_SHORT:
      case CodeConstants.TYPE_SHORTCHAR:
      case CodeConstants.TYPE_INT:
        int value = ((Integer)this.value).intValue();
        return value == 0 || (DecompilerContext.getOption(IFernflowerPreferences.BOOLEAN_TRUE_ONE) && value == 1);
    }

    return false;
  }

  public boolean hasValueOne() {
    switch (constType.type) {
      case CodeConstants.TYPE_BOOLEAN:
      case CodeConstants.TYPE_CHAR:
      case CodeConstants.TYPE_BYTE:
      case CodeConstants.TYPE_BYTECHAR:
      case CodeConstants.TYPE_SHORT:
      case CodeConstants.TYPE_SHORTCHAR:
      case CodeConstants.TYPE_INT:
        return ((Integer)value).intValue() == 1;
      case CodeConstants.TYPE_LONG:
        return ((Long)value).intValue() == 1;
      case CodeConstants.TYPE_DOUBLE:
        return ((Double)value).intValue() == 1;
      case CodeConstants.TYPE_FLOAT:
        return ((Float)value).intValue() == 1;
    }

    return false;
  }

  public static ConstExprent getZeroConstant(int type) {
    switch (type) {
      case CodeConstants.TYPE_INT:
        return new ConstExprent(VarType.VARTYPE_INT, Integer.valueOf(0), null);
      case CodeConstants.TYPE_LONG:
        return new ConstExprent(VarType.VARTYPE_LONG, Long.valueOf(0), null);
      case CodeConstants.TYPE_DOUBLE:
        return new ConstExprent(VarType.VARTYPE_DOUBLE, Double.valueOf(0), null);
      case CodeConstants.TYPE_FLOAT:
        return new ConstExprent(VarType.VARTYPE_FLOAT, Float.valueOf(0), null);
    }

    throw new RuntimeException("Invalid argument: " + type);
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
            constType.typeFamily == CodeConstants.TYPE_FAMILY_INTEGER) {
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
    return ((Integer)value).intValue();
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