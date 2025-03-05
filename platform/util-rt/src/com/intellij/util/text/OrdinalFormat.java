// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text;

import java.text.*;
import java.util.Locale;

public final class OrdinalFormat {

  private OrdinalFormat() { }

  /**
   * Replaces all instances of {@code "{?,number,ordinal}"} format elements with the ordinal format for the locale.
   */
  public static void apply(MessageFormat format) {
    Format[] formats = format.getFormats();
    NumberFormat ordinal = null;
    for (int i = 0; i < formats.length; i++) {
      Format element = formats[i];
      if (element instanceof DecimalFormat && "ordinal".equals(((DecimalFormat)element).getPositivePrefix())) {
        if (ordinal == null) ordinal = getOrdinalFormat(format.getLocale());
        format.setFormat(i, ordinal);
      }
    }
  }

  private static NumberFormat getOrdinalFormat(Locale locale) {
    if (locale != null) {
      String language = locale.getLanguage();
      if ("en".equals(language) || language.isEmpty() /*the bundle fallback locale*/) {
        return new EnglishOrdinalFormat();
      }
    }

    return new DecimalFormat();
  }

  private static class EnglishOrdinalFormat extends NumberFormat {
    @Override
    public StringBuffer format(long number, StringBuffer toAppendTo, FieldPosition pos) {
      return new MessageFormat("{0}").format(new Object[]{formatEnglish(number)}, toAppendTo, pos);
    }

    @Override
    public StringBuffer format(double number, StringBuffer toAppendTo, FieldPosition pos) {
      throw new IllegalArgumentException("Cannot format non-integer number");
    }

    @Override
    public Number parse(String source, ParsePosition parsePosition) {
      throw new UnsupportedOperationException();
    }
  }

  public static String formatEnglish(long num) {
    long mod = Math.abs(num) % 100;
    if (mod < 11 || mod > 13) {
      mod = mod % 10;
      if (mod == 1) return num + "st";
      if (mod == 2) return num + "nd";
      if (mod == 3) return num + "rd";
    }
    return num + "th";
  }
}