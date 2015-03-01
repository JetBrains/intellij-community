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
import org.jetbrains.java.decompiler.struct.match.IMatchable.MatchProperties;
import org.jetbrains.java.decompiler.struct.match.MatchNode.RuleValue;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

import java.util.*;
import java.util.Map.Entry;

public class ConstExprent extends Exprent {
  private static final Map<Integer, String> ESCAPES = new HashMap<Integer, String>() {{
    put(new Integer(0x8), "\\b");   /* \u0008: backspace BS */
    put(new Integer(0x9), "\\t");   /* \u0009: horizontal tab HT */
    put(new Integer(0xA), "\\n");   /* \u000a: linefeed LF */
    put(new Integer(0xC), "\\f");   /* \u000c: form feed FF */
    put(new Integer(0xD), "\\r");   /* \u000d: carriage return CR */
    put(new Integer(0x22), "\\\""); /* \u0022: double quote " */
    put(new Integer(0x27), "\\\'"); /* \u0027: single quote ' */
    put(new Integer(0x5C), "\\\\"); /* \u005c: backslash \ */
  }};

  private VarType constType;
  private final Object value;
  private final boolean boolPermitted;

  public ConstExprent(int val, boolean boolPermitted, Set<Integer> bytecodeOffsets) {
    this(guessType(val, boolPermitted), new Integer(val), boolPermitted, bytecodeOffsets);
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
    return new ArrayList<Exprent>();
  }

  @Override
  public TextBuffer toJava(int indent, BytecodeMappingTracer tracer) {
    boolean literal = DecompilerContext.getOption(IFernflowerPreferences.LITERALS_AS_IS);
    boolean ascii = DecompilerContext.getOption(IFernflowerPreferences.ASCII_STRING_CHARACTERS);

    tracer.addMapping(bytecode);

    if (constType.type != CodeConstants.TYPE_NULL && value == null) {
      return new TextBuffer(ExprProcessor.getCastTypeName(constType));
    }
    else {
      switch (constType.type) {
        case CodeConstants.TYPE_BOOLEAN:
          return new TextBuffer(Boolean.toString(((Integer)value).intValue() != 0));
        case CodeConstants.TYPE_CHAR:
          Integer val = (Integer)value;
          String ret = ESCAPES.get(val);
          if (ret == null) {
            char c = (char)val.intValue();
            if (c >= 32 && c < 127 || !ascii && InterpreterUtil.isPrintableUnicode(c)) {
              ret = String.valueOf(c);
            }
            else {
              ret = InterpreterUtil.charToUnicodeLiteral(c);
            }
          }
          return new TextBuffer(ret).enclose("\'", "\'");
        case CodeConstants.TYPE_BYTE:
        case CodeConstants.TYPE_BYTECHAR:
        case CodeConstants.TYPE_SHORT:
        case CodeConstants.TYPE_SHORTCHAR:
        case CodeConstants.TYPE_INT:
          int ival = ((Integer)value).intValue();

          String intfield;
          if (literal) {
            return new TextBuffer(value.toString());
          }
          else if (ival == Integer.MAX_VALUE) {
            intfield = "MAX_VALUE";
          }
          else if (ival == Integer.MIN_VALUE) {
            intfield = "MIN_VALUE";
          }
          else {
            return new TextBuffer(value.toString());
          }
          return new FieldExprent(intfield, "java/lang/Integer", true, null, FieldDescriptor.INTEGER_DESCRIPTOR, bytecode).toJava(0, tracer);
        case CodeConstants.TYPE_LONG:
          long lval = ((Long)value).longValue();

          String longfield;
          if (literal) {
            return new TextBuffer(value.toString()).append("L");
          }
          else if (lval == Long.MAX_VALUE) {
            longfield = "MAX_VALUE";
          }
          else if (lval == Long.MIN_VALUE) {
            longfield = "MIN_VALUE";
          }
          else {
            return new TextBuffer(value.toString()).append("L");
          }
          return new FieldExprent(longfield, "java/lang/Long", true, null, FieldDescriptor.LONG_DESCRIPTOR, bytecode).toJava(0, tracer);
        case CodeConstants.TYPE_DOUBLE:
          double dval = ((Double)value).doubleValue();

          String doublefield;
          if (literal) {
            if (Double.isNaN(dval)) {
              return new TextBuffer("0.0D / 0.0");
            }
            else if (dval == Double.POSITIVE_INFINITY) {
              return new TextBuffer("1.0D / 0.0");
            }
            else if (dval == Double.NEGATIVE_INFINITY) {
              return new TextBuffer("-1.0D / 0.0");
            }
            else {
              return new TextBuffer(value.toString()).append("D");
            }
          }
          else if (Double.isNaN(dval)) {
            doublefield = "NaN";
          }
          else if (dval == Double.POSITIVE_INFINITY) {
            doublefield = "POSITIVE_INFINITY";
          }
          else if (dval == Double.NEGATIVE_INFINITY) {
            doublefield = "NEGATIVE_INFINITY";
          }
          else if (dval == Double.MAX_VALUE) {
            doublefield = "MAX_VALUE";
          }
          else if (dval == Double.MIN_VALUE) {
            doublefield = "MIN_VALUE";
          }
          else {
            return new TextBuffer(value.toString()).append("D");
          }
          return new FieldExprent(doublefield, "java/lang/Double", true, null, FieldDescriptor.DOUBLE_DESCRIPTOR, bytecode).toJava(0, tracer);
        case CodeConstants.TYPE_FLOAT:
          float fval = ((Float)value).floatValue();

          String floatfield;
          if (literal) {
            if (Double.isNaN(fval)) {
              return new TextBuffer("0.0F / 0.0");
            }
            else if (fval == Double.POSITIVE_INFINITY) {
              return new TextBuffer("1.0F / 0.0");
            }
            else if (fval == Double.NEGATIVE_INFINITY) {
              return new TextBuffer("-1.0F / 0.0");
            }
            else {
              return new TextBuffer(value.toString()).append("F");
            }
          }
          else if (Float.isNaN(fval)) {
            floatfield = "NaN";
          }
          else if (fval == Float.POSITIVE_INFINITY) {
            floatfield = "POSITIVE_INFINITY";
          }
          else if (fval == Float.NEGATIVE_INFINITY) {
            floatfield = "NEGATIVE_INFINITY";
          }
          else if (fval == Float.MAX_VALUE) {
            floatfield = "MAX_VALUE";
          }
          else if (fval == Float.MIN_VALUE) {
            floatfield = "MIN_VALUE";
          }
          else {
            return new TextBuffer(value.toString()).append("F");
          }
          return new FieldExprent(floatfield, "java/lang/Float", true, null, FieldDescriptor.FLOAT_DESCRIPTOR, bytecode).toJava(0, tracer);
        case CodeConstants.TYPE_NULL:
          return new TextBuffer("null");
        case CodeConstants.TYPE_OBJECT:
          if (constType.equals(VarType.VARTYPE_STRING)) {
            return new TextBuffer(convertStringToJava(value.toString(), ascii)).enclose("\"", "\"");
          }
          else if (constType.equals(VarType.VARTYPE_CLASS)) {
            String strval = value.toString();

            VarType classtype;
            if (strval.startsWith("[")) { // array of simple type
              classtype = new VarType(strval, false);
            }
            else { // class
              classtype = new VarType(strval, true);
            }

            return new TextBuffer(ExprProcessor.getCastTypeName(classtype)).append(".class");
          }
      }
    }

    throw new RuntimeException("invalid constant type");
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
        case 0x27: //"\\\\'");  // u0027: single quote '
          buffer.append("\\\'");
          break;
        default:
          if (c >= 32 && c < 127 || !ascii && InterpreterUtil.isPrintableUnicode(c)) {
            buffer.append(c);
          }
          else {
            buffer.append(InterpreterUtil.charToUnicodeLiteral(c));
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

  public boolean hasBooleanValue() {
    switch (constType.type) {
      case CodeConstants.TYPE_BOOLEAN:
      case CodeConstants.TYPE_CHAR:
      case CodeConstants.TYPE_BYTE:
      case CodeConstants.TYPE_BYTECHAR:
      case CodeConstants.TYPE_SHORT:
      case CodeConstants.TYPE_SHORTCHAR:
      case CodeConstants.TYPE_INT:
        Integer ival = (Integer)value;
        return ival.intValue() == 0 ||
               (DecompilerContext.getOption(IFernflowerPreferences.BOOLEAN_TRUE_ONE) && ival.intValue() == 1);
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
        return new ConstExprent(VarType.VARTYPE_INT, new Integer(0), null);
      case CodeConstants.TYPE_LONG:
        return new ConstExprent(VarType.VARTYPE_LONG, new Long(0), null);
      case CodeConstants.TYPE_DOUBLE:
        return new ConstExprent(VarType.VARTYPE_DOUBLE, new Double(0), null);
      case CodeConstants.TYPE_FLOAT:
        return new ConstExprent(VarType.VARTYPE_FLOAT, new Float(0), null);
    }

    throw new RuntimeException("Invalid argument!");
  }

  public VarType getConstType() {
    return constType;
  }

  public void setConstType(VarType constType) {
    this.constType = constType;
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
  
  public boolean match(MatchNode matchNode, MatchEngine engine) {

    if(!super.match(matchNode, engine)) {
      return false;
    }
    
    for(Entry<MatchProperties, RuleValue> rule : matchNode.getRules().entrySet()) {
      RuleValue rule_value = rule.getValue();
      
      switch(rule.getKey()) {
      case EXPRENT_CONSTTYPE:
        if(!rule_value.value.equals(this.constType)) {
          return false;
        }
        break;
      case EXPRENT_CONSTVALUE:
        if(rule_value.isVariable()) {
          if(!engine.checkAndSetVariableValue(rule_value.value.toString(), this.value)) {
            return false;
          }
        } 
        break;
      }
    }
    
    return true;
  }
  
}
