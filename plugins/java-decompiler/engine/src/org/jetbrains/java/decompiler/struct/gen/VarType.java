// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.struct.gen;

import org.jetbrains.java.decompiler.code.CodeConstants;

import java.util.Objects;

public class VarType implements Type {  // TODO: optimize switch

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

  private final int type;
  private final int arrayDim;
  private final String value;
  private final int typeFamily;
  private final int stackSize;
  private final boolean falseBoolean;

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
          value = signature.substring(i);
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

  @Override
  public int getType() {
    return type;
  }

  @Override
  public int getArrayDim() {
    return arrayDim;
  }

  @Override
  public String getValue() {
    return value;
  }

  public int getTypeFamily() {
    return typeFamily;
  }

  public int getStackSize() {
    return stackSize;
  }

  private static String getChar(int type) {
    return switch (type) {
      case CodeConstants.TYPE_BYTE -> "B";
      case CodeConstants.TYPE_CHAR -> "C";
      case CodeConstants.TYPE_DOUBLE -> "D";
      case CodeConstants.TYPE_FLOAT -> "F";
      case CodeConstants.TYPE_INT -> "I";
      case CodeConstants.TYPE_LONG -> "J";
      case CodeConstants.TYPE_SHORT -> "S";
      case CodeConstants.TYPE_BOOLEAN -> "Z";
      case CodeConstants.TYPE_VOID -> "V";
      case CodeConstants.TYPE_GROUP2EMPTY -> "G";
      case CodeConstants.TYPE_NOTINITIALIZED -> "N";
      case CodeConstants.TYPE_ADDRESS -> "A";
      case CodeConstants.TYPE_BYTECHAR -> "X";
      case CodeConstants.TYPE_SHORTCHAR -> "Y";
      case CodeConstants.TYPE_UNKNOWN -> "U";
      case CodeConstants.TYPE_NULL, CodeConstants.TYPE_OBJECT -> null;
      default -> throw new RuntimeException("Invalid type");
    };
  }

  private static int getStackSize(int type, int arrayDim) {
    if (arrayDim > 0) {
      return 1;
    }

    return switch (type) {
      case CodeConstants.TYPE_DOUBLE, CodeConstants.TYPE_LONG -> 2;
      case CodeConstants.TYPE_VOID, CodeConstants.TYPE_GROUP2EMPTY -> 0;
      default -> 1;
    };
  }

  private static int getFamily(int type, int arrayDim) {
    if (arrayDim > 0) {
      return CodeConstants.TYPE_FAMILY_OBJECT;
    }

    return switch (type) {
      case CodeConstants.TYPE_BYTE, CodeConstants.TYPE_BYTECHAR, CodeConstants.TYPE_SHORTCHAR, CodeConstants.TYPE_CHAR,
        CodeConstants.TYPE_SHORT, CodeConstants.TYPE_INT -> CodeConstants.TYPE_FAMILY_INTEGER;
      case CodeConstants.TYPE_DOUBLE -> CodeConstants.TYPE_FAMILY_DOUBLE;
      case CodeConstants.TYPE_FLOAT -> CodeConstants.TYPE_FAMILY_FLOAT;
      case CodeConstants.TYPE_LONG -> CodeConstants.TYPE_FAMILY_LONG;
      case CodeConstants.TYPE_BOOLEAN -> CodeConstants.TYPE_FAMILY_BOOLEAN;
      case CodeConstants.TYPE_NULL, CodeConstants.TYPE_OBJECT -> CodeConstants.TYPE_FAMILY_OBJECT;
      default -> CodeConstants.TYPE_FAMILY_UNKNOWN;
    };
  }

  public VarType decreaseArrayDim() {
    if (getArrayDim() > 0) {
      return new VarType(getType(), getArrayDim() - 1, getValue());
    }
    else {
      //throw new RuntimeException("array dimension equals 0!"); FIXME: investigate this case
      return this;
    }
  }

  public VarType resizeArrayDim(int newArrayDim) {
    return new VarType(getType(), newArrayDim, getValue(), getTypeFamily(), getStackSize(), isFalseBoolean());
  }

  public VarType copy() {
    return copy(false);
  }

  public VarType copy(boolean forceFalseBoolean) {
    return new VarType(getType(), getArrayDim(), getValue(), getTypeFamily(), getStackSize(), isFalseBoolean() || forceFalseBoolean);
  }

  public boolean isFalseBoolean() {
    return falseBoolean;
  }

  public boolean isSuperset(VarType val) {
    return this.equals(val) || this.isStrictSuperset(val);
  }

  public boolean isStrictSuperset(VarType val) {
    int valType = val.getType();

    if (valType == CodeConstants.TYPE_UNKNOWN && getType() != CodeConstants.TYPE_UNKNOWN) {
      return true;
    }

    if (val.getArrayDim() > 0) {
      return this.equals(VARTYPE_OBJECT);
    }
    else if (getArrayDim() > 0) {
      return (valType == CodeConstants.TYPE_NULL);
    }

    boolean res = false;

    switch (getType()) {
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

    if (!(o instanceof VarType vt)) {
      return false;
    }

    return getType() == vt.getType() && getArrayDim() == vt.getArrayDim() && Objects.equals(getValue(), vt.getValue());
  }

  @Override
  public String toString() {
    StringBuilder res = new StringBuilder();
    for (int i = 0; i < getArrayDim(); i++) {
      res.append('[');
    }
    if (getType() == CodeConstants.TYPE_OBJECT) {
      res.append('L').append(getValue()).append(';');
    }
    else {
      res.append(getValue());
    }
    return res.toString();
  }

  // type1 and type2 must not be null
  public static VarType getCommonMinType(VarType type1, VarType type2) {
    if (type1.getType() == CodeConstants.TYPE_BOOLEAN && type2.getType() == CodeConstants.TYPE_BOOLEAN) { // special case booleans
      return type1.isFalseBoolean() ? type2 : type1;
    }

    if (type1.isSuperset(type2)) {
      return type2;
    }
    else if (type2.isSuperset(type1)) {
      return type1;
    }
    else if (type1.getTypeFamily() == type2.getTypeFamily()) {
      switch (type1.getTypeFamily()) {
        case CodeConstants.TYPE_FAMILY_INTEGER -> {
          if ((type1.getType() == CodeConstants.TYPE_CHAR && type2.getType() == CodeConstants.TYPE_SHORT)
              || (type1.getType() == CodeConstants.TYPE_SHORT && type2.getType() == CodeConstants.TYPE_CHAR)) {
            return VARTYPE_SHORTCHAR;
          }
          else {
            return VARTYPE_BYTECHAR;
          }
        }
        case CodeConstants.TYPE_FAMILY_OBJECT -> {
          return VARTYPE_NULL;
        }
      }
    }

    return null;
  }

  // type1 and type2 must not be null
  public static VarType getCommonSupertype(VarType type1, VarType type2) {
    if (type1.getType() == CodeConstants.TYPE_BOOLEAN && type2.getType() == CodeConstants.TYPE_BOOLEAN) { // special case booleans
      return type1.isFalseBoolean() ? type1 : type2;
    }

    if (type1.isSuperset(type2)) {
      return type1;
    }
    else if (type2.isSuperset(type1)) {
      return type2;
    }
    else if (type1.getTypeFamily() == type2.getTypeFamily()) {
      switch (type1.getTypeFamily()) {
        case CodeConstants.TYPE_FAMILY_INTEGER -> {
          if ((type1.getType() == CodeConstants.TYPE_SHORTCHAR && type2.getType() == CodeConstants.TYPE_BYTE)
              || (type1.getType() == CodeConstants.TYPE_BYTE && type2.getType() == CodeConstants.TYPE_SHORTCHAR)) {
            return VARTYPE_SHORT;
          }
          else {
            return VARTYPE_INT;
          }
        }
        case CodeConstants.TYPE_FAMILY_OBJECT -> {
          return VARTYPE_OBJECT;
        }
      }
    }

    return null;
  }

  public static VarType getMinTypeInFamily(int family) {
    return switch (family) {
      case CodeConstants.TYPE_FAMILY_BOOLEAN -> VARTYPE_BOOLEAN;
      case CodeConstants.TYPE_FAMILY_INTEGER -> VARTYPE_BYTECHAR;
      case CodeConstants.TYPE_FAMILY_OBJECT -> VARTYPE_NULL;
      case CodeConstants.TYPE_FAMILY_FLOAT -> VARTYPE_FLOAT;
      case CodeConstants.TYPE_FAMILY_LONG -> VARTYPE_LONG;
      case CodeConstants.TYPE_FAMILY_DOUBLE -> VARTYPE_DOUBLE;
      case CodeConstants.TYPE_FAMILY_UNKNOWN -> VARTYPE_UNKNOWN;
      default -> throw new IllegalArgumentException("Invalid type family: " + family);
    };
  }

  public static int getType(char c) {
    return switch (c) {
      case 'B' -> CodeConstants.TYPE_BYTE;
      case 'C' -> CodeConstants.TYPE_CHAR;
      case 'D' -> CodeConstants.TYPE_DOUBLE;
      case 'F' -> CodeConstants.TYPE_FLOAT;
      case 'I' -> CodeConstants.TYPE_INT;
      case 'J' -> CodeConstants.TYPE_LONG;
      case 'S' -> CodeConstants.TYPE_SHORT;
      case 'Z' -> CodeConstants.TYPE_BOOLEAN;
      case 'V' -> CodeConstants.TYPE_VOID;
      case 'G' -> CodeConstants.TYPE_GROUP2EMPTY;
      case 'N' -> CodeConstants.TYPE_NOTINITIALIZED;
      case 'A' -> CodeConstants.TYPE_ADDRESS;
      case 'X' -> CodeConstants.TYPE_BYTECHAR;
      case 'Y' -> CodeConstants.TYPE_SHORTCHAR;
      case 'U' -> CodeConstants.TYPE_UNKNOWN;
      default -> throw new IllegalArgumentException("Invalid type: " + c);
    };
  }
}
