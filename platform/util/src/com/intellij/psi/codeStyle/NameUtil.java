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

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FList;
import com.intellij.util.text.CharArrayCharSequence;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NameUtil {
  private static final Function<String,String> LOWERCASE_MAPPING = new Function<String, String>() {
    public String fun(final String s) {
      return s.toLowerCase();
    }
  };
  private static final int MAX_LENGTH = 40;

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
    return buildRegexp(pattern, exactPrefixLen, allowToUpper, allowToLower, false, false);
  }

  public static String buildRegexp(String pattern,
                                   int exactPrefixLen,
                                   boolean allowToUpper,
                                   boolean allowToLower,
                                   boolean lowerCaseWords,
                                   boolean forCompletion) {
    final int eol = pattern.indexOf('\n');
    if (eol != -1) {
      pattern = pattern.substring(0, eol);
    }
    if (pattern.length() >= MAX_LENGTH) {
      pattern = pattern.substring(0, MAX_LENGTH);
    }

    @NonNls final StringBuilder buffer = new StringBuilder();
    boolean lastIsUppercase = false;
    boolean prevIsUppercase = false;
    final boolean endsWithSpace = !forCompletion && StringUtil.endsWithChar(pattern, ' ');
    if (!forCompletion) {
      pattern = pattern.trim();
    }
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
    //System.out.println("pattern = " + pattern);
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
            buffer.append("|[A-Za-z\\s0-9\\$]*[_-]+[");
            buffer.append(c);
            buffer.append(Character.toLowerCase(c));
            buffer.append("]");
          }
          buffer.append(')');
        }
        else if (Character.isLowerCase(c) && allowToUpper) {
          buffer.append('[');
          buffer.append(c);
          buffer.append(Character.toUpperCase(c));
          buffer.append(']');
          if (lowerCaseWords) {
            buffer.append("([a-z\\s0-9\\$]*[-_]+)?");
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
        buffer.append("([a-z\\s0-9\\$_-]*[\\ _-]+)+");
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

    //System.out.println("rx = " + buffer.toString());
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

  public static List<String> getSuggestionsByName(String name,
                                                   String prefix,
                                                   String suffix,
                                                   boolean upperCaseStyle,
                                                   boolean preferLongerNames, boolean isArray) {
    ArrayList<String> answer = new ArrayList<String>();
    String[] words = nameToWords(name);

    for (int step = 0; step < words.length; step++) {
      int wordCount = preferLongerNames ? words.length - step : step + 1;

      String startWord = words[words.length - wordCount];
      char c = startWord.charAt(0);
      if( c == '_' || !Character.isJavaIdentifierStart( c ) )
      {
        continue;
      }

      answer.add(compoundSuggestion(prefix, upperCaseStyle, words, wordCount, startWord, c, isArray, false) + suffix);
      answer.add(compoundSuggestion(prefix, upperCaseStyle, words, wordCount, startWord, c, isArray, true) + suffix);
    }
    return answer;
  }

  private static String compoundSuggestion(String prefix,
                                           boolean upperCaseStyle,
                                           String[] words,
                                           int wordCount,
                                           String startWord,
                                           char c,
                                           boolean isArray,
                                           boolean skip_) {
    StringBuilder buffer = new StringBuilder();

    buffer.append(prefix);

    if (upperCaseStyle) {
      startWord = StringUtilRt.toUpperCase(startWord);
    }
    else {
      if (prefix.length() == 0 || StringUtil.endsWithChar(prefix, '_')) {
        startWord = startWord.toLowerCase();
      }
      else {
        startWord = Character.toUpperCase(c) + startWord.substring(1);
      }
    }
    buffer.append(startWord);

    for (int i = words.length - wordCount + 1; i < words.length; i++) {
      String word = words[i];
      String prevWord = words[i - 1];
      if (upperCaseStyle) {
        word = StringUtilRt.toUpperCase(word);
        if (prevWord.charAt(prevWord.length() - 1) != '_' && word.charAt(0) != '_') {
          word = "_" + word;
        }
      }
      else {
        if (prevWord.charAt(prevWord.length() - 1) == '_') {
          word = word.toLowerCase();
        }

        if (skip_) {
          if (word.equals("_")) continue;
          if (prevWord.equals("_")) {
            word = StringUtil.capitalize(word);
          }
        }
      }
      buffer.append(word);
    }

    String suggestion = buffer.toString();
    if (isArray) {
      suggestion = StringUtil.pluralize(suggestion);
      if (upperCaseStyle) {
        suggestion = StringUtilRt.toUpperCase(suggestion);
      }
    }
    return suggestion;
  }

  private static boolean isWordStart(String text, int i) {
    if (isWordStart(text.charAt(i))) {
      return true;
    }
    if (!Character.isLetterOrDigit(text.charAt(i))) {
      return false;
    }
    if (i > 0 && (MinusculeMatcher.isWordSeparator(text.charAt(i - 1)) || text.charAt(i - 1) == '.')) {
      return true;
    }
    return false;
  }

  private static boolean isWordStart(char p) {
    return Character.isUpperCase(p) || Character.isDigit(p);
  }

  private static int nextWord(String text, int start) {
    if (!Character.isLetterOrDigit(text.charAt(start))) {
      return start + 1;
    }

    int i = start;
    while (i < text.length() && isWordStart(text, i)) {
      i++;
    }
    if (i > start + 1) {
      if (i == text.length() || !Character.isLetterOrDigit(text.charAt(i))) {
        return i;
      }
      return i - 1;
    }
    while (i < text.length() && !isWordStart(text, i) && !!Character.isLetterOrDigit(text.charAt(i))) {
      i++;
    }
    return i;
  }

  private static void addAllWords(String text, List<String> result) {
    int start = 0;
    while (start < text.length()) {
      int next = nextWord(text, start);
      result.add(text.substring(start, next));
      start = next;
    }
  }

  /**
   * @deprecated use com.intellij.util.text.Matcher
   */
  public interface Matcher {
    boolean matches(String name);
  }

  public static com.intellij.util.text.Matcher buildCompletionMatcher(String pattern, int exactPrefixLen, boolean allowToUpper, boolean allowToLower) {
    MatchingCaseSensitivity options = !allowToLower && !allowToUpper ? MatchingCaseSensitivity.ALL : exactPrefixLen > 0 ? MatchingCaseSensitivity.FIRST_LETTER : MatchingCaseSensitivity.NONE;
    return buildMatcher(pattern, options);
  }

  public static com.intellij.util.text.Matcher buildMatcher(String pattern, int exactPrefixLen, boolean allowToUpper, boolean allowToLower) {
    MatchingCaseSensitivity options = !allowToLower && !allowToUpper ? MatchingCaseSensitivity.ALL : exactPrefixLen > 0 ? MatchingCaseSensitivity.FIRST_LETTER : MatchingCaseSensitivity.NONE;
    return buildMatcher(pattern, options);
  }

  @SuppressWarnings("UnusedParameters")
  @Deprecated
  public static com.intellij.util.text.Matcher buildMatcher(String pattern, int exactPrefixLen, boolean allowToUpper, boolean allowToLower, boolean lowerCaseWords) {
    MatchingCaseSensitivity options = !allowToLower && !allowToUpper ? MatchingCaseSensitivity.ALL : exactPrefixLen > 0 ? MatchingCaseSensitivity.FIRST_LETTER : MatchingCaseSensitivity.NONE;
    return buildMatcher(pattern, options);
  }

  public static MinusculeMatcher buildMatcher(final String pattern, MatchingCaseSensitivity options) {
    return new MinusculeMatcher(pattern, options);
  }

  public enum MatchingCaseSensitivity {
    NONE, FIRST_LETTER, ALL
  }

  public static class MinusculeMatcher implements com.intellij.util.text.Matcher {
    private final char[] myPattern;
    private final MatchingCaseSensitivity myOptions;

    public MinusculeMatcher(String pattern, MatchingCaseSensitivity options) {
      myOptions = options;
      myPattern = StringUtil.trimEnd(pattern, "* ").replaceAll(":", "\\*:").toCharArray();
    }

    @Nullable
    private FList<TextRange> matchName(String name, int patternIndex, int nameIndex) {
      if (patternIndex == myPattern.length) {
        return FList.emptyList();
      }
      if ('*' == myPattern[patternIndex]) {
        return skipChars(name, patternIndex, nameIndex, true);
      }
      if (nameIndex == name.length()) {
        return null;
      }

      if ('.' == myPattern[patternIndex] && name.charAt(nameIndex) != '.') {
        return skipChars(name, patternIndex, nameIndex, false);
      }

      if (patternIndex == 0 && myOptions != MatchingCaseSensitivity.NONE && name.charAt(nameIndex) != myPattern[0]) {
        return null;
      }

      if (' ' == myPattern[patternIndex] && patternIndex != myPattern.length - 1) {
        return skipWords(name, patternIndex, nameIndex);
      }
      if (isWordSeparator(name.charAt(nameIndex))) {
        return skipSeparators(name, patternIndex, nameIndex);
      }

      if (StringUtil.toLowerCase(name.charAt(nameIndex)) != StringUtil.toLowerCase(myPattern[patternIndex])) {
        if (Character.isDigit(name.charAt(nameIndex)) || (name.charAt(nameIndex) == '.' && name.indexOf('.', nameIndex + 1) > 0)) {
          return matchName(name, patternIndex, nameIndex + 1);
        }
        return null;
      }

      if (myOptions == MatchingCaseSensitivity.ALL && name.charAt(nameIndex) != myPattern[patternIndex]) {
        return null;
      }

      int nextStart = NameUtil.nextWord(name, nameIndex);

      int lastUpper = isWordStart(myPattern[patternIndex]) ? 0 : -1;

      int i = 1;
      while (true) {
        if (patternIndex + i == myPattern.length || i + nameIndex == nextStart) {
          break;
        }
        char p = myPattern[patternIndex + i];
        char w = name.charAt(i + nameIndex);
        if (lastUpper == i - 1 && isWordStart(p) && myOptions != MatchingCaseSensitivity.ALL) {
          if (p == w) {
            lastUpper = i;
          }
          p = StringUtil.toLowerCase(p);
        }

        if (myOptions != MatchingCaseSensitivity.ALL) {
          w = StringUtil.toLowerCase(w);
        }
        if (w != p) {
          break;
        }
        i++;
      }

      if (isFinalSpaceMatch(name, patternIndex, nameIndex, nextStart, i)) {
        return FList.<TextRange>emptyList().prepend(TextRange.from(nameIndex, i));
      }

      return matchAfterFragment(name, patternIndex, nameIndex, nextStart, lastUpper, i);
    }

    private boolean isFinalSpaceMatch(String name, int patternIndex, int nameIndex, int nextStart, int i) {
      return nextStart == name.length() &&
          patternIndex + i == myPattern.length - 1 &&
          ' ' == myPattern[patternIndex + i] &&
          (i == 1 && isWordStart(myPattern[patternIndex]) || i + nameIndex == name.length());
    }

    @Nullable
    private FList<TextRange> matchAfterFragment(String name, int patternIndex, int nameIndex, int nextStart, int lastUpper, int matchLen) {
      boolean star = patternIndex + matchLen < myPattern.length && myPattern[patternIndex + matchLen] == '*';
      if (lastUpper >= 0) {
        FList<TextRange> ranges = matchName(name, patternIndex + lastUpper + 1, star && matchLen == lastUpper ? nameIndex + lastUpper : nextStart);
        if (ranges != null) {
          return prependRange(ranges, nameIndex, lastUpper + 1);
        }
      }

      int trial = matchLen;
      while (trial > 0) {
        FList<TextRange> ranges = matchName(name, patternIndex + trial, nextStart);
        if (ranges != null) {
          return prependRange(ranges, nameIndex, trial);
        }
        trial--;
      }

      FList<TextRange> ranges = matchName(name, patternIndex + matchLen, nameIndex + matchLen);
      if (ranges != null) {
        return prependRange(ranges, nameIndex, matchLen);
      }
      return null;
    }

    private static FList<TextRange> prependRange(FList<TextRange> ranges, int from, int length) {
      TextRange head = ranges.getHead();
      if (head != null && head.getStartOffset() == from + length) {
        return ranges.getTail().prepend(new TextRange(from, head.getEndOffset()));
      }
      return ranges.prepend(TextRange.from(from, length));
    }

    private static boolean isWordSeparator(char c) {
      return Character.isWhitespace(c) || c == '_' || c == '-' || c == ':';
    }

    @Nullable
    private FList<TextRange> skipSeparators(String name, int patternIndex, int nameIndex) {
      int nextStart = NameUtil.nextWord(name, nameIndex);
      assert nextStart - nameIndex == 1 : "'" + name + "'" + nameIndex + " " + nextStart;
      char p = myPattern[patternIndex];
      if (isWordSeparator(p)) {
        if (myOptions != MatchingCaseSensitivity.NONE &&
            nameIndex == 0 && name.length() > 1 && patternIndex + 1 < myPattern.length &&
            isWordSeparator(name.charAt(1)) && !isWordSeparator(myPattern[patternIndex + 1])) {
          return null;
        }

        final FList<TextRange> ranges = matchName(name, patternIndex + 1, nextStart);
        if (ranges != null) {
          return prependRange(ranges, nameIndex, 1);
        }

        return null;
      }

      return matchName(name, patternIndex, nextStart);
    }

    @Nullable
    private FList<TextRange> skipChars(String name, int patternIndex, int nameIndex, boolean maySkipNextChar) {
      while ('*' == myPattern[patternIndex]) {
        patternIndex++;
        if (patternIndex == myPattern.length) {
          return FList.emptyList();
        }
      }

      String nextChar = String.valueOf(myPattern[patternIndex]);
      boolean upper = Character.isUpperCase(myPattern[patternIndex]);

      int fromIndex = nameIndex;
      while (true) {
        int next = StringUtil.indexOfIgnoreCase(name, nextChar, fromIndex);
        if (next < 0) {
          break;
        }
        if (upper && next > 0 && !Character.isUpperCase(name.charAt(next))) {
          fromIndex = next + 1;
          continue;
        }

        FList<TextRange> ranges = matchName(name, patternIndex, next);
        if (ranges != null) {
          return ranges;
        }
        if (!maySkipNextChar) {
          return null;
        }
        fromIndex = next + 1;
      }
      return null;
    }

    @Nullable
    private FList<TextRange> skipWords(String name, int patternIndex, int nameIndex) {
      while (' ' == myPattern[patternIndex]) {
        patternIndex++;
        if (patternIndex == myPattern.length) {
          return null;
        }
      }

      if (nameIndex == 0 || isWordStart(name, nameIndex)) {
        FList<TextRange> ranges = matchName(name, patternIndex, nameIndex);
        if (ranges != null) {
          return ranges;
        }
      }

      int fromIndex = nameIndex;
      while (fromIndex < name.length()) {
        int next = isWordSeparator(myPattern[patternIndex]) ? name.indexOf(myPattern[patternIndex], fromIndex) : nextWord(name, fromIndex);
        if (next < 0) {
          break;
        }

        FList<TextRange> ranges = matchName(name, patternIndex, next);
        if (ranges != null) {
          return ranges;
        }
        fromIndex = next;
      }
      return null;
    }

    public int matchingDegree(String name) {
      Iterable<TextRange> iterable = matchingFragments(name);
      if (iterable == null) return Integer.MIN_VALUE;

      int fragmentCount = 0;
      int matchingCaps = 0;
      CharArrayCharSequence seq = new CharArrayCharSequence(myPattern);
      int p = -1;
      TextRange first = null;
      for (TextRange range : iterable) {
        if (first == null) {
          first = range;
        }
        for (int i = range.getStartOffset(); i < range.getEndOffset(); i++) {
          char c = name.charAt(i);
          p = StringUtil.indexOf(seq, c, p + 1, myPattern.length, false);
          if (p < 0) {
            break;
          }
          if (Character.isUpperCase(myPattern[p])) {
            matchingCaps += c == myPattern[p] ? 1 : -1;
          }
        }
        fragmentCount++;
      }

      int commonStart = 0;
      while (commonStart < name.length() && commonStart < myPattern.length && name.charAt(commonStart) == myPattern[commonStart]) {
        commonStart++;
      }

      boolean prefixMatching = first != null && first.getStartOffset() == 0;
      boolean middleWordStart = first != null && first.getStartOffset() > 0 && isWordStart(name, first.getStartOffset());

      return -fragmentCount + matchingCaps * 10 + commonStart + (prefixMatching ? 2 : middleWordStart ? 1 : 0) * 100;
    }

    @Override
    public boolean matches(String name) {
      return matchingFragments(name) != null;
    }

    @Nullable
    public Iterable<TextRange> matchingFragments(String name) {
      if (name.isEmpty()) {
        return myPattern.length == 0 ? Collections.<TextRange>emptyList() : null;
      }

      return matchName(name, 0, 0);
    }
  }
}
