// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.integer;

import com.intellij.codeInsight.intention.numeric.NumberConverter;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class JavaNumberConverters {
  static final NumberConverter INTEGER_TO_HEX = new NumberConverter() {
    @Override
    @Contract(pure = true)
    public @Nullable String getConvertedText(@NotNull String text, @NotNull Number number) {
      if (text.startsWith("0x") || text.startsWith("0X")) return null;
      if (number instanceof Integer) {
        return "0x" + Integer.toHexString(number.intValue());
      }
      if (number instanceof Long) {
        return "0x" + Long.toHexString(number.longValue()) + "L";
      }
      return null;
    }

    @Override
    public String toString() { return "hex";}
  };
  static final NumberConverter INTEGER_TO_BINARY = new NumberConverter() {
    @Override
    @Contract(pure = true)
    public @Nullable String getConvertedText(@NotNull String text, @NotNull Number number) {
      if (text.startsWith("0b") || text.startsWith("0B")) return null;
      if (number instanceof Integer) {
        return "0b" + Integer.toBinaryString(number.intValue());
      }
      if (number instanceof Long) {
        return "0b" + Long.toBinaryString(number.longValue()) + "L";
      }
      return null;
    }

    @Override
    public String toString() { return "binary";}
  };
  static final NumberConverter INTEGER_TO_OCTAL = new NumberConverter() {
    @Override
    public @Nullable String getConvertedText(@NotNull String text, @NotNull Number number) {
      if (ExpressionUtils.isOctalLiteralText(text)) return null;
      if (number instanceof Integer) {
        return "0" + Integer.toOctalString(number.intValue());
      }
      if (number instanceof Long) {
        return "0" + Long.toOctalString(number.longValue()) + "L";
      }
      return null;
    }

    @Override
    public String toString() {
      return "octal";
    }
  };
  static final NumberConverter INTEGER_TO_DECIMAL = new NumberConverter() {
    @Override
    public @Nullable String getConvertedText(@NotNull String text, @NotNull Number number) {
      if ("0L".equals(text) || "0l".equals(text) || text.charAt(0) != '0') return null;
      if (number instanceof Integer) {
        return Integer.toString(number.intValue());
      }
      if (number instanceof Long) {
        return Long.toString(number.longValue()) + 'L';
      }
      return null;
    }

    @Override
    public String toString() {
      return "decimal";
    }
  };
  static final NumberConverter FLOAT_TO_HEX = new NumberConverter() {
    @Override
    @Contract(pure = true)
    public @Nullable String getConvertedText(@NotNull String originalText, @NotNull Number original) {
      if (originalText.startsWith("0x") || originalText.startsWith("0X")) return null;
      if (original instanceof Float) {
        return Float.toHexString(original.floatValue()) + "f";
      }
      if (original instanceof Double) {
        return Double.toHexString(original.doubleValue());
      }
      return null;
    }

    @Override
    public String toString() { return "hex";}
  };
  static final NumberConverter FLOAT_TO_DECIMAL = new NumberConverter() {
    @Override
    @Contract(pure = true)
    public @Nullable String getConvertedText(@NotNull String originalText, @NotNull Number original) {
      if (!originalText.startsWith("0x") && !originalText.startsWith("0X")) return null;
      if (original instanceof Float) {
        return Float.toString(original.floatValue()) + 'f';
      }
      if (original instanceof Double) {
        return Double.toString(original.doubleValue());
      }
      return null;
    }

    @Override
    public String toString() { return "decimal";}
  };
  static final NumberConverter FLOAT_TO_PLAIN = new NumberConverter() {
    @Override
    @Contract(pure = true)
    public @Nullable String getConvertedText(@NotNull String text, @NotNull Number number) {
      if (!(number instanceof Float) && !(number instanceof Double)) return null;
      if (!text.contains("e") && !text.contains("E")) return null;
      String result;
      try {
        result = new BigDecimal(number.toString()).stripTrailingZeros().toPlainString();
      }
      catch (NumberFormatException ignored) {
        return null;
      }
      if (number instanceof Float) result += "f";
      else if (!result.contains(".")) {
        try {
          Integer.parseInt(result);
        }
        catch (NumberFormatException e) {
          return result + ".0";
        }
      }
      return result;
    }

    @Override
    public String toString() { return "plain format";}
  };
  static final NumberConverter FLOAT_TO_SCIENTIFIC = new NumberConverter() {
    private final DecimalFormat FORMAT = new DecimalFormat("0.0#############E00", new DecimalFormatSymbols(Locale.US));

    @Override
    @Contract(pure = true)
    public @Nullable String getConvertedText(@NotNull String text, @NotNull Number number) {
      if (!(number instanceof Float) && !(number instanceof Double)) return null;
      if (text.contains("e") || text.contains("E")) return null;
      String result = FORMAT.format(Double.parseDouble(number.toString()));  // convert to double w/o adding parasitic digits
      if (number instanceof Float) result += "f";
      return result;
    }

    @Override
    public String toString() { return "scientific format";}
  };
}
