package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.structuralsearch.*;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 17.11.2004
 * Time: 19:29:05
 * To change this template use File | Settings | File Templates.
 */
class StringToConstraintsTransformer {
  @NonNls private static final String REF = "ref";
  @NonNls private static final String READ = "read";
  @NonNls private static final String WRITE = "write";
  @NonNls private static final String REGEX = "regex";
  @NonNls private static final String REGEXW = "regexw";
  @NonNls private static final String EXPRTYPE = "exprtype";
  @NonNls private static final String FORMAL = "formal";
  @NonNls private static final String SCRIPT = "script";
  @NonNls private static final String CONTAINS = "contains";
  @NonNls private static final String WITHIN = "within";

  @SuppressWarnings("AssignmentToForLoopParameter")
  static void transformOldPattern(MatchOptions options) {
      final String pattern = options.getSearchPattern();

      final StringBuilder buf = new StringBuilder();

      StringBuilder miscBuffer = null;
      int anonymousTypedVarsCount = 0;
      boolean targetFound = false;

      final int length = pattern.length();
      for(int index=0; index < length; ++index) {
        char ch = pattern.charAt(index);

        if (index == 0 && ch == '[') {
          if (miscBuffer == null) miscBuffer = new StringBuilder();
          else miscBuffer.setLength(0);
          final MatchVariableConstraint constraint = new MatchVariableConstraint();
          constraint.setName(Configuration.CONTEXT_VAR_NAME);
          index = eatTypedVarCondition(0, pattern, miscBuffer, constraint);
          options.addVariableConstraint(constraint);
          if (index == length) break;
          ch = pattern.charAt(index);
        }
        if (ch == '\\' && index + 1 < length) {
          ch = pattern.charAt(++index);
        }
        else if (ch=='\'') {
          // doubling '
          if (index + 1 < length &&
              pattern.charAt(index + 1)=='\''
             ) {
            // ignore next '
            index++;
          } else if (index + 2 < length &&
                     pattern.charAt(index + 2)=='\''
             ) {
            // eat simple character
            buf.append(ch);
            buf.append(pattern.charAt(++index));
            ch = pattern.charAt(++index);
          } else if (index + 3 < length &&
                     pattern.charAt(index + 1)=='\\' &&
                     pattern.charAt(index + 3)=='\''
          ) {
            // eat simple escape character
            buf.append(ch);
            buf.append(pattern.charAt(++index));
            buf.append(pattern.charAt(++index));
            ch = pattern.charAt(++index);
          } else if (index + 7 < length &&
                     pattern.charAt(index + 1)=='\\' &&
                     pattern.charAt(index + 2)=='u' &&
                     pattern.charAt(index + 7)=='\'') {
            // eat simple escape character
            buf.append(ch);
            buf.append(pattern.charAt(++index));
            buf.append(pattern.charAt(++index));
            buf.append(pattern.charAt(++index));
            buf.append(pattern.charAt(++index));
            buf.append(pattern.charAt(++index));
            buf.append(pattern.charAt(++index));
            ch = pattern.charAt(++index);
          } else {
            // typed variable

            buf.append("$");
            if (miscBuffer == null) miscBuffer = new StringBuilder();
            else miscBuffer.setLength(0);

            // eat the name of typed var
            for(++index; index< length && Character.isJavaIdentifierPart(ch = pattern.charAt(index)); ++index) {
              miscBuffer.append(ch);
              buf.append(ch);
            }

            if (miscBuffer.length() == 0) throw new MalformedPatternException(SSRBundle.message("error.expected.character"));
            boolean anonymous = false;
            if (miscBuffer.charAt(0)=='_')  {
              anonymous = true;

              if(miscBuffer.length() == 1) {
                // anonymous var, make it unique for the case of constraints
                anonymousTypedVarsCount++;
                miscBuffer.append(anonymousTypedVarsCount);
                buf.append(anonymousTypedVarsCount);
              } else {
                buf.deleteCharAt(buf.length()-miscBuffer.length());
                miscBuffer.deleteCharAt(0);
              }
            }

            buf.append("$");
            String typedVar = miscBuffer.toString();
            int minOccurs = 1;
            int maxOccurs = 1;
            boolean greedy = true;
            MatchVariableConstraint constraint = options.getVariableConstraint(typedVar);
            boolean constraintCreated = false;

            if (constraint==null) {
              constraint = new MatchVariableConstraint();
              constraint.setName( typedVar );
              constraintCreated = true;
            }

            // Check the number of occurrences for typed variable
            final int savedIndex = index;
            if (index < length) {
              if (ch == '+') {
                maxOccurs = Integer.MAX_VALUE;
                ++index;
              } else if (ch == '?') {
                minOccurs = 0;
                ++index;
              } else if (ch == '*') {
                minOccurs = 0;
                maxOccurs = Integer.MAX_VALUE;
                ++index;
              } else if (ch == '{') {
                ++index;
                minOccurs = 0;
                while (index < length && (ch = pattern.charAt(index)) >= '0' && ch <= '9') {
                  minOccurs *= 10;
                  minOccurs += (ch - '0');
                  if (minOccurs < 0) throw new MalformedPatternException(SSRBundle.message("error.overflow"));
                  ++index;
                }

                if (ch==',') {
                  ++index;
                  maxOccurs = 0;

                  while (index < length && (ch = pattern.charAt(index)) >= '0' && ch <= '9') {
                    maxOccurs *= 10;
                    maxOccurs += (ch - '0');
                    if (maxOccurs < 0) throw new MalformedPatternException(SSRBundle.message("error.overflow"));
                    ++index;
                  }
                } else {
                  maxOccurs = Integer.MAX_VALUE;
                }

                if (ch != '}') {
                  if (maxOccurs == Integer.MAX_VALUE) throw new MalformedPatternException(SSRBundle.message("error.expected.brace1"));
                  else throw new MalformedPatternException(SSRBundle.message("error.expected.brace2"));
                }
                ++index;
              }

              if (index < length) {
                ch = pattern.charAt(index);
                if (ch=='?') {
                  greedy = false;
                  ++index;
                }
              }
            }

            if (constraintCreated) {
              constraint.setMinCount(minOccurs);
              constraint.setMaxCount(maxOccurs);
              constraint.setGreedy(greedy);
              constraint.setPartOfSearchResults(!anonymous);
              if (targetFound && !anonymous) {
                throw new MalformedPatternException(SSRBundle.message("error.only.one.target.allowed"));
              }
              targetFound = !anonymous;
            }
            else if (savedIndex != index) {
              throw new MalformedPatternException(SSRBundle.message("error.condition.only.on.first.variable.reference"));
            }

            if (index < length && pattern.charAt(index) == ':') {
              ++index;
              if (index >= length) throw new MalformedPatternException(SSRBundle.message("error.expected.condition", ":"));
              ch = pattern.charAt(index);
              if (ch == ':') {
                // double colon instead of condition
                buf.append(ch);
              }
              else {
                if (!constraintCreated)
                  throw new MalformedPatternException(SSRBundle.message("error.condition.only.on.first.variable.reference"));
                index = eatTypedVarCondition(index, pattern, miscBuffer, constraint);
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
        }

        buf.append(ch);
      }

      options.setSearchPattern( buf.toString() );
    }

  private static int eatTypedVarCondition(int index, String pattern, StringBuilder miscBuffer, MatchVariableConstraint constraint) {
    final int length = pattern.length();

    char ch = pattern.charAt(index);
    if (ch == '+' || ch == '*') {
      // this is type axis navigation relation
      switch(ch) {
        case '+':
          constraint.setStrictlyWithinHierarchy(true);
          break;
        case '*':
          constraint.setWithinHierarchy(true);
          break;
      }

      ++index;
      if (index >= length) throw new MalformedPatternException(SSRBundle.message("error.expected.condition", ch));
      ch = pattern.charAt(index);
    }

    if (ch == '[') {
      // eat complete condition
      miscBuffer.setLength(0);
      boolean quoted = false;
      while (++index < length) {
        ch = pattern.charAt(index);
        if (pattern.charAt(index - 1) != '\\') {
          if (ch == '"') quoted = !quoted;
          else if (ch == ']' && !quoted) break;
        }
        miscBuffer.append(ch);
      }
      if (quoted) throw new MalformedPatternException(SSRBundle.message("error.expected.value", "\""));
      if (ch != ']') throw new MalformedPatternException(SSRBundle.message("error.expected.condition.or.bracket"));
      ++index;
      parseCondition(constraint, miscBuffer.toString());
    }
    else {
      // eat reg exp constraint
      miscBuffer.setLength(0);
      index = handleRegExp(index, pattern, miscBuffer, constraint);
    }
    return index;
  }

  private static int handleRegExp(int index,
                                  String pattern,
                                  StringBuilder miscBuffer,
                                  MatchVariableConstraint constraint) {
    char c = pattern.charAt(index - 1);
    final int length = pattern.length();
    for(char ch; index < length && !Character.isWhitespace(ch = pattern.charAt(index)); ++index) {
      miscBuffer.append(ch);
    }

    if (miscBuffer.length() == 0)
      if (c == ':') throw new MalformedPatternException(SSRBundle.message("error.expected.condition", c));
      else return index;
    String regexp = miscBuffer.toString();

    if (constraint.getRegExp() != null && constraint.getRegExp().length() > 0 && !constraint.getRegExp().equals(regexp)) {
      throw new MalformedPatternException(SSRBundle.message("error.two.different.type.constraints"));
    }
    else {
      checkRegex(regexp);
      constraint.setRegExp(regexp);
    }

    return index;
  }

  private static void parseCondition(MatchVariableConstraint constraint, String condition) {
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
        text.setLength(0);
        int spaces = 0; // balance spaces surrounding content between parentheses
        while (++i < length && condition.charAt(i) == ' ') spaces++;
        i--;
        boolean quoted = false;
        boolean closed = false;
        while (++i < length) {
          c = condition.charAt(i);
          if (condition.charAt(i - 1) != '\\') {
            if (c == '"') quoted = !quoted;
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
        if (quoted) throw new MalformedPatternException(SSRBundle.message("error.expected.value", "\""));
        if (!closed) throw new MalformedPatternException(SSRBundle.message("error.expected.value",
                                                                           StringUtil.repeatSymbol(' ', spaces) + ")"));
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
        if (++i == length || condition.charAt(i) != '&' || optionExpected)
          throw new MalformedPatternException(SSRBundle.message("error.unexpected.value", "&"));
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
    else if (invert) throw new MalformedPatternException(SSRBundle.message("error.expected.condition", "!"));
    else if (optionExpected) throw new MalformedPatternException(SSRBundle.message("error.expected.condition", "&&"));
  }

  private static void handleOption(@NotNull MatchVariableConstraint constraint, @NotNull String option, @NotNull String argument,
                                   boolean invert) {
    argument = argument.trim();
    if (option.equalsIgnoreCase(REF)) {
      constraint.setReference(true);
      constraint.setInvertReference(invert);
      if (argument.length() == 0 || argument.charAt(0) != '\'')
        throw new MalformedPatternException(SSRBundle.message("error.reference.variable.name.expected", option));
      constraint.setNameOfReferenceVar(argument.substring(1));
    }
    else if (option.equalsIgnoreCase(READ)) {
      if (argument.length() != 0) throw new MalformedPatternException(SSRBundle.message("error.no.argument.expected", option));
      constraint.setReadAccess(true);
      constraint.setInvertReadAccess(invert);
    }
    else if (option.equalsIgnoreCase(WRITE)) {
      if (argument.length() != 0) throw new MalformedPatternException(SSRBundle.message("error.no.argument.expected", option));
      constraint.setWriteAccess(true);
      constraint.setInvertWriteAccess(invert);
    }
    else if (option.equalsIgnoreCase(REGEX) || option.equalsIgnoreCase(REGEXW)) {
      if (argument.length() == 0)
        throw new MalformedPatternException(SSRBundle.message("error.regular.expression.argument.expected", option));
      if (argument.charAt(0) == '*') {
        argument = argument.substring(1);
        constraint.setWithinHierarchy(true);
      }
      checkRegex(argument);
      constraint.setRegExp(argument);
      constraint.setInvertRegExp(invert);
      if (option.equalsIgnoreCase(REGEXW)) {
        constraint.setWholeWordsOnly(true);
      }
    }
    else if (option.equalsIgnoreCase(EXPRTYPE)) {
      if (argument.length() == 0)
        throw new MalformedPatternException(SSRBundle.message("error.regular.expression.argument.expected", option));
      if (argument.charAt(0) == '*') {
        argument = argument.substring(1);
        constraint.setExprTypeWithinHierarchy(true);
      }
      checkRegex(argument);
      constraint.setNameOfExprType(argument);
      constraint.setInvertExprType(invert);
    }
    else if (option.equalsIgnoreCase(FORMAL)) {
      if (argument.length() == 0)
        throw new MalformedPatternException(SSRBundle.message("error.regular.expression.argument.expected", option));
      if (argument.charAt(0) == '*') {
        argument = argument.substring(1);
        constraint.setFormalArgTypeWithinHierarchy(true);
      }
      checkRegex(argument);
      constraint.setNameOfFormalArgType(argument);
      constraint.setInvertFormalType(invert);
    }
    else if (option.equalsIgnoreCase(SCRIPT)) {
      if (argument.length() == 0) throw new MalformedPatternException(SSRBundle.message("error.script.argument.expected", option));
      if (invert) throw new MalformedPatternException(SSRBundle.message("error.cannot.invert", option));
      constraint.setScriptCodeConstraint(argument);
    }
    else if (option.equalsIgnoreCase(CONTAINS)) {
      if (argument.length() == 0) throw new MalformedPatternException(SSRBundle.message("error.pattern.argument.expected", option));
      constraint.setContainsConstraint(argument);
      constraint.setInvertContainsConstraint(invert);
    }
    else if (option.equalsIgnoreCase(WITHIN)) {
      if (!Configuration.CONTEXT_VAR_NAME.equals(constraint.getName()))
        throw new MalformedPatternException(SSRBundle.message("error.only.applicable.to.complete.match", option));
      if (argument.length() == 0) throw new MalformedPatternException(SSRBundle.message("error.pattern.argument.expected", option));
      constraint.setWithinConstraint(argument);
      constraint.setInvertWithinConstraint(invert);
    }
    else {
      throw new UnsupportedPatternException(SSRBundle.message("option.is.not.recognized.error.message", option));
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
}
