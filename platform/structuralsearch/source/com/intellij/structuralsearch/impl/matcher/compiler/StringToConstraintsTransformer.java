// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.structuralsearch.MalformedPatternException;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.MatchVariableConstraint;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * @author maxim
 */
public final class StringToConstraintsTransformer {
  @NonNls private static final String REF = "ref";
  @NonNls private static final String REGEX = "regex";
  @NonNls private static final String REGEXW = "regexw";
  @NonNls private static final String EXPRTYPE = "exprtype";
  @NonNls private static final String FORMAL = "formal";
  @NonNls private static final String SCRIPT = "script";
  @NonNls private static final String CONTAINS = "contains";
  @NonNls private static final String WITHIN = "within";
  @NonNls private static final String CONTEXT = "context";

  private static final Set<String> knownOptions =
    Set.of(REF, REGEX, REGEXW, EXPRTYPE, FORMAL, SCRIPT, CONTAINS, WITHIN, CONTEXT);

  @SuppressWarnings("AssignmentToForLoopParameter")
  public static void transformCriteria(@NotNull String criteria, @NotNull MatchOptions options) {
    final StringBuilder pattern = new StringBuilder();
    int anonymousTypedVarsCount = 0;
    boolean targetFound = false;

    final MatchVariableConstraint context = options.addNewVariableConstraint(Configuration.CONTEXT_VAR_NAME);

    final int length = criteria.length();
    for (int index = 0; index < length; ++index) {
      char ch = criteria.charAt(index);

      if (index == 0 && ch == '[') {
        index = handleTypedVarCondition(0, criteria, context);
        if (index == length) break;
        ch = criteria.charAt(index);
      }
      if (ch == '\\' && index + 1 < length) {
        ch = criteria.charAt(++index);
      }
      else if (ch == '\'') {
        final int newIndex = handleCharacterLiteral(criteria, index, pattern);
        if (newIndex != index) {
          index = newIndex;
          continue;
        }

        // typed variable; eat the name of typed var
        int endIndex = ++index;
        while (endIndex < length && Character.isJavaIdentifierPart(criteria.charAt(endIndex))) endIndex++;
        if (endIndex == index) throw new MalformedPatternException(SSRBundle.message("error.expected.character"));

        boolean target = true;
        final String typedVar;
        if (criteria.charAt(index) == '_') {
          target = false;

          if (endIndex == index + 1) {
            // anonymous var, make it unique for the case of constraints
            anonymousTypedVarsCount++;
            typedVar = "_" + anonymousTypedVarsCount;
          }
          else {
            typedVar = criteria.substring(index + 1, endIndex);
          }
        }
        else {
          typedVar = criteria.substring(index, endIndex);
        }

        pattern.append("$").append(typedVar).append("$");
        index = endIndex;
        MatchVariableConstraint constraint = options.getVariableConstraint(typedVar);
        boolean constraintCreated = false;

        if (constraint == null) {
          constraint = new MatchVariableConstraint(typedVar);
          constraintCreated = true;
        }

        // Check the number of occurrences for typed variable
        final int savedIndex = index;
        int minOccurs = 1;
        int maxOccurs = 1;
        boolean greedy = true;
        if (index < length) {
          ch = criteria.charAt(index);
          if (ch == '+') {
            maxOccurs = Integer.MAX_VALUE;
            ++index;
          }
          else if (ch == '?') {
            minOccurs = 0;
            ++index;
          }
          else if (ch == '*') {
            minOccurs = 0;
            maxOccurs = Integer.MAX_VALUE;
            ++index;
          }
          else if (ch == '{') {
            ++index;
            minOccurs = -1;
            maxOccurs = -1;
            while (index < length && (ch = criteria.charAt(index)) >= '0' && ch <= '9') {
              if (minOccurs < 0) minOccurs = 0;
              minOccurs = (minOccurs * 10) + (ch - '0');
              if (minOccurs < 0) throw new MalformedPatternException(SSRBundle.message("error.overflow"));
              ++index;
            }

            if (ch == ',') {
              ++index;

              while (index < length && (ch = criteria.charAt(index)) >= '0' && ch <= '9') {
                if (maxOccurs < 0) maxOccurs = 0;
                maxOccurs = (maxOccurs * 10) + (ch - '0');
                if (maxOccurs < 0) throw new MalformedPatternException(SSRBundle.message("error.overflow"));
                ++index;
              }
            }
            else {
              maxOccurs = -2;
            }

            if (ch != '}') {
              if (minOccurs < 0 && maxOccurs < 0) throw new MalformedPatternException(SSRBundle.message("error.expected.digit"));
              if (maxOccurs < 0) {
                throw new MalformedPatternException(SSRBundle.message("error.expected.brace1"));
              }
              else {
                throw new MalformedPatternException(SSRBundle.message("error.expected.brace2"));
              }
            }
            if (minOccurs < 0 && maxOccurs < 0) {
              throw new MalformedPatternException(SSRBundle.message("error.empty.quantifier"));
            }
            else if (minOccurs == -1) {
              minOccurs = 0;
            }
            else if (maxOccurs == -1) {
              maxOccurs = Integer.MAX_VALUE;
            }
            else if (maxOccurs == -2) maxOccurs = minOccurs;
            ++index;
          }

          if (index < length) {
            ch = criteria.charAt(index);
            if (ch == '?') {
              greedy = false;
              ++index;
            }
          }
        }

        if (constraintCreated) {
          constraint.setMinCount(minOccurs);
          constraint.setMaxCount(maxOccurs);
          constraint.setGreedy(greedy);
          constraint.setPartOfSearchResults(target);
          if (targetFound && target) {
            throw new MalformedPatternException(SSRBundle.message("error.only.one.target.allowed"));
          }
          targetFound |= target;
        }
        else if (savedIndex != index) {
          throw new MalformedPatternException(SSRBundle.message("error.condition.only.on.first.variable.reference"));
        }

        if (index < length && criteria.charAt(index) == ':') {
          ++index;
          if (index >= length) throw new MalformedPatternException(SSRBundle.message("error.expected.condition", ":"));
          ch = criteria.charAt(index);
          if (ch == ':') {
            // double colon instead of condition
            pattern.append(ch);
          }
          else {
            if (!constraintCreated) {
              throw new MalformedPatternException(SSRBundle.message("error.condition.only.on.first.variable.reference"));
            }
            index = handleTypedVarCondition(index, criteria, constraint);
          }
        }

        if (constraintCreated) {
          options.addVariableConstraint(constraint);
        }

        if (index == length) break;
        // rewind to process white space or unrelated symbol
        index--;
        continue;
      }

      pattern.append(ch);
    }

    options.setSearchPattern(pattern.toString());
  }

  private static int handleCharacterLiteral(@NotNull String criteria, int index, @NotNull StringBuilder pattern) {
    final int length = criteria.length();
    if (index + 1 < length && criteria.charAt(index + 1) == '\'') {
      // ignore next '
      pattern.append('\'');
      return index + 1;
    }
    else if (index + 2 < length && criteria.charAt(index + 2) == '\'') {
      // eat simple character
      pattern.append(criteria, index, index + 3);
      return index + 2;
    }
    else if (index + 3 < length && criteria.charAt(index + 1) == '\\' && criteria.charAt(index + 3) == '\'') {
      // eat simple escape character
      pattern.append(criteria, index, index + 4);
      return index + 3;
    }
    else if (index + 7 < length &&
             criteria.charAt(index + 1) == '\\' &&
             criteria.charAt(index + 2) == 'u' &&
             criteria.charAt(index + 7) == '\'') {
      // eat unicode escape character
      pattern.append(criteria, index, index + 8);
      return index + 7;
    }
    return index;
  }

  private static int handleTypedVarCondition(int index, @NotNull String criteria, @NotNull MatchVariableConstraint constraint) {
    final int length = criteria.length();

    char ch = criteria.charAt(index);
    if (ch == '!') {
      constraint.setInvertRegExp(true);
      ++index;
      if (index >= length) throw new MalformedPatternException(SSRBundle.message("error.expected.condition", Character.valueOf(ch)));
      ch = criteria.charAt(index);
    }
    if (ch == '+' || ch == '*') {
      // this is type axis navigation relation
      switch (ch) {
        case '+' -> constraint.setStrictlyWithinHierarchy(true);
        case '*' -> constraint.setWithinHierarchy(true);
      }

      ++index;
      if (index >= length) throw new MalformedPatternException(SSRBundle.message("error.expected.condition", Character.valueOf(ch)));
      ch = criteria.charAt(index);
    }

    if (ch == '[') {
      int spaces = 0; // balance spaces surrounding content between brackets
      while (++index < length && criteria.charAt(index) == ' ') spaces++;

      // eat complete condition
      boolean quoted = false;
      boolean closed = false;
      int endIndex = index - 1;
      while (++endIndex < length) {
        if (criteria.charAt(endIndex - 1) != '\\') {
          ch = criteria.charAt(endIndex);
          if (ch == '"') {
            quoted = !quoted;
          }
          else if (ch == ']' && !quoted) {
            int j = 1;
            while (j <= spaces && criteria.charAt(endIndex - j) == ' ') j++;
            if (j - 1 == spaces) {
              endIndex -= spaces;
              closed = true;
              break;
            }
          }
        }
      }
      if (quoted) throw new MalformedPatternException(SSRBundle.message("error.expected.value", "\""));
      if (!closed) throw new MalformedPatternException(SSRBundle.message("error.expected.value", " ".repeat(spaces) + "]"));
      if (index > endIndex) throw new MalformedPatternException(SSRBundle.message("error.expected.condition", "["));
      parseCondition(constraint, criteria.substring(index, endIndex));
      return endIndex + spaces + 1;
    }
    else {
      // eat reg exp constraint
      return handleRegExp(index, criteria, constraint);
    }
  }

  private static int handleRegExp(int index, @NotNull String criteria, @NotNull MatchVariableConstraint constraint) {
    final int length = criteria.length();
    int endIndex = index;
    while (endIndex < length && !Character.isWhitespace(criteria.charAt(endIndex))) {
      ++endIndex;
    }

    if (endIndex == index) {
      if (criteria.charAt(index - 1) == ':') {
        throw new MalformedPatternException(SSRBundle.message("error.expected.condition", ":"));
      }
      else {
        return endIndex;
      }
    }
    final String regexp = criteria.substring(index, endIndex);

    if (!constraint.getRegExp().isEmpty() && !constraint.getRegExp().equals(regexp)) {
      throw new MalformedPatternException(SSRBundle.message("error.two.different.type.constraints"));
    }
    else {
      checkRegex(regexp);
      constraint.setRegExp(regexp);
    }

    return endIndex;
  }

  @SuppressWarnings("AssignmentToForLoopParameter")
  private static void parseCondition(@NotNull MatchVariableConstraint constraint, @NotNull String condition) {
    final int length = condition.length();
    final StringBuilder text = new StringBuilder();
    boolean invert = false;
    boolean optionExpected = true;
    for (int i = 0; i < length; i++) {
      char c = condition.charAt(i);
      if (Character.isWhitespace(c)) {
        if (text.length() == 0) continue;
        handleOption(constraint, text.toString(), "", invert);
        optionExpected = false;
      }
      else if (c == '(') {
        if (text.length() == 0) throw new MalformedPatternException(SSRBundle.message("error.expected.condition.name"));
        final String option = text.toString();
        if (!option.startsWith("_") && !knownOptions.contains(option)) {
          throw new MalformedPatternException(SSRBundle.message("option.is.not.recognized.error.message", option));
        }
        text.setLength(0);
        int spaces = 0; // balance spaces surrounding content between parentheses
        while (++i < length && condition.charAt(i) == ' ') spaces++;
        i--;
        boolean quoted = false;
        boolean closed = false;
        while (++i < length) {
          c = condition.charAt(i);
          if (condition.charAt(i - 1) != '\\') {
            if (c == '"') {
              quoted = !quoted;
            }
            else if (c == ')' && !quoted) {
              int j = 1;
              while (j <= spaces && condition.charAt(i - j) == ' ') j++;
              if (j - 1 == spaces) {
                closed = true;
                break;
              }
            }
          }
          text.append(c);
        }
        if (text.length() == 0) throw new MalformedPatternException(SSRBundle.message("error.argument.expected", option));
        if (quoted) throw new MalformedPatternException(SSRBundle.message("error.expected.value", "\""));
        if (!closed) throw new MalformedPatternException(SSRBundle.message("error.expected.value", " ".repeat(spaces) + ")"));
        handleOption(constraint, option, text.toString(), invert);
        text.setLength(0);
        invert = false;
        optionExpected = false;
      }
      else if (c == '&') {
        if (text.length() != 0) {
          handleOption(constraint, text.toString(), "", invert);
          optionExpected = false;
        }
        if (++i == length || condition.charAt(i) != '&' || optionExpected) {
          throw new MalformedPatternException(SSRBundle.message("error.unexpected.value", "&"));
        }
        text.setLength(0);
        invert = false;
        optionExpected = true;
      }
      else if (!optionExpected) {
        throw new MalformedPatternException(SSRBundle.message("error.expected.value", "&&"));
      }
      else if (c == '!') {
        if (text.length() != 0) throw new MalformedPatternException(SSRBundle.message("error.unexpected.value", "!"));
        invert = !invert;
      }
      else {
        text.append(c);
      }
    }
    if (text.length() != 0) {
      handleOption(constraint, text.toString(), "", invert);
    }
    else if (invert) {
      throw new MalformedPatternException(SSRBundle.message("error.expected.condition", "!"));
    }
    else if (optionExpected) throw new MalformedPatternException(SSRBundle.message("error.expected.condition", length == 0 ? "[" : "&&"));
  }

  private static void handleOption(@NotNull MatchVariableConstraint constraint, @NotNull String option, @NotNull String argument,
                                   boolean invert) {
    argument = argument.trim();
    if (option.equals(REF)) {
      constraint.setReferenceConstraint(argument);
      constraint.setInvertReference(invert);
    }
    else if (option.equals(REGEX) || option.equals(REGEXW)) {
      if (argument.charAt(0) == '*') {
        argument = argument.substring(1);
        constraint.setWithinHierarchy(true);
      }
      checkRegex(argument);
      constraint.setRegExp(argument);
      constraint.setInvertRegExp(invert);
      if (option.equals(REGEXW)) {
        constraint.setWholeWordsOnly(true);
      }
    }
    else if (option.equals(EXPRTYPE)) {
      if (argument.charAt(0) == '*') {
        argument = argument.substring(1);
        constraint.setExprTypeWithinHierarchy(true);
      }
      boolean regex = false;
      if (argument.charAt(0) == '~') {
        argument = argument.substring(1);
        regex = true;
      }
      argument = unescape(argument);
      if (regex) constraint.setNameOfExprType(argument);
      else constraint.setExpressionTypes(argument);
      constraint.setInvertExprType(invert);
    }
    else if (option.equals(FORMAL)) {
      if (argument.charAt(0) == '*') {
        argument = argument.substring(1);
        constraint.setFormalArgTypeWithinHierarchy(true);
      }
      argument = unescape(argument);
      constraint.setExpectedTypes(argument);
      constraint.setInvertFormalType(invert);
    }
    else if (option.equals(SCRIPT)) {
      if (invert) throw new MalformedPatternException(SSRBundle.message("error.cannot.invert", option));
      constraint.setScriptCodeConstraint(argument);
    }
    else if (option.equals(CONTAINS)) {
      constraint.setContainsConstraint(argument);
      constraint.setInvertContainsConstraint(invert);
    }
    else if (option.equals(WITHIN)) {
      if (!Configuration.CONTEXT_VAR_NAME.equals(constraint.getName())) {
        throw new MalformedPatternException(SSRBundle.message("error.only.applicable.to.complete.match", option));
      }
      constraint.setWithinConstraint(argument);
      constraint.setInvertWithinConstraint(invert);
    }
    else if (option.equals(CONTEXT)) {
      if (invert) throw new MalformedPatternException(SSRBundle.message("error.cannot.invert", option));
      if (!Configuration.CONTEXT_VAR_NAME.equals(constraint.getName())) {
        throw new MalformedPatternException(SSRBundle.message("error.only.applicable.to.complete.match", option));
      }
      constraint.setContextConstraint(argument);
    }
    else if (option.startsWith("_")) {
      if (invert) throw new MalformedPatternException(SSRBundle.message("error.cannot.invert", option));
      constraint.putAdditionalConstraint(option.substring(1), argument);
    }
    else {
      assert false;
    }
  }

  private static void checkRegex(@NotNull String regex) {
    try {
      Pattern.compile(regex);
    }
    catch (PatternSyntaxException e) {
      throw new MalformedPatternException(SSRBundle.message("invalid.regular.expression", e.getMessage()));
    }
  }

  @NotNull
  private static String unescape(@NotNull String s) {
    final StringBuilder result = new StringBuilder();
    boolean escaped = false;
    for (int i = 0, length = s.length(); i < length; i++) {
      final int c = s.codePointAt(i);
      if (c == '\\' && !escaped) {
        escaped = true;
      }
      else {
        escaped = false;
        result.appendCodePoint(c);
      }
    }
    return result.toString();
  }
}
