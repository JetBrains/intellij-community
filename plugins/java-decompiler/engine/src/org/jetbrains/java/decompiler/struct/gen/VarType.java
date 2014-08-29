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
package org.jetbrains.java.decompiler.struct.gen;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

public class VarType {  // TODO: optimize switch

  public static final int FALSEBOOLEAN = 1;

  public static final VarType VARTYPE_UNKNOWN = new VarType(CodeConstants.TYPE_UNKNOWN);
  public static final VarType VARTYPE_INT = new VarType(CodeConstants.TYPE_INT);
  public static final VarType VARTYPE_FLOAT = new VarType(CodeConstants.TYPE_FLOAT);
  public static final VarType VARTYPE_LONG = new VarType(CodeConstants.TYPE_LONG);
  public static final VarType VARTYPE_DOUBLE = new VarType(CodeConstants.TYPE_DOUBLE);
  public static final VarType VARTYPE_BYTE = new VarType(CodeConstants.TYPE_BYTE);
  public static final VarType VARTYPE_CHAR = new VarType(CodeConstants.TYPE_CHAR);
  public static final VarType VARTYPE_SHORT = new VarType(CodeConstants.TYPE_SHORT);
  public static final VarType VARTYPE_BOOLEAN = new VarType(CodeConstants.TYPE_BOOLEAN);
  public static final VarType VARTYPE_BYTECHAR = new VarType(CodeConstants.TYPE_BYTECHAR);
  public static final VarType VARTYPE_SHORTCHAR = new VarType(CodeConstants.TYPE_SHORTCHAR);

  public static final VarType VARTYPE_NULL = new VarType(CodeConstants.TYPE_NULL, 0, null);
  public static final VarType VARTYPE_GROUP2EMPTY = new VarType(CodeConstants.TYPE_GROUP2EMPTY);
  public static final VarType VARTYPE_STRING = new VarType(CodeConstants.TYPE_OBJECT, 0, "java/lang/String");
  public static final VarType VARTYPE_CLASS = new VarType(CodeConstants.TYPE_OBJECT, 0, "java/lang/Class");
  public static final VarType VARTYPE_OBJECT = new VarType(CodeConstants.TYPE_OBJECT, 0, "java/lang/Object");
  public static final VarType VARTYPE_VOID = new VarType(CodeConstants.TYPE_VOID);

  public int type;

  public int type_family;

  public int arraydim;

  public String value;

  public int stack_size;

  public int convinfo;

  public VarType(int type) {
    this.type = type;
    this.arraydim = 0;

    value = getChar(type);
    setStackSize(type);
    setFamily();
  }

  public VarType(int type, int arraydim) {
    this(type);
    this.arraydim = arraydim;
    setFamily();
  }

  public VarType(int type, int arraydim, String value) {
    this(type);
    this.arraydim = arraydim;
    this.value = value;
    setFamily();
  }

  public VarType(String strtype) {
    this(strtype, false);
  }

  public VarType(String strtype, boolean cltype) {
    parseTypeString(strtype, cltype);
    setStackSize(type);
    setFamily();
  }

  public void decArrayDim() {
    if (arraydim > 0) {
      arraydim--;
      setFamily();
    }
    else {
      // throw new RuntimeException("array dimension equals 0!"); FIXME: investigate this case
    }
  }

  public String toString() {
    String res = "";

    for (int i = 0; i < arraydim; i++) {
      res += "[";
    }

    if (type == CodeConstants.TYPE_OBJECT) {
      res += "L" + value + ";";
    }
    else {
      res += value;
    }

    return res;
  }

  public VarType copy() {
    VarType v = new VarType(type, arraydim, value);
    v.convinfo = convinfo;
    return v;
  }

  public boolean isFalseBoolean() {
    return (convinfo & FALSEBOOLEAN) != 0;
  }

  public boolean isSuperset(VarType val) {

    return this.equals(val) || this.isStrictSuperset(val);
  }

  public boolean isStrictSuperset(VarType val) {

    int valtype = val.type;

    if (valtype == CodeConstants.TYPE_UNKNOWN && type != CodeConstants.TYPE_UNKNOWN) {
      return true;
    }

    if (val.arraydim > 0) {
      return this.equals(VARTYPE_OBJECT);
    }
    else if (arraydim > 0) {
      return (valtype == CodeConstants.TYPE_NULL);
    }

    boolean res = false;

    switch (type) {
      case CodeConstants.TYPE_INT:
        res |= (valtype == CodeConstants.TYPE_SHORT ||
                valtype == CodeConstants.TYPE_CHAR);
      case CodeConstants.TYPE_SHORT:
        res |= (valtype == CodeConstants.TYPE_BYTE);
      case CodeConstants.TYPE_CHAR:
        res |= (valtype == CodeConstants.TYPE_SHORTCHAR);
      case CodeConstants.TYPE_BYTE:
      case CodeConstants.TYPE_SHORTCHAR:
        res |= (valtype == CodeConstants.TYPE_BYTECHAR);
      case CodeConstants.TYPE_BYTECHAR:
        res |= (valtype == CodeConstants.TYPE_BOOLEAN);
        break;
      case CodeConstants.TYPE_OBJECT:
        if (valtype == CodeConstants.TYPE_NULL) {
          return true;
        }
        else if (this.equals(VARTYPE_OBJECT)) {
          return valtype == CodeConstants.TYPE_OBJECT &&
                 !val.equals(VARTYPE_OBJECT);
        }
    }

    return res;
  }

  // type1 and type2 must not be null
  public static VarType getCommonMinType(VarType type1, VarType type2) {

    if (type1.type == CodeConstants.TYPE_BOOLEAN && type2.type == CodeConstants.TYPE_BOOLEAN) { // special case booleans
      return type1.isFalseBoolean() ? type2 : type1;
    }

    if (type1.isSuperset(type2)) {
      return type2;
    }
    else if (type2.isSuperset(type1)) {
      return type1;
    }
    else if (type1.type_family == type2.type_family) {
      switch (type1.type_family) {
        case CodeConstants.TYPE_FAMILY_INTEGER:
          if ((type1.type == CodeConstants.TYPE_CHAR && type2.type == CodeConstants.TYPE_SHORT)
              || (type1.type == CodeConstants.TYPE_SHORT && type2.type == CodeConstants.TYPE_CHAR)) {
            return VARTYPE_SHORTCHAR;
          }
          else {
            return VARTYPE_BYTECHAR;
          }
        case CodeConstants.TYPE_FAMILY_OBJECT:
          return VARTYPE_NULL;
      }
    }

    return null;
  }

  // type1 and type2 must not be null
  public static VarType getCommonSupertype(VarType type1, VarType type2) {

    if (type1.type == CodeConstants.TYPE_BOOLEAN && type2.type == CodeConstants.TYPE_BOOLEAN) { // special case booleans
      return type1.isFalseBoolean() ? type1 : type2;
    }

    if (type1.isSuperset(type2)) {
      return type1;
    }
    else if (type2.isSuperset(type1)) {
      return type2;
    }
    else if (type1.type_family == type2.type_family) {
      switch (type1.type_family) {
        case CodeConstants.TYPE_FAMILY_INTEGER:
          if ((type1.type == CodeConstants.TYPE_SHORTCHAR && type2.type == CodeConstants.TYPE_BYTE)
              || (type1.type == CodeConstants.TYPE_BYTE && type2.type == CodeConstants.TYPE_SHORTCHAR)) {
            return VARTYPE_SHORT;
          }
          else {
            return VARTYPE_INT;
          }
        case CodeConstants.TYPE_FAMILY_OBJECT:
          return VARTYPE_OBJECT;
      }
    }

    return null;
  }

  public static VarType getMinTypeInFamily(int family) {
    switch (family) {
      case CodeConstants.TYPE_FAMILY_BOOLEAN:
        return VARTYPE_BOOLEAN;
      case CodeConstants.TYPE_FAMILY_INTEGER:
        return VARTYPE_BYTECHAR;
      case CodeConstants.TYPE_FAMILY_OBJECT:
        return VARTYPE_NULL;
      case CodeConstants.TYPE_FAMILY_FLOAT:
        return VARTYPE_FLOAT;
      case CodeConstants.TYPE_FAMILY_LONG:
        return VARTYPE_LONG;
      case CodeConstants.TYPE_FAMILY_DOUBLE:
        return VARTYPE_DOUBLE;
      case CodeConstants.TYPE_FAMILY_UNKNOWN:
        return VARTYPE_UNKNOWN;
      default:
        throw new RuntimeException("invalid type family!");
    }
  }

  public boolean equals(Object o) {

    if (o == this) {
      return true;
    }

    if (o == null || !(o instanceof VarType)) {
      return false;
    }

    VarType vt = (VarType)o;
    return type == vt.type && arraydim == vt.arraydim && InterpreterUtil.equalObjects(value, vt.value);
  }

  private void parseTypeString(String strtype, boolean cltype) {

    for (int i = 0; i < strtype.length(); i++) {
      switch (strtype.charAt(i)) {
        case '[':
          arraydim++;
          break;
        case 'L':
          if (strtype.charAt(strtype.length() - 1) == ';') {
            type = CodeConstants.TYPE_OBJECT;
            value = strtype.substring(i + 1, strtype.length() - 1);
            return;
          }
        default:
          value = strtype.substring(i, strtype.length());
          if ((cltype && i == 0) || value.length() > 1) {
            type = CodeConstants.TYPE_OBJECT;
          }
          else {
            type = getType(value.charAt(0));
          }
          return;
      }
    }
  }

  private void setStackSize(int type) {
    if (arraydim > 0) {
      stack_size = 1;
    }
    else {
      stack_size = (type == CodeConstants.TYPE_DOUBLE ||
                    type == CodeConstants.TYPE_LONG) ? 2 :
                   ((type == CodeConstants.TYPE_VOID ||
                     type == CodeConstants.TYPE_GROUP2EMPTY) ? 0 : 1);
    }
  }

  private static int getType(char c) {
    switch (c) {
      case 'B':
        return CodeConstants.TYPE_BYTE;
      case 'C':
        return CodeConstants.TYPE_CHAR;
      case 'D':
        return CodeConstants.TYPE_DOUBLE;
      case 'F':
        return CodeConstants.TYPE_FLOAT;
      case 'I':
        return CodeConstants.TYPE_INT;
      case 'J':
        return CodeConstants.TYPE_LONG;
      case 'S':
        return CodeConstants.TYPE_SHORT;
      case 'Z':
        return CodeConstants.TYPE_BOOLEAN;
      case 'V':
        return CodeConstants.TYPE_VOID;
      case 'G':
        return CodeConstants.TYPE_GROUP2EMPTY;
      case 'N':
        return CodeConstants.TYPE_NOTINITIALIZED;
      case 'A':
        return CodeConstants.TYPE_ADDRESS;
      case 'X':
        return CodeConstants.TYPE_BYTECHAR;
      case 'Y':
        return CodeConstants.TYPE_SHORTCHAR;
      case 'U':
        return CodeConstants.TYPE_UNKNOWN;
      default:
        throw new RuntimeException("Invalid type");
    }
  }

  private static String getChar(int type) {
    switch (type) {
      case CodeConstants.TYPE_BYTE:
        return "B";
      case CodeConstants.TYPE_CHAR:
        return "C";
      case CodeConstants.TYPE_DOUBLE:
        return "D";
      case CodeConstants.TYPE_FLOAT:
        return "F";
      case CodeConstants.TYPE_INT:
        return "I";
      case CodeConstants.TYPE_LONG:
        return "J";
      case CodeConstants.TYPE_SHORT:
        return "S";
      case CodeConstants.TYPE_BOOLEAN:
        return "Z";
      case CodeConstants.TYPE_VOID:
        return "V";
      case CodeConstants.TYPE_GROUP2EMPTY:
        return "G";
      case CodeConstants.TYPE_NOTINITIALIZED:
        return "N";
      case CodeConstants.TYPE_ADDRESS:
        return "A";
      case CodeConstants.TYPE_BYTECHAR:
        return "X";
      case CodeConstants.TYPE_SHORTCHAR:
        return "Y";
      case CodeConstants.TYPE_UNKNOWN:
        return "U";
      case CodeConstants.TYPE_NULL:
      case CodeConstants.TYPE_OBJECT:
        return null;
      default:
        throw new RuntimeException("Invalid type");
    }
  }

  public void setFamily() {

    if (arraydim > 0) {
      this.type_family = CodeConstants.TYPE_FAMILY_OBJECT;
      return;
    }

    switch (type) {
      case CodeConstants.TYPE_BYTE:
      case CodeConstants.TYPE_BYTECHAR:
      case CodeConstants.TYPE_SHORTCHAR:
      case CodeConstants.TYPE_CHAR:
      case CodeConstants.TYPE_SHORT:
      case CodeConstants.TYPE_INT:
        this.type_family = CodeConstants.TYPE_FAMILY_INTEGER;
        break;
      case CodeConstants.TYPE_DOUBLE:
        this.type_family = CodeConstants.TYPE_FAMILY_DOUBLE;
        break;
      case CodeConstants.TYPE_FLOAT:
        this.type_family = CodeConstants.TYPE_FAMILY_FLOAT;
        break;
      case CodeConstants.TYPE_LONG:
        this.type_family = CodeConstants.TYPE_FAMILY_LONG;
        break;
      case CodeConstants.TYPE_BOOLEAN:
        this.type_family = CodeConstants.TYPE_FAMILY_BOOLEAN;
        break;
      case CodeConstants.TYPE_NULL:
      case CodeConstants.TYPE_OBJECT:
        this.type_family = CodeConstants.TYPE_FAMILY_OBJECT;
        break;
      default:
        this.type_family = CodeConstants.TYPE_FAMILY_UNKNOWN;
    }
  }
}
