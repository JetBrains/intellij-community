/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.apache.oro.text.regex.MalformedPatternException;
import org.apache.oro.text.regex.Pattern;
import org.apache.oro.text.regex.Perl5Compiler;
import org.apache.oro.text.regex.Perl5Matcher;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.List;

public class NameUtil {
  private static final Function<String,String> LOWERCASE_MAPPING = new Function<String, String>() {
    public String fun(final String s) {
      return s.toLowerCase();
    }
  };

  private NameUtil() {}

  public static List<String> nameToWordsLowerCase(String name){
    return ContainerUtil.map(nameToWords(name), LOWERCASE_MAPPING);
  }

  public static String[] nameToWords(String name){
    ArrayList<String> array = new ArrayList<String>();
    int index = 0;
    int wordStart;

    while(index < name.length()){
      wordStart = index;
      int upperCaseCount = 0;
      int lowerCaseCount = 0;
      int digitCount = 0;
      int specialCount = 0;
      while(index < name.length()){
        char c = name.charAt(index);
        if (Character.isDigit(c)){
          if (upperCaseCount > 0 || lowerCaseCount > 0 || specialCount > 0) break;
          digitCount++;
        }
        else if (Character.isUpperCase(c)){
          if (lowerCaseCount > 0 || digitCount > 0 || specialCount > 0) break;
          upperCaseCount++;
        }
        else if (Character.isLowerCase(c)){
          if (digitCount > 0 || specialCount > 0) break;
          if (upperCaseCount > 1) {
            index--;
            break;
          }
          lowerCaseCount++;
        }
        else{
          if (upperCaseCount > 0 || lowerCaseCount > 0 || digitCount > 0) break;
          specialCount++;
        }
        index++;
      }
      String word = name.substring(wordStart, index);
      array.add(word);
    }
    return ArrayUtil.toStringArray(array);
  }

  public static String buildRegexp(String pattern, int exactPrefixLen, boolean allowToUpper, boolean allowToLower) {
    return buildRegexp(pattern, exactPrefixLen, allowToUpper, allowToLower, false);
  }

  public static String buildRegexp(String pattern, int exactPrefixLen, boolean allowToUpper, boolean allowToLower, boolean lowerCaseWords) {
    final int eol = pattern.indexOf('\n');
    if (eol != -1) {
      pattern = pattern.substring(0, eol);
    }
    if (pattern.length() >= 80) {
      pattern = pattern.substring(0, 80);
    }

    @NonNls final StringBuffer buffer = new StringBuffer();
    boolean lastIsUppercase = false;
    boolean prevIsUppercase = false;
    final boolean endsWithSpace = StringUtil.endsWithChar(pattern, ' ');
    pattern = pattern.trim();
    exactPrefixLen = Math.min(exactPrefixLen, pattern.length());
    /*final boolean uppercaseOnly = containsOnlyUppercaseLetters(pattern.substring(exactPrefixLen));
    if (uppercaseOnly) {
      allowToLower = false;
    }*/
    if (exactPrefixLen > 0) {
      char c = pattern.charAt(exactPrefixLen - 1);
      prevIsUppercase = Character.isUpperCase(c) || Character.isDigit(c);
    }

    for (int i = 0; i != exactPrefixLen; ++i) {
      final char c = pattern.charAt(i);
      if (Character.isLetterOrDigit(c)) {
        buffer.append(c);
      }
      else {
        // for standard RegExp engine
        // buffer.append("\\u");
        // buffer.append(Integer.toHexString(c + 0x20000).substring(1));

        // for OROMATCHER RegExp engine
        buffer.append("\\").append(c);
        //buffer.append(Integer.toHexString(c + 0x20000).substring(2));
      }
    }

    if (exactPrefixLen == 0) {
      buffer.append("_*");  // ignore leading underscores
    }

    boolean firstIdentifierLetter = (exactPrefixLen == 0);
    for (int i = exactPrefixLen; i < pattern.length(); i++) {
      final char c = pattern.charAt(i);
      lastIsUppercase = false;
      if (Character.isLetterOrDigit(c)) {
        prevIsUppercase = false;

        // This logic allows to use uppercase letters only to catch the name like PDM for PsiDocumentManager
        if (Character.isUpperCase(c) || Character.isDigit(c)) {
          prevIsUppercase = true;
          lastIsUppercase = true;

          buffer.append('(');

          if (!firstIdentifierLetter) {
            buffer.append("[a-z\\s0-9\\$]*");
          }

          buffer.append(c);
          if (allowToLower) {
            buffer.append('|');
            buffer.append(Character.toLowerCase(c));
          }
          if (!firstIdentifierLetter) {
            buffer.append("|([A-Za-z\\s0-9\\$]*(_|-)(");
            buffer.append(c);
            buffer.append("|");
            buffer.append(Character.toLowerCase(c));
            buffer.append("))");
          }
          buffer.append(')');
        }
        else if (Character.isLowerCase(c) && allowToUpper) {
          buffer.append('[');
          buffer.append(c);
          buffer.append(Character.toUpperCase(c));
          buffer.append(']');
          if (lowerCaseWords) {
            buffer.append("([a-z\\s0-9\\$]*[-_])?");
          }
        }
        else {
          buffer.append(c);
        }

        firstIdentifierLetter = false;
      }
      else if (c == '*') {
        buffer.append(".*");
        firstIdentifierLetter = true;
      }
      else if (c == '.') {
        if (!firstIdentifierLetter) {
          buffer.append("[a-z\\s0-9\\$]*\\.");
        }
        else {
          buffer.append("\\.");
        }
        firstIdentifierLetter = true;
      }
      else if (c == ' ') {
        buffer.append("([a-z\\s0-9\\$_-]*[\\ _-])+");
        firstIdentifierLetter = true;
      }
      else {
        if (c == ':' || prevIsUppercase) {
          buffer.append("[A-Za-z\\s0-9\\$]*");
        }

        firstIdentifierLetter = true;
        // for standard RegExp engine
        // buffer.append("\\u");
        // buffer.append(Integer.toHexString(c + 0x20000).substring(1));

        // for OROMATCHER RegExp engine
        buffer.append("\\").append(c);
        //buffer.append(Integer.toHexString(c + 0x20000).substring(3));
      }
    }

    if (!endsWithSpace) {
      buffer.append(".*");
    }
    else if (lastIsUppercase) {
      buffer.append("[a-z\\s0-9\\$]*");
    }

    return buffer.toString();
  }

  /**
   * Splits an identifier into words, separated with underscores or upper-case characters
   * (camel-case).
   *
   * @param name the identifier to split.
   * @return the array of strings into which the identifier has been split.
   */
  public static String[] splitNameIntoWords(@NotNull String name) {
    final String[] underlineDelimited = name.split("_");
    List<String> result = new ArrayList<String>();
    for (String word : underlineDelimited) {
      addAllWords(word, result);
    }
    return ArrayUtil.toStringArray(result);
  }

  private enum WordState { NO_WORD, PREV_UC, WORD }

  private static void addAllWords(String word, List<String> result) {
    CharacterIterator it = new StringCharacterIterator(word);
    StringBuffer b = new StringBuffer();
    WordState state = WordState.NO_WORD;
    char curPrevUC = '\0';
    for (char c = it.first(); c != CharacterIterator.DONE; c = it.next()) {
      switch (state) {
        case NO_WORD:
          if (!Character.isUpperCase(c)) {
            b.append(c);
            state = WordState.WORD;
          }
          else {
            state = WordState.PREV_UC;
            curPrevUC = c;
          }
          break;
        case PREV_UC:
          if (!Character.isUpperCase(c)) {
            b = startNewWord(result, b, curPrevUC);
            b.append(c);
            state = WordState.WORD;
          }
          else {
            b.append(curPrevUC);
            state = WordState.PREV_UC;
            curPrevUC = c;
          }
          break;
        case WORD:
          if (Character.isUpperCase(c)) {
            startNewWord(result, b, c);
            b.setLength(0);
            state = WordState.PREV_UC;
            curPrevUC = c;
          }
          else {
            b.append(c);
          }
          break;
      }
    }
    if (state == WordState.PREV_UC) {
      b.append(curPrevUC);
    }
    result.add(b.toString());
  }

  private static StringBuffer startNewWord(List<String> result, StringBuffer b, char c) {
    if (b.length() > 0) {
      result.add(b.toString());
    }
    b = new StringBuffer();
    b.append(c);
    return b;
  }

  public interface Matcher {
    boolean matches(String name);
  }

  public static Matcher buildMatcher(String pattern, int exactPrefixLen, boolean allowToUpper, boolean allowToLower) {
    return buildMatcher(pattern, buildRegexp(pattern, exactPrefixLen, allowToUpper, allowToLower));
  }

  public static Matcher buildMatcher(String pattern, int exactPrefixLen, boolean allowToUpper, boolean allowToLower, boolean lowerCaseWords) {
    return buildMatcher(pattern, buildRegexp(pattern, exactPrefixLen, allowToUpper, allowToLower, lowerCaseWords));
  }

  private static Matcher buildMatcher(String pattern, String regexp) {
    return new OptimizedMatcher(pattern, regexp);
  }

  private static class OptimizedMatcher implements Matcher {
    private final char[] myPreparedPattern;
    private final boolean myEnsureFirstSymbolsMatch;
    private final Perl5Matcher myMatcher;
    private final Pattern myPattern;

    public OptimizedMatcher(String pattern, String regexp) {
      myPreparedPattern = preparePattern(pattern).toCharArray();
      myEnsureFirstSymbolsMatch = pattern.length() > 0 && Character.isLetterOrDigit(pattern.charAt(0));
      try {
        myPattern = new Perl5Compiler().compile(regexp);
      }
      catch (MalformedPatternException e) {
        throw new RuntimeException(e);
      }
      myMatcher = new Perl5Matcher();
    }

    public boolean matches(String name) {
      if (!prefilter(name, myPreparedPattern)) {
        return false;
      }

      return myMatcher.matches(name, myPattern);
    }

    private static String preparePattern(String pattern) {
      StringBuilder builder = new StringBuilder();
      for (int i = 0; i < pattern.length(); i++) {
        char c = pattern.charAt(i);
        if (Character.isLetterOrDigit(c)) {
          builder.append(StringUtil.toLowerCase(c));
        }
      }
      return builder.toString();
    }

    /*
     * Don't try regexp matcher on names, which do not contain all the alphanumerics from the pattern in pattern's original order.
     */
    private boolean prefilter(String name, char[] pattern) {
      int patternIndex = 0;
      int nameIndex = 0;

      int patternLen = pattern.length;
      int nameLen = name.length();

      if (myEnsureFirstSymbolsMatch) {
        while (nameIndex < nameLen && name.charAt(nameIndex) == '_') {
          nameIndex++;
        }

        if (patternLen == 0 || nameIndex >= nameLen) return false;
        if (StringUtil.toLowerCase(name.charAt(nameIndex)) != pattern[0]) return false;

        nameIndex++;
        patternIndex++;
      }

      while (patternIndex < patternLen) {
        char c = pattern[patternIndex++];

        while (true) {
          if (nameIndex >= nameLen) return false;
          if (StringUtil.toLowerCase(name.charAt(nameIndex++)) == c) break;
        }
      }

      return true;
    }
  }
}
