/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.bugs;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.InheritanceUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings({"CollectionDeclaredAsConcreteClass", "ObjectEquality", "HardCodedStringLiteral"})
class FormatDecode {

  private static final String FORMAT_SPECIFIER =
    "%(\\d+\\$)?([-#+ 0,(\\<]*)?(\\d+)?(\\.\\d+)?([tT])?([a-zA-Z%])";

  private static final Pattern fsPattern = Pattern.compile(FORMAT_SPECIFIER);

  private FormatDecode() {}

  private static final Validator ALL_VALIDATOR = new AllValidator();

  private static final int LEFT_JUSTIFY = 1; // '-'
  private static final int ALTERNATE = 2; // '#'
  private static final int PLUS = 4; // '+'
  private static final int LEADING_SPACE = 8; // ' '
  private static final int ZERO_PAD = 16; // '0'
  private static final int GROUP = 32; // ','
  private static final int PARENTHESES = 64; // '('
  private static final int PREVIOUS = 128; // '<'

  private static int flag(char c) {
    switch (c) {
      case '-': return LEFT_JUSTIFY;
      case '#': return ALTERNATE;
      case '+': return PLUS;
      case ' ': return LEADING_SPACE;
      case '0': return ZERO_PAD;
      case ',': return GROUP;
      case '(': return PARENTHESES;
      case '<': return PREVIOUS;
      default: throw new IllegalFormatException();
    }
  }

  public static Validator[] decode(String formatString, int argumentCount) {
    final ArrayList<Validator> parameters = new ArrayList<Validator>();

    final Matcher matcher = fsPattern.matcher(formatString);
    boolean previousAllowed = false;
    int implicit = 0;
    int pos = 0;
    int i = 0;
    while (matcher.find(i)) {
      final int start = matcher.start();
      if (start != i) {
        checkText(formatString.substring(i, start));
      }
      i = matcher.end();
      final String specifier = matcher.group();
      final String posSpec = matcher.group(1);
      final String flags = matcher.group(2);
      final String width = matcher.group(3);
      final String precision = matcher.group(4);
      final String dateSpec = matcher.group(5);
      final String spec = matcher.group(6);

      int flagBits = 0;
      for (int j = 0; j < flags.length(); j++) {
        final int bit = flag(flags.charAt(j));
        if ((flagBits | bit) == flagBits) throw new IllegalFormatException(specifier); // duplicate flag
        flagBits |= bit;
      }
      if (isAllBitsSet(flagBits, LEADING_SPACE | PLUS) || isAllBitsSet(flagBits, LEFT_JUSTIFY | ZERO_PAD)) {
        // illegal flag combination
        throw new IllegalFormatException(specifier);
      }

      // check this first because it should not affect "implicit"
      if ("n".equals(spec)) {
        // no flags allowed
        if (flagBits != 0 || !StringUtil.isEmpty(width) || !StringUtil.isEmpty(precision)) throw new IllegalFormatException(specifier);
        continue;
      }
      else if ("%".equals(spec)) {
        if (isAnyBitSet(flagBits, ~LEFT_JUSTIFY) || !StringUtil.isEmpty(precision)) throw new IllegalFormatException(specifier);
        continue;
      }

      if (posSpec != null) {
        if (isAnyBitSet(flagBits, PREVIOUS)) throw new IllegalFormatException(specifier);
        final String num = posSpec.substring(0, posSpec.length() - 1);
        pos = Integer.parseInt(num) - 1;
        previousAllowed = true;
      }
      else if (isAnyBitSet(flagBits, PREVIOUS)) {
        // reuse last pos
        if (!previousAllowed) throw new IllegalFormatException(specifier);
      }
      else {
        previousAllowed = true;
        pos = implicit++;
      }

      final Validator allowed;
      if (dateSpec != null) {  // a t or T
        if (isAnyBitSet(flagBits, ~LEFT_JUSTIFY) || !StringUtil.isEmpty(precision)) throw new IllegalFormatException(specifier);
        allowed = new DateValidator(specifier);
      }
      else {
        switch (spec.charAt(0)) {
          case 'b':
          case 'B':
          case 'h':
          case 'H':
            if (isAnyBitSet(flagBits, ~LEFT_JUSTIFY)) throw new IllegalFormatException(specifier);
            allowed = ALL_VALIDATOR;
            break;
          case 's':
          case 'S':
            if (isAnyBitSet(flagBits, ~(LEFT_JUSTIFY | ALTERNATE))) throw new IllegalFormatException(specifier);
            allowed = ALL_VALIDATOR;
            break;
          case 'c':
          case 'C':
            if (isAnyBitSet(flagBits, ~LEFT_JUSTIFY) || !StringUtil.isEmpty(precision)) throw new IllegalFormatException(specifier);
            allowed = new CharValidator(specifier);
            break;
          case 'd':
            if (isAnyBitSet(flagBits, ALTERNATE)) throw new IllegalFormatException(specifier);
            allowed = new IntValidator(specifier);
            break;
          case 'o':
          case 'x':
          case 'X':
            if (isAnyBitSet(flagBits, PLUS | LEADING_SPACE | GROUP) || !StringUtil.isEmpty(precision)) {
              throw new IllegalFormatException(specifier);
            }
            allowed = new IntValidator(specifier);
            break;
          case 'a':
          case 'A':
            if (isAnyBitSet(flagBits, PARENTHESES | GROUP)) throw new IllegalFormatException(specifier);
            allowed = new FloatValidator(specifier);
            break;
          case 'e':
          case 'E':
            if (isAnyBitSet(flagBits, GROUP)) throw new IllegalFormatException(specifier);
            allowed = new FloatValidator(specifier);
            break;
          case 'g':
          case 'G':
            if (isAnyBitSet(flagBits, ALTERNATE)) throw new IllegalFormatException(specifier);
            allowed = new FloatValidator(specifier);
            break;
          case 'f':
            allowed = new FloatValidator(specifier);
            break;
          default:
            throw new IllegalFormatException(specifier);
        }
      }
      storeValidator(allowed, pos, parameters, argumentCount);
    }
    if (i < formatString.length() - 1) {
      checkText(formatString.substring(i));
    }

    return parameters.toArray(new Validator[parameters.size()]);
  }

  private static boolean isAnyBitSet(int value, int mask) {
    return (value & mask) != 0;
  }

  private static boolean isAllBitsSet(int value, int mask) {
    return (value & mask) == mask;
  }

  private static void checkText(String s) {
    if (s.indexOf('%') != -1) {
      throw new IllegalFormatException();
    }
  }

  private static void storeValidator(Validator validator, int pos,
                                     ArrayList<Validator> parameters,
                                     int argumentCount) {
    if (pos < parameters.size()) {
      final Validator existing = parameters.get(pos);
      if (existing == null) {
        parameters.set(pos, validator);
      }
      else if (existing instanceof MultiValidator) {
        ((MultiValidator)existing).addValidator(validator);
      }
      else if (existing != validator) {
        final MultiValidator multiValidator = new MultiValidator(existing.getSpecifier());
        multiValidator.addValidator(existing);
        multiValidator.addValidator(validator);
        parameters.set(pos, multiValidator);
      }
    }
    else {
      while (pos > parameters.size() && argumentCount > parameters.size()) {
        parameters.add(null);
      }
      parameters.add(validator);
    }
  }

  public static class IllegalFormatException extends RuntimeException {

    public IllegalFormatException(String message) {
      super(message);
    }

    public IllegalFormatException() {}
  }

  private static class AllValidator extends Validator {

    public AllValidator() {
      super("");
    }

    @Override
    public boolean valid(PsiType type) {
      return true;
    }
  }

  private static class DateValidator extends Validator {

    public DateValidator(String specifier) {
      super(specifier);
    }

    @Override
    public boolean valid(PsiType type) {
      final String text = type.getCanonicalText();
      return PsiType.LONG.equals(type) ||
             CommonClassNames.JAVA_LANG_LONG.equals(text) ||
             InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_DATE) ||
             InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_CALENDAR) ||
             InheritanceUtil.isInheritor(type, "java.time.temporal.TemporalAccessor");
    }
  }

  private static class CharValidator extends Validator {

    public CharValidator(String specifier) {
      super(specifier);
    }

    @Override
    public boolean valid(PsiType type) {
      if (PsiType.CHAR.equals(type) || PsiType.BYTE.equals(type) || PsiType.SHORT.equals(type) || PsiType.INT.equals(type)) {
        return true;
      }
      final String text = type.getCanonicalText();
      return CommonClassNames.JAVA_LANG_CHARACTER.equals(text) ||
             CommonClassNames.JAVA_LANG_BYTE.equals(text) ||
             CommonClassNames.JAVA_LANG_SHORT.equals(text) ||
             CommonClassNames.JAVA_LANG_INTEGER.equals(text);
    }
  }

  private static class IntValidator extends Validator {

    public IntValidator(String specifier) {
      super(specifier);
    }

    @Override
    public boolean valid(PsiType type) {
      final String text = type.getCanonicalText();
      return PsiType.INT.equals(type) ||
             CommonClassNames.JAVA_LANG_INTEGER.equals(text) ||
             PsiType.LONG.equals(type) ||
             CommonClassNames.JAVA_LANG_LONG.equals(text) ||
             PsiType.SHORT.equals(type) ||
             CommonClassNames.JAVA_LANG_SHORT.equals(text) ||
             PsiType.BYTE.equals(type) ||
             CommonClassNames.JAVA_LANG_BYTE.equals(text) ||
             "java.math.BigInteger".equals(text);
    }
  }

  private static class FloatValidator extends Validator {

    public FloatValidator(String specifier) {
      super(specifier);
    }

    @Override
    public boolean valid(PsiType type) {
      final String text = type.getCanonicalText();
      return PsiType.DOUBLE.equals(type) ||
             CommonClassNames.JAVA_LANG_DOUBLE.equals(text) ||
             PsiType.FLOAT.equals(type) ||
             CommonClassNames.JAVA_LANG_FLOAT.equals(text) ||
             "java.math.BigDecimal".equals(text);
    }
  }

  private static class MultiValidator extends Validator {
    private final Set<Validator> validators = new HashSet<Validator>(3);

    public MultiValidator(String specifier) {
      super(specifier);
    }

    @Override
    public boolean valid(PsiType type) {
      for (Validator validator : validators) {
        if (!validator.valid(type)) {
          return false;
        }
      }
      return true;
    }

    public void addValidator(Validator validator) {
      validators.add(validator);
    }
  }

  abstract static class Validator {

    private final String mySpecifier;

    public Validator(String specifier) {
      mySpecifier = specifier;
    }

    public abstract boolean valid(PsiType type);

    public String getSpecifier() {
      return mySpecifier;
    }
  }
}