/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiType;
import com.intellij.util.containers.ContainerUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings({"CollectionDeclaredAsConcreteClass", "ObjectEquality", "HardCodedStringLiteral"})
class FormatDecode {

  private static final String FORMAT_SPECIFIER =
    "%(\\d+\\$)?([-#+ 0,(\\<]*)?(\\d+)?(\\.\\d+)?([tT])?([a-zA-Z%])";

  private static final Pattern fsPattern = Pattern.compile(FORMAT_SPECIFIER);

  private FormatDecode() {
    super();
  }

  private static final Validator ALL_VALIDATOR = new AllValidator();

  private static final Validator DATE_VALIDATOR = new DateValidator();

  private static final Validator CHAR_VALIDATOR = new CharValidator();

  private static final Validator INT_VALIDATOR = new IntValidator();

  private static final Validator FLOAT_VALIDATOR = new FloatValidator();

  /**
   * Holds information about validator replacement rules, i.e. allows to answer if validator of particular type may be
   * safely replaced by validator of another particular type.
   * <p/>
   * For example, validator of type {@link AllValidator#type() 'all'} may be safely replaced by validator of any other
   * type, e.g. {@link DateValidator#type() Date/Time} or {@link CharValidator#type() 'char validator'} may be replaced
   * by {@link IntValidator#type() 'int validator'} because {@link Formatter java formatter} knows how to
   * {@link Formatter.FormatSpecifier#printCharacter(Object) print character from integer} etc.
   * <p/>
   * Generally, current collection holds set of mappings where the key is type of validator that may be safely replaced
   * by validator of type that is contained at <code>'values'</code> collection.
   */
  private static final Map<String, Set<String>> REPLACEABLE_VALIDATOR_TYPES = new HashMap<String, Set<String>>();

  static {
    REPLACEABLE_VALIDATOR_TYPES.put(
      ALL_VALIDATOR.type(),
      ContainerUtil.set(DATE_VALIDATOR.type(), CHAR_VALIDATOR.type(), INT_VALIDATOR.type(), FLOAT_VALIDATOR.type())
    );
    REPLACEABLE_VALIDATOR_TYPES.put(CHAR_VALIDATOR.type(), ContainerUtil.set(INT_VALIDATOR.type()));
  }

  public static Validator[] decode(String formatString, int argumentCount) {
    final ArrayList<Validator> parameters = new ArrayList<Validator>();

    final Matcher matcher = fsPattern.matcher(formatString);
    int implicit = 0;
    int pos = 0;
    for (int i = 0; matcher.find(i); i = matcher.end()) {
      final String posSpec = matcher.group(1);
      final String flags = matcher.group(2);
      final String dateSpec = matcher.group(5);
      final String spec = matcher.group(6);

      // check this first because it should not affect "implicit"
      if ("n".equals(spec) || "%".equals(spec)) {
        continue;
      }

      if (posSpec != null) {
        final String num = posSpec.substring(0, posSpec.length() - 1);
        pos = Integer.parseInt(num) - 1;
      }
      else if (flags == null || flags.indexOf('<') < 0) {
        pos = implicit++;
      }
      // else if the flag has "<" reuse the last pos

      final Validator allowed;
      if (dateSpec != null) {  // a t or T
        allowed = DATE_VALIDATOR;
      }
      else {
        switch (Character.toLowerCase(spec.charAt(0))) {
          case 'b':
          case 'h':
          case 's':
            allowed = ALL_VALIDATOR;
            break;
          case 'c':
            allowed = CHAR_VALIDATOR;
            break;
          case 'd':
          case 'o':
          case 'x':
            allowed = INT_VALIDATOR;
            break;
          case 'e':
          case 'f':
          case 'g':
          case 'a':
            allowed = FLOAT_VALIDATOR;
            break;
          default:
            throw new UnknownFormatException(matcher.group());
        }
      }
      argAt(allowed, pos, parameters, argumentCount);
    }

    return parameters.toArray(new Validator[parameters.size()]);
  }

  private static void argAt(Validator val, int pos,
                            ArrayList<Validator> parameters,
                            int argumentCount) {
    if (pos < parameters.size()) {
      final Validator old = parameters.get(pos);
      Set<String> replaceableTypes = REPLACEABLE_VALIDATOR_TYPES.get(old.type());
      if (replaceableTypes != null && replaceableTypes.contains(val.type())) {
        parameters.set(pos, val);
      }
      // it's OK to overwrite ALL with something more specific
      // it's OK to ignore overwrite of something else with ALL or itself
      else if (val != ALL_VALIDATOR && val != old) {
        throw new DuplicateFormatFlagsException(
          "requires both " + old.type() + " and " + val.type());
      }
    }
    else {
      while (pos > parameters.size() &&
             argumentCount > parameters.size()) {
        parameters.add(ALL_VALIDATOR);
      }
      parameters.add(val);
    }
  }

  public static class UnknownFormatException extends RuntimeException {

    public UnknownFormatException(String message) {
      super(message);
    }
  }

  public static class DuplicateFormatFlagsException extends RuntimeException {

    public DuplicateFormatFlagsException(String message) {
      super(message);
    }
  }

  private static class AllValidator implements Validator {

    @Override
    public boolean valid(PsiType type) {
      return true;
    }

    @Override
    public String type() {
      return "any";
    }
  }

  private static class DateValidator implements Validator {

    @Override
    public boolean valid(PsiType type) {
      final String text = type.getCanonicalText();

      return PsiType.LONG.equals(type) ||
             CommonClassNames.JAVA_LANG_LONG.equals(text) ||
             CommonClassNames.JAVA_UTIL_DATE.equals(text) ||
             CommonClassNames.JAVA_UTIL_CALENDAR.equals(text);
    }

    @Override
    public String type() {
      return "Date/Time";
    }
  }

  private static class CharValidator implements Validator {

    @Override
    public boolean valid(PsiType type) {
      final String text = type.getCanonicalText();
      return PsiType.CHAR.equals(type) ||
             CommonClassNames.JAVA_LANG_CHARACTER.equals(text);
    }

    @Override
    public String type() {
      return "char";
    }
  }

  private static class IntValidator implements Validator {

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

    @Override
    public String type() {
      return "integer type";
    }
  }

  private static class FloatValidator implements Validator {

    @Override
    public boolean valid(PsiType type) {
      final String text = type.getCanonicalText();
      return PsiType.DOUBLE.equals(type) ||
             CommonClassNames.JAVA_LANG_DOUBLE.equals(text) ||
             PsiType.FLOAT.equals(type) ||
             CommonClassNames.JAVA_LANG_FLOAT.equals(text) ||
             "java.math.BigDecimal".equals(text);
    }

    @Override
    public String type() {
      return "floating point";
    }
  }
}