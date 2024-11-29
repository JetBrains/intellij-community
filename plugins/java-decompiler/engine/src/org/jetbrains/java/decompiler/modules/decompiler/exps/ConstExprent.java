// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.modules.decompiler.exps;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
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

  private static final Map<Double, String[]> PI_DOUBLES = new HashMap<>();
  private static final Map<Float, String[]> PI_FLOATS = new HashMap<>();
  private static final Map<Float, String> FLOAT_CONSTANTS = new HashMap<>();
  private static final Map<Double, String> DOUBLE_CONSTANTS = new HashMap<>();

  static {
    final double PI_D = Math.PI;
    final float PI_F = (float)Math.PI;
    PI_DOUBLES.put(PI_D, new String[] { "", "" });
    PI_DOUBLES.put(-PI_D, new String[] { "-", "" });
    PI_FLOATS.put(PI_F, new String[] { "", "" });
    PI_FLOATS.put(-PI_F, new String[] { "-", "" });

    PI_DOUBLES.put(PI_D * 2D, new String[] { "(", " * 2D)" });
    PI_DOUBLES.put(-PI_D * 2D, new String[] { "(-", " * 2D)" });
    PI_FLOATS.put(PI_F * 2F, new String[] { "(", " * 2F)" });
    PI_FLOATS.put(-PI_F * 2F, new String[] { "(-", " * 2F)" });

    PI_DOUBLES.put(PI_D / 2D, new String[] { "(", " / 2D)" });
    PI_DOUBLES.put(-PI_D / 2D, new String[] { "(-", " / 2D)" });
    PI_FLOATS.put(PI_F / 2F, new String[] { "(", " / 2F)" });
    PI_FLOATS.put(-PI_F / 2F, new String[] { "(-", " / 2F)" });

    PI_DOUBLES.put(PI_D * 1.5D, new String[] { "(", " * 1.5D)" });
    PI_DOUBLES.put(-PI_D * 1.5D, new String[] { "(-", " * 1.5D)" });
    PI_FLOATS.put(PI_F * 1.5F, new String[] { "(", " * 1.5F)" });
    PI_FLOATS.put(-PI_F * 1.5F, new String[] { "(-", " * 1.5F)" });

    PI_DOUBLES.put(PI_D / 3D, new String[] { "(", " / 3D)" });
    PI_DOUBLES.put(-PI_D / 3D, new String[] { "(-", " / 3D)" });
    PI_FLOATS.put(PI_F / 3F, new String[] { "(", " / 3F)" });
    PI_FLOATS.put(-PI_F / 3F, new String[] { "(-", " / 3F)" });

    PI_DOUBLES.put(PI_D / 4D, new String[] { "(", " / 4D)" });
    PI_DOUBLES.put(-PI_D / 4D, new String[] { "(-", " / 4D)" });
    PI_FLOATS.put(PI_F / 4F, new String[] { "(", " / 4F)" });
    PI_FLOATS.put(-PI_F / 4F, new String[] { "(-", " / 4F)" });

    PI_DOUBLES.put(PI_D / 5D, new String[] { "(", " / 5D)" });
    PI_DOUBLES.put(-PI_D / 5D, new String[] { "(-", " / 5D)" });
    PI_FLOATS.put(PI_F / 5F, new String[] { "(", " / 5F)" });
    PI_FLOATS.put(-PI_F / 5F, new String[] { "(-", " / 5F)" });

    PI_DOUBLES.put(PI_D / 6D, new String[] { "(", " / 6D)" });
    PI_DOUBLES.put(-PI_D / 6D, new String[] { "(-", " / 6D)" });
    PI_FLOATS.put(PI_F / 6F, new String[] { "(", " / 6F)" });
    PI_FLOATS.put(-PI_F / 6F, new String[] { "(-", " / 6F)" });

    PI_DOUBLES.put(PI_D / 8D, new String[] { "(", " / 8D)" });
    PI_DOUBLES.put(-PI_D / 8D, new String[] { "(-", " / 8D)" });
    PI_FLOATS.put(PI_F / 8F, new String[] { "(", " / 8F)" });
    PI_FLOATS.put(-PI_F / 8F, new String[] { "(-", " / 8F)" });

    PI_DOUBLES.put(PI_D / 10D, new String[] { "(", " / 10D)" });
    PI_DOUBLES.put(-PI_D / 10D, new String[] { "(-", " / 10D)" });
    PI_FLOATS.put(PI_F / 10F, new String[] { "(", " / 10F)" });
    PI_FLOATS.put(-PI_F / 10F, new String[] { "(-", " / 10F)" });

    // Radian/degree conversions
    PI_DOUBLES.put(PI_D / 180D, new String[] { "(", " / 180D)" });
    PI_DOUBLES.put(180D / PI_D, new String[] { "(180D / ", ")" });
    PI_FLOATS.put(PI_F / 180F, new String[] { "(", " / 180F)" });
    PI_FLOATS.put(180F / PI_F, new String[] { "(180F / ", ")" });

    FLOAT_CONSTANTS.put((float)Integer.MAX_VALUE, "(float)Integer.MAX_VALUE");
    FLOAT_CONSTANTS.put((float)Integer.MIN_VALUE, "(float)Integer.MIN_VALUE");
    FLOAT_CONSTANTS.put((float)Long.MAX_VALUE, "(float)Long.MAX_VALUE");
    FLOAT_CONSTANTS.put((float)Long.MIN_VALUE, "(float)Long.MIN_VALUE");
    DOUBLE_CONSTANTS.put((double)Integer.MAX_VALUE, "(double)Integer.MAX_VALUE");
    DOUBLE_CONSTANTS.put((double)Integer.MIN_VALUE, "(double)Integer.MIN_VALUE");
    DOUBLE_CONSTANTS.put((double)Long.MAX_VALUE, "(double)Long.MAX_VALUE");
    DOUBLE_CONSTANTS.put((double)Long.MIN_VALUE, "(double)Long.MIN_VALUE");
  }

  @NotNull
  private VarType constType;
  private final Object value;
  private final boolean boolPermitted;

  public ConstExprent(int val, boolean boolPermitted, BitSet bytecodeOffsets) {
    this(guessType(val, boolPermitted), val, boolPermitted, bytecodeOffsets);
  }

  public ConstExprent(VarType constType, Object value, BitSet bytecodeOffsets) {
    this(constType, value, false, bytecodeOffsets);
  }

  public ConstExprent(VarType constType, Object value, BitSet bytecodeOffsets, StructMember parent) {
    this(constType, value, bytecodeOffsets);
    this.parent = parent;
  }

  private ConstExprent(VarType constType, Object value, boolean boolPermitted, BitSet bytecodeOffsets) {
    super(EXPRENT_CONST);
    this.constType = constType == null ? VarType.VARTYPE_UNKNOWN : constType;
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

  @NotNull
  @Override
  public VarType getExprType() {
    return constType;
  }

  @Override
  public int getExprentUse() {
    return Exprent.MULTIPLE_USES | Exprent.SIDE_EFFECTS_FREE;
  }

  @Override
  public List<Exprent> getAllExprents(List<Exprent> list) {
    return list;
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
      case CodeConstants.TYPE_FLOAT -> createFloat(literal, (Float)value, tracer);
      case CodeConstants.TYPE_DOUBLE -> {
        double doubleVal = (Double)value;
        boolean withSuffix = DecompilerContext.getOption(IFernflowerPreferences.STANDARDIZE_FLOATING_POINT_NUMBERS);
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
          else if (doubleVal == -Double.MAX_VALUE && !inConstantVariable(DOUBLE_SIG, MAX_VAL)) {
            yield new FieldExprent(MAX_VAL, DOUBLE_SIG, true, null, FieldDescriptor.DOUBLE_DESCRIPTOR, bytecode).toJava(0, tracer).prepend("-");
          }
          else if (doubleVal == -Double.MIN_NORMAL) {
            yield new FieldExprent(MIN_NORM, DOUBLE_SIG, true, null, FieldDescriptor.DOUBLE_DESCRIPTOR, bytecode).toJava(0, tracer).prepend("-");
          }
          else if (doubleVal == -Double.MIN_VALUE) {
            yield new FieldExprent(MIN_VAL, DOUBLE_SIG, true, null, FieldDescriptor.DOUBLE_DESCRIPTOR, bytecode).toJava(0, tracer).prepend("-");
          }
          else if (PI_DOUBLES.containsKey(doubleVal)) {
            String[] parts = PI_DOUBLES.get(doubleVal);
            yield getPiDouble(tracer).enclose(parts[0], parts[1]);
          }
          else if (DOUBLE_CONSTANTS.containsKey(doubleVal)) {
            yield new TextBuffer(DOUBLE_CONSTANTS.get(doubleVal));
          }
        }
        else if (Double.isNaN(doubleVal)) {
          yield withSuffix ? new TextBuffer("0.0D / 0.0D") : new TextBuffer("0.0 / 0.0");
        }
        else if (doubleVal == Double.POSITIVE_INFINITY) {
          yield withSuffix ? new TextBuffer("1.0D / 0.0D") : new TextBuffer("1.0 / 0.0") ;
        }
        else if (doubleVal == Double.NEGATIVE_INFINITY) {
          yield withSuffix ? new TextBuffer("-1.0D / 0.0D") : new TextBuffer("-1.0 / 0.0");
        }
        TextBuffer doubleBuffer = new TextBuffer(trimDouble(Double.toString(doubleVal), doubleVal));
        if (withSuffix) {
          doubleBuffer = doubleBuffer.append('D');
        }

        if (!literal) {
          // Check for cases where a float literal has been upcasted to a double.
          // (for instance, double d = .01F results in 0.009999999776482582D without this)
          float nearestFloatVal = (float)doubleVal;
          if (doubleVal == (double)nearestFloatVal) {
            // Value can be represented precisely as both a float and a double.
            // Now check if the string representation as a float is nicer/shorter.
            // If they're the same, there's no point in the cast and such (e.g. don't decompile 1.0D as (double)1.0F).
            TextBuffer floatBuffer = createFloat(literal, nearestFloatVal, tracer);
            if (floatBuffer.length() != doubleBuffer.length()) {
              // Include a cast to prevent using the wrong method call in ambiguous cases.
              yield floatBuffer.prepend("(double)");
            }
          }
        }

        yield doubleBuffer;
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

  private TextBuffer createFloat(boolean literal, float floatVal, BytecodeMappingTracer tracer) {
    if (!literal) {
      // Float constants, some of which can't be represented directly
      if (Float.isNaN(floatVal) && !inConstantVariable(FLOAT_SIG, NAN)) {
        return new FieldExprent(NAN, FLOAT_SIG, true, null, FieldDescriptor.FLOAT_DESCRIPTOR, bytecode).toJava(0, tracer);
      }
      else if (floatVal == Float.POSITIVE_INFINITY && !inConstantVariable(FLOAT_SIG, POS_INF)) {
        return new FieldExprent(POS_INF, FLOAT_SIG, true, null, FieldDescriptor.FLOAT_DESCRIPTOR, bytecode).toJava(0, tracer);
      }
      else if (floatVal == Float.NEGATIVE_INFINITY && !inConstantVariable(FLOAT_SIG, NEG_INF)) {
        return new FieldExprent(NEG_INF, FLOAT_SIG, true, null, FieldDescriptor.FLOAT_DESCRIPTOR, bytecode).toJava(0, tracer);
      }
      else if (floatVal == Float.MAX_VALUE && !inConstantVariable(FLOAT_SIG, MAX_VAL)) {
        return new FieldExprent(MAX_VAL, FLOAT_SIG, true, null, FieldDescriptor.FLOAT_DESCRIPTOR, bytecode).toJava(0, tracer);
      }
      else if (floatVal == Float.MIN_NORMAL && !inConstantVariable(FLOAT_SIG, MIN_NORM)) {
        return new FieldExprent(MIN_NORM, FLOAT_SIG, true, null, FieldDescriptor.FLOAT_DESCRIPTOR, bytecode).toJava(0, tracer);
      }
      else if (floatVal == Float.MIN_VALUE && !inConstantVariable(FLOAT_SIG, MIN_VAL)) {
        return new FieldExprent(MIN_VAL, FLOAT_SIG, true, null, FieldDescriptor.FLOAT_DESCRIPTOR, bytecode).toJava(0, tracer);
      }
      else if (floatVal == -Float.MAX_VALUE && !inConstantVariable(FLOAT_SIG, MAX_VAL)) {
        return new FieldExprent(MAX_VAL, FLOAT_SIG, true, null, FieldDescriptor.FLOAT_DESCRIPTOR, bytecode).toJava(0, tracer).prepend("-");
      }
      else if (floatVal == -Float.MIN_NORMAL && !inConstantVariable(FLOAT_SIG, MIN_NORM)) {
        return new FieldExprent(MIN_NORM, FLOAT_SIG, true, null, FieldDescriptor.FLOAT_DESCRIPTOR, bytecode).toJava(0, tracer).prepend("-");
      }
      else if (floatVal == -Float.MIN_VALUE && !inConstantVariable(FLOAT_SIG, MIN_VAL)) {
        return new FieldExprent(MIN_VAL, FLOAT_SIG, true, null, FieldDescriptor.FLOAT_DESCRIPTOR, bytecode).toJava(0, tracer).prepend("-");
      }
      // Math constants
      else if (floatVal == (float)Math.E && !inConstantVariable(MATH_SIG, E)) {
        return new FieldExprent(E, MATH_SIG, true, null, FieldDescriptor.DOUBLE_DESCRIPTOR, bytecode).toJava(0, tracer).prepend("(float)");
      }
      else if (PI_FLOATS.containsKey(floatVal) && !inConstantVariable(MATH_SIG, PI)) {
        String[] parts = PI_FLOATS.get(floatVal);
        return getPiFloat(tracer).enclose(parts[0], parts[1]);
      }
      else if (FLOAT_CONSTANTS.containsKey(floatVal)) {
        return new TextBuffer(FLOAT_CONSTANTS.get(floatVal));
      }
    }
    else {
      // Check for special values that can't be used directly in code
      // (and we can't replace with the constant due to the user requesting not to)
      if (Float.isNaN(floatVal)) {
        return new TextBuffer("0.0F / 0.0F");
      }
      else if (floatVal == Float.POSITIVE_INFINITY) {
        return new TextBuffer("1.0F / 0.0F");
      }
      else if (floatVal == Float.NEGATIVE_INFINITY) {
        return new TextBuffer("-1.0F / 0.0F");
      }
    }
    return new TextBuffer(trimFloat(Float.toString(floatVal), floatVal)).append('F');
  }

  private TextBuffer getPiDouble(BytecodeMappingTracer tracer) {
    return new FieldExprent(PI, MATH_SIG, true, null, FieldDescriptor.DOUBLE_DESCRIPTOR, bytecode).toJava(0, tracer);
  }

  private TextBuffer getPiFloat(BytecodeMappingTracer tracer) {
    // java.lang.Math doesn't have a float version of pi, unfortunately
    return getPiDouble(tracer).prepend("(float)");
  }

  // Different JVM implementations/version display Floats and Doubles with different String representations
  // for the same thing. This trims them all down to only the necessary amount.
  @VisibleForTesting
  public static String trimFloat(String value, float start) {
    // Includes NaN and simple numbers
    if (value.length() <= 3 || !DecompilerContext.getOption(IFernflowerPreferences.STANDARDIZE_FLOATING_POINT_NUMBERS))
      return value;

    String exp = "";
    int eIdx = value.indexOf('E');
    if (eIdx != -1) {
      exp = value.substring(eIdx);
      value = value.substring(0, eIdx);
    }

    // Cut off digits that don't affect the value
    String temp = value;
    int dotIdx = value.indexOf('.');
    do {
      value = temp;
      temp = value.substring(0, value.length() - 1);
    } while (!temp.isEmpty() && !"-".equals(temp) && Float.parseFloat(temp + exp) == start);

    if (dotIdx != -1 && value.indexOf('.') == -1) {
      value += ".0";
    } else if (dotIdx != -1) {
      String integer = value.substring(0, dotIdx);
      String decimal = value.substring(dotIdx + 1);

      String rounded = (Integer.parseInt(integer) + 1) + ".0" + exp;
      if (Float.parseFloat(rounded) == start)
        return rounded;

      long decimalVal = 1;
      int leadingZeros = 0;
      for (int i = 0; i < decimal.length() - 1; i++) {
        if (decimal.charAt(i) == '0' && leadingZeros == i) {
          leadingZeros++;
        }
        decimalVal = (decimalVal - 1) * 10 + decimal.charAt(i) - '0' + 1;
        rounded = integer + '.' + "0".repeat(leadingZeros) + decimalVal + exp;
        if (Float.parseFloat(rounded) == start) {
          return rounded;
        }
      }
    }

    return value + exp;
  }

  @VisibleForTesting
  public static String trimDouble(String value, double start) {
    // Includes NaN and simple numbers
    if (value.length() <= 3 || !DecompilerContext.getOption(IFernflowerPreferences.STANDARDIZE_FLOATING_POINT_NUMBERS))
      return value;

    String exp = "";
    int eIdx = value.indexOf('E');
    if (eIdx != -1) {
      exp = value.substring(eIdx);
      value = value.substring(0, eIdx);
    }

    // Cut off digits that don't affect the value
    String temp = value;
    int dotIdx = value.indexOf('.');
    do {
      value = temp;
      temp = value.substring(0, value.length() - 1);
    } while (!temp.isEmpty() && !"-".equals(temp) && Double.parseDouble(temp + exp) == start);

    if (dotIdx != -1 && value.indexOf('.') == -1) {
      value += ".0";
    } else if (dotIdx != -1) {
      String integer = value.substring(0, dotIdx);
      String decimal = value.substring(dotIdx + 1);

      String rounded = (Long.parseLong(integer) + 1) + ".0" + exp;
      if (Double.parseDouble(rounded) == start)
        return rounded;

      long decimalVal = 1;
      int leadingZeros = 0;
      for (int i = 0; i < decimal.length() - 1; i++) {
        if(decimal.charAt(i) == '0' && leadingZeros == i) {
          leadingZeros++;
        }
        decimalVal = (decimalVal - 1) * 10 + decimal.charAt(i) - '0' + 1;
        rounded = integer + '.' + "0".repeat(leadingZeros) + decimalVal + exp;
        if (Double.parseDouble(rounded) == start) {
          return rounded;
        }
      }
    }
    return value + exp;
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
    int result = constType.hashCode();
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

  public @NotNull VarType getConstType() {
    return constType;
  }

  public void setConstType(@Nullable VarType constType) {
    if (constType == null) {
      constType = VarType.VARTYPE_UNKNOWN;
    }
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

  @Override
  public void fillBytecodeRange(@Nullable BitSet values) {
    measureBytecode(values);
  }

  @Override
  public String toString() {
    return "const(" + toJava(0, new BytecodeMappingTracer()) + ")";
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