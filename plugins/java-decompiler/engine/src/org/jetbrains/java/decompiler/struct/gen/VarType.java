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

  public static final VarType[] EMPTY_ARRAY = {};

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
  public static final VarType VARTYPE_STRING = new VarType(CodeConstants.TYPE_OBJECT, 0, "java/lang/String");
  public static final VarType VARTYPE_CLASS = new VarType(CodeConstants.TYPE_OBJECT, 0, "java/lang/Class");
  public static final VarType VARTYPE_OBJECT = new VarType(CodeConstants.TYPE_OBJECT, 0, "java/lang/Object");
  public static final VarType VARTYPE_INTEGER = new VarType(CodeConstants.TYPE_OBJECT, 0, "java/lang/Integer");
  public static final VarType VARTYPE_CHARACTER = new VarType(CodeConstants.TYPE_OBJECT, 0, "java/lang/Character");
  public static final VarType VARTYPE_BYTE_OBJ = new VarType(CodeConstants.TYPE_OBJECT, 0, "java/lang/Byte");
  public static final VarType VARTYPE_SHORT_OBJ = new VarType(CodeConstants.TYPE_OBJECT, 0, "java/lang/Short");
  public static final VarType VARTYPE_VOID = new VarType(CodeConstants.TYPE_VOID);

  public final int type;
  public final int arrayDim;
  public final String value;
  public final int typeFamily;
  public final int stackSize;
  public final boolean falseBoolean;

  public VarType(int type) {
    this(type, 0);
  }

  public VarType(int type, int arrayDim) {
    this(type, arrayDim, getChar(type));
  }

  public VarType(int type, int arrayDim, String value) {
    this(type, arrayDim, value, getFamily(type, arrayDim), getStackSize(type, arrayDim), false);
  }

  private VarType(int type, int arrayDim, String value, int typeFamily, int stackSize, boolean falseBoolean) {
    this.type = type;
    this.arrayDim = arrayDim;
    this.value = value;
    this.typeFamily = typeFamily;
    this.stackSize = stackSize;
    this.falseBoolean = falseBoolean;
  }

  public VarType(String signature) {
    this(signature, false);
  }

  public VarType(String signature, boolean clType) {
    int type = 0;
    int arrayDim = 0;
    String value = null;

    loop:
    for (int i = 0; i < signature.length(); i++) {
      switch (signature.charAt(i)) {
        case '[':
          arrayDim++;
          break;

        case 'L':
          if (signature.charAt(signature.length() - 1) == ';') {
            type = CodeConstants.TYPE_OBJECT;
            value = signature.substring(i + 1, signature.length() - 1);
            break loop;
          }

        default:
          value = signature.substring(i, signature.length());
          if ((clType && i == 0) || value.length() > 1) {
            type = CodeConstants.TYPE_OBJECT;
          }
          else {
            type = getType(value.charAt(0));
          }
          break loop;
      }
    }

    this.type = type;
    this.arrayDim = arrayDim;
    this.value = value;
    this.typeFamily = getFamily(type, arrayDim);
    this.stackSize = getStackSize(type, arrayDim);
    this.falseBoolean = false;
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

  private static int getStackSize(int type, int arrayDim) {
    if (arrayDim > 0) {
      return 1;
    }

    switch (type) {
      case CodeConstants.TYPE_DOUBLE:
      case CodeConstants.TYPE_LONG:
        return 2;
      case CodeConstants.TYPE_VOID:
      case CodeConstants.TYPE_GROUP2EMPTY:
        return 0;
      default:
        return 1;
    }
  }

  private static int getFamily(int type, int arrayDim) {
    if (arrayDim > 0) {
      return CodeConstants.TYPE_FAMILY_OBJECT;
    }

    switch (type) {
      case CodeConstants.TYPE_BYTE:
      case CodeConstants.TYPE_BYTECHAR:
      case CodeConstants.TYPE_SHORTCHAR:
      case CodeConstants.TYPE_CHAR:
      case CodeConstants.TYPE_SHORT:
      case CodeConstants.TYPE_INT:
        return CodeConstants.TYPE_FAMILY_INTEGER;
      case CodeConstants.TYPE_DOUBLE:
        return CodeConstants.TYPE_FAMILY_DOUBLE;
      case CodeConstants.TYPE_FLOAT:
        return CodeConstants.TYPE_FAMILY_FLOAT;
      case CodeConstants.TYPE_LONG:
        return CodeConstants.TYPE_FAMILY_LONG;
      case CodeConstants.TYPE_BOOLEAN:
        return CodeConstants.TYPE_FAMILY_BOOLEAN;
      case CodeConstants.TYPE_NULL:
      case CodeConstants.TYPE_OBJECT:
        return CodeConstants.TYPE_FAMILY_OBJECT;
      default:
        return CodeConstants.TYPE_FAMILY_UNKNOWN;
    }
  }

  public VarType decreaseArrayDim() {
    if (arrayDim > 0) {
      return new VarType(type, arrayDim - 1, value);
    }
    else {
      //throw new RuntimeException("array dimension equals 0!"); FIXME: investigate this case
      return this;
    }
  }

  public VarType resizeArrayDim(int newArrayDim) {
    return new VarType(type, newArrayDim, value, typeFamily, stackSize, falseBoolean);
  }

  public VarType copy() {
    return copy(false);
  }

  public VarType copy(boolean forceFalseBoolean) {
    return new VarType(type, arrayDim, value, typeFamily, stackSize, falseBoolean || forceFalseBoolean);
  }

  public boolean isFalseBoolean() {
    return falseBoolean;
  }

  public boolean isSuperset(VarType val) {
    return this.equals(val) || this.isStrictSuperset(val);
  }

  public boolean isStrictSuperset(VarType val) {
    int valType = val.type;

    if (valType == CodeConstants.TYPE_UNKNOWN && type != CodeConstants.TYPE_UNKNOWN) {
      return true;
    }

    if (val.arrayDim > 0) {
      return this.equals(VARTYPE_OBJECT);
    }
    else if (arrayDim > 0) {
      return (valType == CodeConstants.TYPE_NULL);
    }

    boolean res = false;

    switch (type) {
      case CodeConstants.TYPE_INT:
        res = (valType == CodeConstants.TYPE_SHORT || valType == CodeConstants.TYPE_CHAR);
      case CodeConstants.TYPE_SHORT:
        res |= (valType == CodeConstants.TYPE_BYTE);
      case CodeConstants.TYPE_CHAR:
        res |= (valType == CodeConstants.TYPE_SHORTCHAR);
      case CodeConstants.TYPE_BYTE:
      case CodeConstants.TYPE_SHORTCHAR:
        res |= (valType == CodeConstants.TYPE_BYTECHAR);
      case CodeConstants.TYPE_BYTECHAR:
        res |= (valType == CodeConstants.TYPE_BOOLEAN);
        break;

      case CodeConstants.TYPE_OBJECT:
        if (valType == CodeConstants.TYPE_NULL) {
          return true;
        }
        else if (this.equals(VARTYPE_OBJECT)) {
          return valType == CodeConstants.TYPE_OBJECT && !val.equals(VARTYPE_OBJECT);
        }
    }

    return res;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }

    if (o == null || !(o instanceof VarType)) {
      return false;
    }

    VarType vt = (VarType)o;
    return type == vt.type && arrayDim == vt.arrayDim && InterpreterUtil.equalObjects(value, vt.value);
  }

  @Override
  public String toString() {
    StringBuilder res = new StringBuilder();
    for (int i = 0; i < arrayDim; i++) {
      res.append('[');
    }
    if (type == CodeConstants.TYPE_OBJECT) {
      res.append('L').append(value).append(';');
    }
    else {
      res.append(value);
    }
    return res.toString();
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
    else if (type1.typeFamily == type2.typeFamily) {
      switch (type1.typeFamily) {
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
    else if (type1.typeFamily == type2.typeFamily) {
      switch (type1.typeFamily) {
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
        throw new IllegalArgumentException("Invalid type family: " + family);
    }
  }

  public static int getType(char c) {
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
        throw new IllegalArgumentException("Invalid type: " + c);
    }
  }
}
