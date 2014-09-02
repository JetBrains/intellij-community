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
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.struct.gen.FieldDescriptor;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ConstExprent extends Exprent {
  private static final HashMap<Integer, String> escapes = new HashMap<Integer, String>();

  static {
    escapes.put(new Integer(0x8), "\\b"); /* \u0008: backspace BS */
    escapes.put(new Integer(0x9), "\\t"); /* \u0009: horizontal tab HT */
    escapes.put(new Integer(0xA), "\\n"); /* \u000a: linefeed LF */
    escapes.put(new Integer(0xC), "\\f"); /* \u000c: form feed FF */
    escapes.put(new Integer(0xD), "\\r"); /* \u000d: carriage return CR */
    escapes.put(new Integer(0x22), "\\\""); /* \u0022: double quote " */
    escapes.put(new Integer(0x27), "\\\'"); /* \u0027: single quote ' */
    escapes.put(new Integer(0x5C), "\\\\"); /* \u005c: backslash \ */
  }


  private VarType consttype;

  private Object value;

  private boolean boolPermitted;

  {
    this.type = EXPRENT_CONST;
  }

  public ConstExprent(int val, boolean boolPermitted) {

    this.boolPermitted = boolPermitted;
    if (boolPermitted) {
      consttype = VarType.VARTYPE_BOOLEAN;
      if (val != 0 && val != 1) {
        consttype = consttype.copy();
        consttype.convinfo |= VarType.FALSEBOOLEAN;
      }
    }
    else {
      if (0 <= val && val <= 127) {
        consttype = VarType.VARTYPE_BYTECHAR;
      }
      else if (-128 <= val && val <= 127) {
        consttype = VarType.VARTYPE_BYTE;
      }
      else if (0 <= val && val <= 32767) {
        consttype = VarType.VARTYPE_SHORTCHAR;
      }
      else if (-32768 <= val && val <= 32767) {
        consttype = VarType.VARTYPE_SHORT;
      }
      else if (0 <= val && val <= 0xFFFF) {
        consttype = VarType.VARTYPE_CHAR;
      }
      else {
        consttype = VarType.VARTYPE_INT;
      }
    }
    value = new Integer(val);
  }

  public ConstExprent(VarType consttype, Object value) {
    this.consttype = consttype;
    this.value = value;
  }

  public Exprent copy() {
    return new ConstExprent(consttype, value);
  }

  public VarType getExprType() {
    return consttype;
  }

  public int getExprentUse() {
    return Exprent.MULTIPLE_USES | Exprent.SIDE_EFFECTS_FREE;
  }

  public List<Exprent> getAllExprents() {
    return new ArrayList<Exprent>();
  }

  public String toJava(int indent) {
    boolean literal = DecompilerContext.getOption(IFernflowerPreferences.LITERALS_AS_IS);
    boolean ascii = DecompilerContext.getOption(IFernflowerPreferences.ASCII_STRING_CHARACTERS);

    if (consttype.type != CodeConstants.TYPE_NULL && value == null) {
      return ExprProcessor.getCastTypeName(consttype);
    }
    else {
      switch (consttype.type) {
        case CodeConstants.TYPE_BOOLEAN:
          return Boolean.toString(((Integer)value).intValue() != 0);
        case CodeConstants.TYPE_CHAR:
          Integer val = (Integer)value;
          String ret = escapes.get(val);
          if (ret == null) {
            char c = (char)val.intValue();
            if (c >= 32 && c < 127 || !ascii && InterpreterUtil.isPrintableUnicode(c)) {
              ret = String.valueOf(c);
            }
            else {
              ret = InterpreterUtil.charToUnicodeLiteral(c);
            }
          }
          return "\'" + ret + "\'";
        case CodeConstants.TYPE_BYTE:
        case CodeConstants.TYPE_BYTECHAR:
        case CodeConstants.TYPE_SHORT:
        case CodeConstants.TYPE_SHORTCHAR:
        case CodeConstants.TYPE_INT:
          int ival = ((Integer)value).intValue();

          String intfield;
          if (literal) {
            return value.toString();
          }
          else if (ival == Integer.MAX_VALUE) {
            intfield = "MAX_VALUE";
          }
          else if (ival == Integer.MIN_VALUE) {
            intfield = "MIN_VALUE";
          }
          else {
            return value.toString();
          }
          return new FieldExprent(intfield, "java/lang/Integer", true, null, FieldDescriptor.INTEGER_DESCRIPTOR).toJava(0);
        case CodeConstants.TYPE_LONG:
          long lval = ((Long)value).longValue();

          String longfield;
          if (literal) {
            return value.toString() + "L";
          }
          else if (lval == Long.MAX_VALUE) {
            longfield = "MAX_VALUE";
          }
          else if (lval == Long.MIN_VALUE) {
            longfield = "MIN_VALUE";
          }
          else {
            return value.toString() + "L";
          }
          return new FieldExprent(longfield, "java/lang/Long", true, null, FieldDescriptor.LONG_DESCRIPTOR).toJava(0);
        case CodeConstants.TYPE_DOUBLE:
          double dval = ((Double)value).doubleValue();

          String doublefield;
          if (literal) {
            if (Double.isNaN(dval)) {
              return "0.0D / 0.0";
            }
            else if (dval == Double.POSITIVE_INFINITY) {
              return "1.0D / 0.0";
            }
            else if (dval == Double.NEGATIVE_INFINITY) {
              return "-1.0D / 0.0";
            }
            else {
              return value.toString() + "D";
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
            return value.toString() + "D";
          }
          return new FieldExprent(doublefield, "java/lang/Double", true, null, FieldDescriptor.DOUBLE_DESCRIPTOR).toJava(0);
        case CodeConstants.TYPE_FLOAT:
          float fval = ((Float)value).floatValue();

          String floatfield;
          if (literal) {
            if (Double.isNaN(fval)) {
              return "0.0F / 0.0";
            }
            else if (fval == Double.POSITIVE_INFINITY) {
              return "1.0F / 0.0";
            }
            else if (fval == Double.NEGATIVE_INFINITY) {
              return "-1.0F / 0.0";
            }
            else {
              return value.toString() + "F";
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
            return value.toString() + "F";
          }
          return new FieldExprent(floatfield, "java/lang/Float", true, null, FieldDescriptor.FLOAT_DESCRIPTOR).toJava(0);
        case CodeConstants.TYPE_NULL:
          return "null";
        case CodeConstants.TYPE_OBJECT:
          if (consttype.equals(VarType.VARTYPE_STRING)) {
            return "\"" + convertStringToJava(value.toString(), ascii) + "\"";
          }
          else if (consttype.equals(VarType.VARTYPE_CLASS)) {
            String strval = value.toString();

            VarType classtype;
            if (strval.startsWith("[")) { // array of simple type
              classtype = new VarType(strval, false);
            }
            else { // class
              classtype = new VarType(strval, true);
            }

            return ExprProcessor.getCastTypeName(classtype) + ".class";
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


  public boolean equals(Object o) {
    if (o == this) return true;
    if (o == null || !(o instanceof ConstExprent)) return false;

    ConstExprent cn = (ConstExprent)o;
    return InterpreterUtil.equalObjects(consttype, cn.getConsttype()) &&
           InterpreterUtil.equalObjects(value, cn.getValue());
  }

  public boolean hasBooleanValue() {

    switch (consttype.type) {
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

    switch (consttype.type) {
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
        return new ConstExprent(VarType.VARTYPE_INT, new Integer(0));
      case CodeConstants.TYPE_LONG:
        return new ConstExprent(VarType.VARTYPE_LONG, new Long(0));
      case CodeConstants.TYPE_DOUBLE:
        return new ConstExprent(VarType.VARTYPE_DOUBLE, new Double(0));
      case CodeConstants.TYPE_FLOAT:
        return new ConstExprent(VarType.VARTYPE_FLOAT, new Float(0));
    }

    throw new RuntimeException("Invalid argument!");
  }

  public VarType getConsttype() {
    return consttype;
  }

  public void setConsttype(VarType consttype) {
    this.consttype = consttype;
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

  public void setBoolPermitted(boolean boolPermitted) {
    this.boolPermitted = boolPermitted;
  }
}
