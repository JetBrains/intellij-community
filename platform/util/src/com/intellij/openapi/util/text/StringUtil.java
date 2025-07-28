// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.text;

import com.intellij.ReviseWhenPortedToJDK;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FreezableArrayList;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.CharSequenceSubSequence;
import com.intellij.util.text.MergingCharSequence;
import com.intellij.util.text.VersionComparatorUtil;
import kotlin.jvm.internal.Ref;
import org.jetbrains.annotations.*;
import org.jetbrains.annotations.ApiStatus.NonExtendable;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import java.beans.Introspector;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;

//TeamCity inherits StringUtil: do not add private constructors!!!
@SuppressWarnings({"NonFinalUtilityClass", "UtilityClassWithPublicConstructor"})
@NonExtendable
public class StringUtil {

  /**
   * @deprecated do not inherit from this class
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  public StringUtil() { }

  @SuppressWarnings("UnnecessaryUnicodeEscape")
  public static final String ELLIPSIS = "\u2026";
  public static final String THREE_DOTS = "...";
  public static final String NON_BREAK_SPACE = "\u00A0";

  // "(?>" removes backtraces; this is not just important for matching speed, but also to stop stack overflows
  private static final Pattern HTML_PATTERN = Pattern.compile("(?><[^>]*>)", Pattern.MULTILINE);

  private static final Pattern BREAKS_PATTERN = Pattern.compile("(?><[bB][rR](?>\\s*)/?\\s*>)");

  private static final class Splitters {
    private static final Pattern EOL_SPLIT_KEEP_SEPARATORS = Pattern.compile("(?<=(\r\n|\n))|(?<=\r)(?=[^\n])");
    private static final Pattern EOL_SPLIT_PATTERN = Pattern.compile(" *(\r|\n|\r\n)+ *");
    private static final Pattern EOL_SPLIT_PATTERN_WITH_EMPTY = Pattern.compile(" *(\r|\n|\r\n) *");
    private static final Pattern EOL_SPLIT_DONT_TRIM_PATTERN = Pattern.compile("(\r|\n|\r\n)+");
  }

  /**
   * @return a lightweight CharSequence which results from replacing {@code [start, end)} range in the {@code charSeq} with {@code replacement}.
   * Works in O(1), but retains references to the passed char sequences, so please use something else if you want them to be garbage-collected.
   */
  @Contract(pure = true)
  public static @NotNull MergingCharSequence replaceSubSequence(@NotNull CharSequence charSeq, int start, int end, @NotNull CharSequence replacement) {
    return new MergingCharSequence(
          new MergingCharSequence(new CharSequenceSubSequence(charSeq, 0, start), replacement),
          new CharSequenceSubSequence(charSeq, end, charSeq.length()));
  }

  private static final class MyHtml2Text extends HTMLEditorKit.ParserCallback {
    private final @NotNull StringBuilder myBuffer = new StringBuilder();
    private final boolean myIsSkipStyleTag;

    private boolean myIsStyleTagOpened;

    private MyHtml2Text(boolean isSkipStyleTag) {
      myIsSkipStyleTag = isSkipStyleTag;
    }

    void parse(@NotNull Reader in) throws IOException {
      myBuffer.setLength(0);
      new ParserDelegator().parse(in, this, Boolean.TRUE);
    }

    @Override
    public void handleText(char @NotNull [] text, int pos) {
      if (!myIsStyleTagOpened) {
        myBuffer.append(text);
      }
    }

    @Override
    public void handleStartTag(@NotNull HTML.Tag tag, MutableAttributeSet set, int i) {
      if (myIsSkipStyleTag && "style".equals(tag.toString())) {
        myIsStyleTagOpened = true;
      }
      handleTag(tag);
    }

    @Override
    public void handleEndTag(@NotNull HTML.Tag tag, int pos) {
      if (myIsSkipStyleTag && "style".equals(tag.toString())) {
        myIsStyleTagOpened = false;
      }
    }

    @Override
    public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet set, int i) {
      handleTag(tag);
    }

    private void handleTag(@NotNull HTML.Tag tag) {
      if (tag.breaksFlow() && myBuffer.length() > 0) {
        myBuffer.append(System.lineSeparator());
      }
    }

    @NotNull String getText() {
      return myBuffer.toString();
    }
  }

  public static final java.util.function.Function<String, String> QUOTER = s -> "\"" + s + "\"";

  public static final java.util.function.Function<String, String> SINGLE_QUOTER = s -> "'" + s + "'";

  @Contract(pure = true)
  public static @Unmodifiable @NotNull List<String> getWordsInStringLongestFirst(@NotNull String find) {
    List<String> words = getWordsIn(find);
    // hope long words are rare
    return ContainerUtil.sorted(words, (o1, o2) -> o2.length() - o1.length());
  }

  @Contract(pure = true)
  public static @NotNull String escapePattern(@NotNull String text) {
    return replace(replace(text, "'", "''"), "{", "'{'");
  }

  /**
   * @deprecated use {@link Object#toString()} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  @Contract(pure = true)
  public static @NotNull <T> Function<T, String> createToStringFunction(@SuppressWarnings("unused") @NotNull Class<T> cls) {
    return Object::toString;
  }

  // Unlike String.replace(CharSequence,CharSequence) does not allocate intermediate objects on non-match
  // TODO revise when JDK9 arrives - its String.replace(CharSequence, CharSequence) is more optimized
  @ReviseWhenPortedToJDK("9")
  @Contract(pure = true)
  public static @NotNull String replace(@NotNull String text, @NotNull String oldS, @NotNull String newS) {
    return replace(text, oldS, newS, false);
  }

  @Contract(pure = true)
  public static @NotNull String replaceIgnoreCase(@NotNull String text, @NotNull String oldS, @NotNull String newS) {
    return replace(text, oldS, newS, true);
  }

  /**
   * @deprecated Use {@link String#replace(char,char)} instead
   */
  @Contract(pure = true)
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static @NotNull String replaceChar(@NotNull String buffer, char oldChar, char newChar) {
    return buffer.replace(oldChar, newChar);
  }

  @Contract(pure = true)
  public static String replace(@NotNull String text, @NotNull String oldS, @NotNull String newS, boolean ignoreCase) {
    if (text.length() < oldS.length()) return text;

    StringBuilder newText = null;
    int i = 0;

    while (i < text.length()) {
      int index = ignoreCase? indexOfIgnoreCase(text, oldS, i) : text.indexOf(oldS, i);
      if (index < 0) {
        if (i == 0) {
          return text;
        }

        newText.append(text, i, text.length());
        break;
      }
      else {
        if (newText == null) {
          if (text.length() == oldS.length()) {
            return newS;
          }
          newText = new StringBuilder(text.length() - i);
        }

        newText.append(text, i, index);
        newText.append(newS);
        i = index + oldS.length();
      }
    }
    return newText != null ? newText.toString() : "";
  }

  @Contract(pure = true)
  public static int indexOfIgnoreCase(@NotNull String where, @NotNull String what, int fromIndex) {
    return Strings.indexOfIgnoreCase(where, what, fromIndex);
  }

  /**
   * Implementation copied from {@link String#indexOf(String, int)} except character comparisons made case-insensitive
   */
  @Contract(pure = true)
  public static int indexOfIgnoreCase(@NotNull CharSequence where, @NotNull CharSequence what, int fromIndex) {
    return Strings.indexOfIgnoreCase(where, what, fromIndex);
  }

  @Contract(pure = true)
  public static int indexOfIgnoreCase(@NotNull String where, char what, int fromIndex) {
    return Strings.indexOfIgnoreCase(where, what, fromIndex);
  }

  @Contract(pure = true)
  public static int lastIndexOfIgnoreCase(@NotNull String where, char c, int fromIndex) {
    for (int i = Math.min(fromIndex, where.length() - 1); i >= 0; i--) {
      if (charsEqualIgnoreCase(where.charAt(i), c)) {
        return i;
      }
    }

    return -1;
  }

  @Contract(pure = true)
  public static boolean containsIgnoreCase(@NotNull String where, @NotNull String what) {
    return indexOfIgnoreCase(where, what, 0) >= 0;
  }

  @Contract(pure = true)
  public static boolean endsWithIgnoreCase(@NotNull String str, @NotNull String suffix) {
    return Strings.endsWithIgnoreCase(str, suffix);
  }

  @Contract(pure = true)
  public static boolean startsWithIgnoreCase(@NotNull String str, @NotNull String prefix) {
    return StringUtilRt.startsWithIgnoreCase(str, prefix);
  }

  @Contract(pure = true)
  public static @NotNull String stripHtml(@NotNull String html, boolean convertBreaks) {
    return stripHtml(html, convertBreaks ? "\n\n" : null);
  }

  @Contract(pure = true)
  public static @NotNull String stripHtml(@NotNull String html, @Nullable String breaks) {
    if (breaks != null) {
      html = BREAKS_PATTERN.matcher(html).replaceAll(breaks);
    }

    return HTML_PATTERN.matcher(html).replaceAll("");
  }

  @Contract(value = "null -> null; !null -> !null", pure = true)
  public static String toLowerCase(@Nullable String str) {
    return Strings.toLowerCase(str);
  }

  @Contract(pure = true)
  public static @NlsSafe @NotNull String getPackageName(@NotNull String fqName) {
    return getPackageName(fqName, '.');
  }

  /**
   * Given a fqName returns the package name for the type or the containing type.
   * <p/>
   * <ul>
   * <li>{@code java.lang.String} -> {@code java.lang}</li>
   * <li>{@code java.util.Map.Entry} -> {@code java.util.Map}</li>
   * </ul>
   *
   * @param fqName    a fully qualified type name. Not supposed to contain any type arguments
   * @param separator the separator to use. Typically, '.'
   * @return the package name of the type or the declarator of the type. The empty string if the given fqName is unqualified
   */
  @Contract(pure = true)
  public static @NlsSafe @NotNull String getPackageName(@NotNull String fqName, char separator) {
    int lastPointIdx = fqName.lastIndexOf(separator);
    if (lastPointIdx >= 0) {
      return fqName.substring(0, lastPointIdx);
    }
    return "";
  }

  @Contract(pure = true)
  public static int getLineBreakCount(@NotNull CharSequence text) {
    return StringUtilKmp.getLineBreakCount(text);
  }

  @Contract(pure = true)
  public static boolean containsLineBreak(@NotNull CharSequence text) {
    return StringUtilKmp.containsLineBreak(text);
  }

  @Contract(pure = true)
  public static boolean isLineBreak(char c) {
    return StringUtilKmp.isLineBreak(c);
  }

  @Contract(pure = true)
  public static @NotNull String escapeLineBreak(@NotNull String text) {
    StringBuilder buffer = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        case '\n':
          buffer.append("\\n");
          break;
        case '\r':
          buffer.append("\\r");
          break;
        default:
          buffer.append(c);
      }
    }
    return buffer.toString();
  }

  @Contract(pure = true)
  public static boolean endsWithLineBreak(@NotNull CharSequence text) {
    int len = text.length();
    return len > 0 && isLineBreak(text.charAt(len - 1));
  }

  @Contract(pure = true)
  public static int lineColToOffset(@NotNull CharSequence text, int line, int col) {
    int curLine = 0;
    int offset = 0;
    while (line != curLine) {
      if (offset == text.length()) return -1;
      char c = text.charAt(offset);
      if (c == '\n') {
        curLine++;
      }
      else if (c == '\r') {
        curLine++;
        if (offset < text.length() - 1 && text.charAt(offset + 1) == '\n') {
          offset++;
        }
      }
      offset++;
    }
    return offset + col;
  }

  @Contract(pure = true)
  public static int offsetToLineNumber(@NotNull CharSequence text, int offset) {
    LineColumn lineColumn = offsetToLineColumn(text, offset);
    return lineColumn != null ? lineColumn.line : -1;
  }

  @Contract(pure = true)
  public static LineColumn offsetToLineColumn(@NotNull CharSequence text, int offset) {
    int curLine = 0;
    int curLineStart = 0;
    int curOffset = 0;
    while (curOffset < offset) {
      if (curOffset == text.length()) return null;
      char c = text.charAt(curOffset);
      if (c == '\n') {
        curLine++;
        curLineStart = curOffset + 1;
      }
      else if (c == '\r') {
        curLine++;
        if (curOffset < text.length() - 1 && text.charAt(curOffset + 1) == '\n') {
          curOffset++;
        }
        curLineStart = curOffset + 1;
      }
      curOffset++;
    }

    return LineColumn.of(curLine, offset - curLineStart);
  }

  /**
   * Classic dynamic programming algorithm for string differences.
   */
  @Contract(pure = true)
  public static int difference(@NotNull String s1, @NotNull String s2) {
    int[][] a = new int[s1.length()][s2.length()];

    for (int i = 0; i < s1.length(); i++) {
      a[i][0] = i;
    }

    for (int j = 0; j < s2.length(); j++) {
      a[0][j] = j;
    }

    for (int i = 1; i < s1.length(); i++) {
      for (int j = 1; j < s2.length(); j++) {

        a[i][j] = Math.min(Math.min(a[i - 1][j - 1] + (s1.charAt(i) == s2.charAt(j) ? 0 : 1), a[i - 1][j] + 1), a[i][j - 1] + 1);
      }
    }

    return a[s1.length() - 1][s2.length() - 1];
  }

  /**
   * NOTE: This method only works somewhat well for sentences in the English language.
   * Other languages have other capitalization rules. Do not use it for non-English text.
   */
  @Contract(pure = true)
  public static @NotNull String wordsToBeginFromUpperCase(@NotNull String s) {
    return fixCapitalization(s, ourLowerCaseWords, true, true);
  }

  @Contract(pure = true)
  public static @NotNull String wordsToBeginFromLowerCase(@NotNull String s) {
    return fixCapitalization(s, ArrayUtilRt.EMPTY_STRING_ARRAY, false, true);
  }

  /**
   * Does not actually convert to a title case, but just capitalizes all words.
   * This is probably not correct for any language.
   * For actual (English) title case see {@link #wordsToBeginFromUpperCase(String)}.
   */
  @Contract(pure = true)
  public static @NotNull String toTitleCase(@NotNull String s) {
    return fixCapitalization(s, ArrayUtilRt.EMPTY_STRING_ARRAY, true, false);
  }

  @SuppressWarnings("AssignmentToForLoopParameter")
  private static @NotNull String fixCapitalization(@NotNull String s, String @NotNull [] wordsToIgnore, boolean title, boolean mnemonics) {
    StringBuilder buffer = null;
    int length = s.length();
    for (int i = 0; i < length; i++) {
      char prevChar = i == 0 ? ' ' : s.charAt(i - 1);
      char currChar = s.charAt(i);
      if (!Character.isLetterOrDigit(prevChar) && prevChar != '\'') {
        if (Character.isLetterOrDigit(currChar)) {
          if (title || Character.isUpperCase(currChar)) {
            int start = i;
            for (i++; i < length; i++) {
              char c = s.charAt(i);
              if (mnemonics && (c == '&' || c == '_')) continue;
              if (!Character.isLetterOrDigit(c)) break;
            }
            if (!title && i > start + 1 && !Character.isLowerCase(s.charAt(start + 1))) {
              // filter out abbreviations like I18n, SQL and CSS
              continue;
            }
            char prevPrevChar = start > 1 ? s.charAt(start - 2) : 0;
            if (prevChar == '.' && (prevPrevChar == ' ' || prevPrevChar == '*')) {
              // file extension like .java or *.java; don't change its capitalization
              continue;
            }
            if (prevChar == '~' && prevPrevChar == ' ') {
              // special string like ~java or _java; keep it as is
              continue;
            }
            if (!isPreposition(s, start, i - 1, ourOtherNonCapitalizableWords)) {
              boolean firstWord = start == 0 || isPunctuation(prevPrevChar);
              boolean lastWord = i >= length - 1|| isPunctuation(s.charAt(i + 1));
              if (!title || firstWord || lastWord || !isPreposition(s, start, i - 1, wordsToIgnore)) {
                if (buffer == null) {
                  buffer = new StringBuilder(s);
                }
                buffer.setCharAt(start, title ? toUpperCase(currChar) : toLowerCase(currChar));
              }
            }
          }
        }
      }
    }
    return buffer == null ? s : buffer.toString();
  }

  private static boolean isPunctuation(char c) {
    return c == '.' || c == '!' || c == ':' || c == '?';
  }

  private static final String[] ourLowerCaseWords = {
    "a", "an", "and", "as", "at", "but", "by", "down", "for", "from", "in", "into", "near",
    "nor", "of", "on", "onto", "or", "out", "over", "per", "so", "the", "to", "until", "unto", "up", "upon", "via", "with"
  };

  private static final String[] ourOtherNonCapitalizableWords = {
    "iOS", "iPhone", "iPad", "iMac", "macOS"
  };

  @Contract(pure = true)
  public static boolean isPreposition(@NotNull String s, int firstChar, int lastChar, String @NotNull [] prepositions) {
    outer: for (String preposition : prepositions) {
      if (lastChar - firstChar + 1 == preposition.length()) {
        for (int j = 0; j < preposition.length(); j++) {
          if (toLowerCase(s.charAt(firstChar + j)) != toLowerCase(preposition.charAt(j))) continue outer;
        }
        return true;
      }
    }
    return false;
  }

  @Contract(pure = true)
  public static @NotNull NotNullFunction<String, String> escaper(boolean escapeSlash, @Nullable String additionalChars) {
    return dom -> {
      StringBuilder builder = new StringBuilder(dom.length());
      escapeStringCharacters(dom.length(), dom, additionalChars, escapeSlash, builder);
      return builder.toString();
    };
  }


  @Contract(mutates = "param3")
  public static void escapeStringCharacters(int length, @NotNull String str, @NotNull StringBuilder buffer) {
    escapeStringCharacters(length, str, "\"", buffer);
  }

  @Contract(mutates = "param4")
  public static @NotNull StringBuilder escapeStringCharacters(int length,
                                                              @NotNull String str,
                                                              @Nullable String additionalChars,
                                                              @NotNull StringBuilder buffer) {
    return escapeStringCharacters(length, str, additionalChars, true, buffer);
  }

  @Contract(mutates = "param5")
  public static @NotNull StringBuilder escapeStringCharacters(int length,
                                                              @NotNull String str,
                                                              @Nullable String additionalChars,
                                                              boolean escapeSlash,
                                                              @NotNull StringBuilder buffer) {
    return escapeStringCharacters(length, str, additionalChars, escapeSlash, true, buffer);
  }

  @Contract(mutates = "param6")
  public static @NotNull StringBuilder escapeStringCharacters(int length,
                                                              @NotNull String str,
                                                              @Nullable String additionalChars,
                                                              boolean escapeSlash,
                                                              boolean escapeUnicode,
                                                              @NotNull StringBuilder buffer) {
    char prev = 0;
    for (int idx = 0; idx < length; idx++) {
      char ch = str.charAt(idx);
      switch (ch) {
        case '\b':
          buffer.append("\\b");
          break;

        case '\t':
          buffer.append("\\t");
          break;

        case '\n':
          buffer.append("\\n");
          break;

        case '\f':
          buffer.append("\\f");
          break;

        case '\r':
          buffer.append("\\r");
          break;

        default:
          if (escapeSlash && ch == '\\') {
            buffer.append("\\\\");
          }
          else if (additionalChars != null && additionalChars.indexOf(ch) > -1 && (escapeSlash || prev != '\\')) {
            buffer.append("\\").append(ch);
          }
          else if (escapeUnicode && !isPrintableUnicode(ch)) {
            CharSequence hexCode = toUpperCase(Integer.toHexString(ch));
            buffer.append("\\u");
            int paddingCount = 4 - hexCode.length();
            while (paddingCount-- > 0) {
              buffer.append(0);
            }
            buffer.append(hexCode);
          }
          else {
            buffer.append(ch);
          }
      }
      prev = ch;
    }
    return buffer;
  }

  @Contract(pure = true)
  public static boolean isPrintableUnicode(char c) {
    int t = Character.getType(c);
    Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
    return t != Character.UNASSIGNED && t != Character.LINE_SEPARATOR && t != Character.PARAGRAPH_SEPARATOR &&
           t != Character.CONTROL && t != Character.FORMAT && t != Character.PRIVATE_USE && t != Character.SURROGATE &&
           block != Character.UnicodeBlock.VARIATION_SELECTORS && block != Character.UnicodeBlock.VARIATION_SELECTORS_SUPPLEMENT;
  }

  @Contract(pure = true)
  public static @NotNull String escapeStringCharacters(@NotNull String s) {
    StringBuilder buffer = new StringBuilder(s.length());
    escapeStringCharacters(s.length(), s, "\"", buffer);
    return buffer.toString();
  }

  @Contract(pure = true)
  public static @NotNull String escapeCharCharacters(@NotNull String s) {
    StringBuilder buffer = new StringBuilder(s.length());
    escapeStringCharacters(s.length(), s, "'", buffer);
    return buffer.toString();
  }

  @Contract(pure = true)
  public static @NotNull String unescapeStringCharacters(@NotNull String s) {
    StringBuilder buffer = new StringBuilder(s.length());
    unescapeStringCharacters(s.length(), s, buffer);
    return buffer.toString();
  }

  private static boolean isQuoteAt(@NotNull String s, int ind) {
    char ch = s.charAt(ind);
    return ch == '\'' || ch == '\"';
  }

  @Contract(pure = true)
  public static boolean isQuotedString(@NotNull String s) {
    return StringUtilRt.isQuotedString(s);
  }

  /**
   * @return string with paired quotation marks (quote (") or apostrophe (')) removed
   */
  @Contract(pure = true)
  public static @NotNull String unquoteString(@NotNull String s) {
    return StringUtilRt.unquoteString(s);
  }

  @Contract(pure = true)
  public static @NotNull String unquoteString(@NotNull String s, char quotationChar) {
    return StringUtilRt.unquoteString(s, quotationChar);
  }

  @ApiStatus.Internal
  @Contract(mutates = "param3")
  public static void unescapeStringCharacters(int length, @NotNull String s, @NotNull StringBuilder buffer) {
    boolean escaped = false;
    for (int idx = 0; idx < length; idx++) {
      char ch = s.charAt(idx);
      if (!escaped) {
        if (ch == '\\') {
          escaped = true;
        }
        else {
          buffer.append(ch);
        }
      }
      else {
        int octalEscapeMaxLength = 2;
        switch (ch) {
          case 'n':
            buffer.append('\n');
            break;

          case 'r':
            buffer.append('\r');
            break;

          case 'b':
            buffer.append('\b');
            break;

          case 't':
            buffer.append('\t');
            break;

          case 'f':
            buffer.append('\f');
            break;

          case '\'':
            buffer.append('\'');
            break;

          case '\"':
            buffer.append('\"');
            break;

          case '\\':
            buffer.append('\\');
            break;

          case 'u':
            if (idx + 4 < length) {
              try {
                int code = Integer.parseInt(s.substring(idx + 1, idx + 5), 16);
                //noinspection AssignmentToForLoopParameter
                idx += 4;
                buffer.append((char)code);
              }
              catch (NumberFormatException e) {
                buffer.append("\\u");
              }
            }
            else {
              buffer.append("\\u");
            }
            break;

          case '0':
          case '1':
          case '2':
          case '3':
            octalEscapeMaxLength = 3;
            //noinspection fallthrough
          case '4':
          case '5':
          case '6':
          case '7':
            int escapeEnd = idx + 1;
            while (escapeEnd < length && escapeEnd < idx + octalEscapeMaxLength && isOctalDigit(s.charAt(escapeEnd))) escapeEnd++;
            try {
              buffer.append((char)Integer.parseInt(s.substring(idx, escapeEnd), 8));
            }
            catch (NumberFormatException e) {
              throw new RuntimeException("Couldn't parse " + s.substring(idx, escapeEnd), e); // shouldn't happen
            }
            //noinspection AssignmentToForLoopParameter
            idx = escapeEnd - 1;
            break;

          default:
            buffer.append(ch);
            break;
        }
        escaped = false;
      }
    }

    if (escaped) buffer.append('\\');
  }

  /**
   * @see <a href="https://en.cppreference.com/w/cpp/language/escape">C/C++ escaping</a>
   */
  @Contract(pure = true)
  public static @NotNull String unescapeAnsiStringCharacters(@NotNull String s) {
    StringBuilder buffer = new StringBuilder(s.length());
    int length = s.length();
    int count = 0; // count == -1 if the escape sequence has an arbitrary length
    int radix = 0;
    int suffixLen = 0;
    boolean decode = false;

    boolean escaped = false;
    for (int idx = 0; idx < length; idx++) {
      char ch = s.charAt(idx);
      if (!escaped) {
        if (ch == '\\') {
          escaped = true;
        }
        else {
          buffer.append(ch);
        }
      }
      else {
        switch (ch) {
          case '\'':
            buffer.append((char)0x27);
            break;

          case '\"':
            buffer.append((char)0x22);
            break;

          case '?':
            buffer.append((char)0x3f);
            break;

          case '\\':
            buffer.append((char)0x5c);
            break;

          case 'a':
            buffer.append((char)0x07);
            break;

          case 'b':
            buffer.append((char)0x08);
            break;

          case 'f':
            buffer.append((char)0x0c);
            break;

          case 'n':
            buffer.append((char)0x0a);
            break;

          case 'r':
            buffer.append((char)0x0d);
            break;

          case 't':
            buffer.append((char)0x09);
            break;

          case 'v':
            buffer.append((char)0x0b);
            break;

          case '0':
          case '1':
          case '2':
          case '3':
          case '4':
          case '5':
          case '6':
          case '7':
            count = 3;
            radix = 8;
            suffixLen = 0;
            decode = true;
            break;
          case 'x':
            count = -1;
            radix = 0x10;
            suffixLen = 1;
            decode = true;
            break;
          case 'u':
            count = 4;
            radix = 0x10;
            suffixLen = 1;
            decode = true;
            break;
          case 'U':
            count = 8;
            radix = 0x10;
            suffixLen = 1;
            decode = true;
            break;
          default:
            buffer.append(ch);
            break;
        }
        if (decode) {
          decode = false;
          boolean arbitraryLength = count == -1;
          StringBuilder sb = new StringBuilder(arbitraryLength ? 8 : count);
          for (int pos = idx + suffixLen; pos < length && (arbitraryLength || count > 0); ++pos) {
            char chl = s.charAt(pos);
            if (!(radix == 0x10 && isHexDigit(chl) || radix == 8 && isOctalDigit(chl))) {
              break;
            }
            sb.append(chl);
            if (!arbitraryLength) {
              --count;
            }
          }
          if (sb.length() != 0) {
            try {
              int code = Integer.parseInt(sb.toString(), radix);
              if (Character.isValidCodePoint(code)) {
                //noinspection AssignmentToForLoopParameter
                idx += sb.length() + suffixLen - 1;
                // todo: implement UTF-32 support
                buffer.append(Character.toChars(code));
              }
              else {
                buffer.append('\\').append(ch);
              }
            }
            catch (NumberFormatException e) {
              buffer.append('\\').append(ch);
            }
          }
          else {
            buffer.append('\\').append(ch);
          }
        }
        escaped = false;
      }
    }

    if (escaped) buffer.append('\\');
    return buffer.toString();
  }

  /**
   * Pluralize English word. Could be used when e.g., generating collection name by element type.
   * Do not use this method in localized context, as it works for English language only.
   *
   * @param word word to pluralize
   * @return word in plural form
   */
  @Contract(pure = true)
  public static @NotNull @NonNls String pluralize(@NotNull @NonNls String word) {
    return Strings.pluralize(word);
  }

  @Contract(pure = true)
  public static @NotNull String capitalizeWords(@NotNull String text,
                                                boolean allWords) {
    return capitalizeWords(text, " \t\n\r\f([<", allWords, true);
  }

  @Contract(pure = true)
  public static @NotNull String capitalizeWords(@NotNull String text,
                                                @NotNull String tokenizerDelimiters,
                                                boolean allWords,
                                                boolean leaveOriginalDelimiters) {
    StringTokenizer tokenizer = new StringTokenizer(text, tokenizerDelimiters, leaveOriginalDelimiters);
    StringBuilder out = new StringBuilder(text.length());
    boolean toCapitalize = true;
    while (tokenizer.hasMoreTokens()) {
      String word = tokenizer.nextToken();
      if (!leaveOriginalDelimiters && out.length() > 0) {
        out.append(' ');
      }
      out.append(toCapitalize ? capitalize(word) : word);
      if (!allWords) {
        toCapitalize = false;
      }
    }
    return out.toString();
  }

  @Contract(pure = true)
  public static @NotNull String decapitalize(@NotNull String s) {
    return Introspector.decapitalize(s);
  }

  @Contract(pure = true)
  public static boolean isVowel(char c) {
    return c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u' || c == 'y';
  }

  /**
   * Capitalize the first letter of the sentence.
   */
  @Contract(pure = true)
  public static @NotNull String capitalize(@NotNull String s) {
    return Strings.capitalize(s);
  }

  @Contract(value = "null -> false", pure = true)
  public static boolean isCapitalized(@Nullable String s) {
    return Strings.isCapitalized(s);
  }

  @Contract(value = "null -> false", pure = true)
  public static boolean canBeCapitalized(@Nullable String s) {
    return isNotEmpty(s) && Character.isLowerCase(s.charAt(0));
  }

  @Contract(pure = true)
  public static @NotNull String capitalizeWithJavaBeanConvention(@NotNull String s) {
    if (s.length() > 1 && Character.isUpperCase(s.charAt(1))) {
      return s;
    }
    return capitalize(s);
  }

  @Contract(pure = true)
  public static int stringHashCode(@NotNull CharSequence chars) {
    return Strings.stringHashCode(chars);
  }

  @Contract(pure = true)
  public static int stringHashCode(@NotNull CharSequence chars, int from, int to) {
    return Strings.stringHashCode(chars, from, to);
  }

  @Contract(pure = true)
  public static int stringHashCode(@NotNull CharSequence chars, int from, int to, int prefixHash) {
    return Strings.stringHashCode(chars, from, to, prefixHash);
  }

  @Contract(pure = true)
  public static int stringHashCode(char[] chars, int from, int to) {
    return Strings.stringHashCode(chars, from, to);
  }

  @Contract(pure = true)
  public static int stringHashCodeInsensitive(char @NotNull [] chars, int from, int to) {
    return Strings.stringHashCodeInsensitive(chars, from, to);
  }

  @Contract(pure = true)
  public static int stringHashCodeInsensitive(@NotNull CharSequence chars, int from, int to) {
    return Strings.stringHashCodeInsensitive(chars, from, to);
  }

  @Contract(pure = true)
  public static int stringHashCodeInsensitive(@NotNull CharSequence chars, int from, int to, int prefixHash) {
    return Strings.stringHashCodeInsensitive(chars, from, to, prefixHash);
  }

  @Contract(pure = true)
  public static int stringHashCodeInsensitive(@NotNull CharSequence chars) {
    return Strings.stringHashCodeInsensitive(chars);
  }

  @Contract(pure = true)
  public static int stringHashCodeIgnoreWhitespaces(@NotNull CharSequence chars) {
    return Strings.stringHashCodeIgnoreWhitespaces(chars);
  }

  /**
   * Equivalent to string.startsWith(prefixes[0] + prefixes[1] + ...) but avoids creating an object for concatenation.
   */
  @Contract(pure = true)
  public static boolean startsWithConcatenation(@NotNull String string, String @NotNull ... prefixes) {
    int offset = 0;
    for (String prefix : prefixes) {
      int prefixLen = prefix.length();
      if (!string.regionMatches(offset, prefix, 0, prefixLen)) {
        return false;
      }
      offset += prefixLen;
    }
    return true;
  }

  @Contract(value = "null -> null; !null -> !null", pure = true)
  public static String trim(@Nullable String s) {
    return Strings.trim(s);
  }

  @Contract(pure = true)
  public static @NotNull String trimEnd(@NotNull String s, @NotNull String suffix) {
    return Strings.trimEnd(s, suffix);
  }

  @Contract(pure = true)
  public static @NotNull String trimEnd(@NotNull String s, @NotNull String suffix, boolean ignoreCase) {
    return Strings.trimEnd(s, suffix, ignoreCase);
  }

  @Contract(pure = true)
  public static @NotNull String trimEnd(@NotNull String s, char suffix) {
    return Strings.trimEnd(s, suffix);
  }

  @Contract(pure = true)
  public static @NotNull String trimLog(@NotNull String text, int limit) {
    if (limit > 5 && text.length() > limit) {
      return text.substring(0, limit - 5) + " ...\n";
    }
    return text;
  }

  @Contract(pure = true)
  public static @NotNull String trimLeading(@NotNull String string) {
    return trimLeading((CharSequence)string).toString();
  }
  @Contract(pure = true)
  public static @NotNull CharSequence trimLeading(@NotNull CharSequence string) {
    int index = 0;
    while (index < string.length() && Character.isWhitespace(string.charAt(index))) index++;
    return string.subSequence(index, string.length());
  }

  @Contract(pure = true)
  public static @NotNull String trimLeading(@NotNull String string, char symbol) {
    int index = 0;
    while (index < string.length() && string.charAt(index) == symbol) index++;
    return string.substring(index);
  }

  @Contract(mutates = "param1")
  public static @NotNull StringBuilder trimLeading(@NotNull StringBuilder builder, char symbol) {
    int index = 0;
    while (index < builder.length() && builder.charAt(index) == symbol) index++;
    if (index > 0) builder.delete(0, index);
    return builder;
  }

  @Contract(pure = true)
  public static @NotNull String trimTrailing(@NotNull String string) {
    return trimTrailing((CharSequence)string).toString();
  }

  @Contract(pure = true)
  public static @NotNull CharSequence trimTrailing(@NotNull CharSequence string) {
    int index = string.length() - 1;
    while (index >= 0 && Character.isWhitespace(string.charAt(index))) index--;
    return string.subSequence(0, index + 1);
  }

  @Contract(pure = true)
  public static @NotNull String trimTrailing(@NotNull String string, char symbol) {
    int index = string.length() - 1;
    while (index >= 0 && string.charAt(index) == symbol) index--;
    return string.substring(0, index + 1);
  }

  @Contract(mutates = "param1")
  public static @NotNull StringBuilder trimTrailing(@NotNull StringBuilder builder, char symbol) {
    int index = builder.length() - 1;
    while (index >= 0 && builder.charAt(index) == symbol) index--;
    builder.setLength(index + 1);
    return builder;
  }

  @Contract(value = "null -> null; !null -> !null", pure = true)
  public static @Nullable CharSequence trim(@Nullable CharSequence s) {
    if (s == null) return null;
    int startIndex = 0;
    int length = s.length();
    if (length == 0) return s;
    while (startIndex < length && Character.isWhitespace(s.charAt(startIndex))) startIndex++;

    if (startIndex == length) {
      return Strings.EMPTY_CHAR_SEQUENCE;
    }

    int endIndex = length - 1;
    while (endIndex >= startIndex && Character.isWhitespace(s.charAt(endIndex))) endIndex--;
    endIndex++;

    if (startIndex > 0 || endIndex < length) {
      return s.subSequence(startIndex, endIndex);
    }
    return s;
  }

  @Contract(pure = true)
  public static boolean startsWithChar(@Nullable CharSequence s, char prefix) {
    return s != null && s.length() != 0 && s.charAt(0) == prefix;
  }

  @Contract(pure = true)
  public static boolean endsWithChar(@Nullable CharSequence s, char suffix) {
    return Strings.endsWithChar(s, suffix);
  }

  @Contract(pure = true)
  public static @NotNull String trimStart(@NotNull String s, @NotNull String prefix) {
    return Strings.trimStart(s, prefix);
  }

  @Contract(pure = true)
  public static @NotNull String trimExtensions(@NotNull String name) {
    int index = name.indexOf('.');
    return index < 0 ? name : name.substring(0, index);
  }

  @Contract(pure = true)
  public static @NotNull String pluralize(@NotNull String base, int count) {
    if (count == 1) return base;
    return pluralize(base);
  }

  @Contract(mutates = "param1")
  public static void repeatSymbol(@NotNull Appendable buffer, char symbol, int times) {
    assert times >= 0 : times;
    try {
      for (int i = 0; i < times; i++) {
        buffer.append(symbol);
      }
    }
    catch (IOException e) {
      Logger.getInstance(StringUtil.class).error(e);
    }
  }

  @Contract(pure = true)
  public static String defaultIfEmpty(@Nullable String value, String defaultValue) {
    return isEmpty(value) ? defaultValue : value;
  }

  @Contract(value = "null -> false", pure = true)
  public static boolean isNotEmpty(@Nullable String s) {
    return Strings.isNotEmpty(s);
  }

  @Contract(value = "null -> true", pure = true)
  public static boolean isEmpty(@Nullable String s) {
    return Strings.isEmpty(s);
  }

  @Contract(value = "null -> true",pure = true)
  public static boolean isEmpty(@Nullable CharSequence cs) {
    return Strings.isEmpty(cs);
  }

  @Contract(pure = true)
  public static int length(@Nullable CharSequence cs) {
    return cs == null ? 0 : cs.length();
  }

  @Contract(pure = true)
  public static @NotNull String notNullize(@Nullable String s) {
    return Strings.notNullize(s);
  }

  @Contract(pure = true)
  public static @NotNull String notNullize(@Nullable String s, @NotNull String defaultValue) {
    return Strings.notNullize(s, defaultValue);
  }

  @Contract(pure = true)
  public static @Nullable String nullize(@Nullable String s) {
    return Strings.nullize(s, false);
  }

  @Contract(pure = true)
  public static @Nullable String nullize(@Nullable String s, @Nullable String defaultValue) {
    return Strings.nullize(s, defaultValue);
  }

  @Contract(pure = true)
  public static @Nullable String nullize(@Nullable String s, boolean nullizeSpaces) {
    return Strings.nullize(s, nullizeSpaces);
  }

  @Contract(value = "null -> true",pure = true)
  // we need to keep this method to preserve backward compatibility
  public static boolean isEmptyOrSpaces(@Nullable String s) {
    return isEmptyOrSpaces((CharSequence)s);
  }

  @Contract(value = "null -> true", pure = true)
  public static boolean isEmptyOrSpaces(@Nullable CharSequence s) {
    return Strings.isEmptyOrSpaces(s);
  }

  /**
   * True if given symbol is white space, tabulation or line feed.
   *
   * @param c symbol to check
   * @return {@code true} if given symbol is white space, tabulation or line feed; {@code false} otherwise
   */
  @Contract(pure = true)
  public static boolean isWhiteSpace(char c) {
    return Strings.isWhiteSpace(c);
  }

  @Contract(pure = true)
  public static @NotNull String getThrowableText(@NotNull Throwable aThrowable) {
    return ExceptionUtil.getThrowableText(aThrowable);
  }

  @Contract(pure = true)
  public static @Nullable String getMessage(@NotNull Throwable e) {
    return ExceptionUtil.getMessage(e);
  }

  @ReviseWhenPortedToJDK(value = "11", description = "Character.toString(aChar).repeat(count)")
  @Contract(pure = true)
  public static @NotNull String repeatSymbol(char aChar, int count) {
    char[] buffer = new char[count];
    Arrays.fill(buffer, aChar);
    return new String(buffer);
  }

  @Contract(pure = true)
  public static @NotNull String repeat(@NotNull String s, int count) {
    if (count == 0) return "";
    assert count >= 0 : count;
    StringBuilder sb = new StringBuilder(s.length() * count);
    for (int i = 0; i < count; i++) {
      sb.append(s);
    }
    return sb.toString();
  }

  @Contract(pure = true)
  public static @Unmodifiable @NotNull List<String> splitHonorQuotes(@NotNull String s, char separator) {
    return Collections.unmodifiableList(StringUtilRt.splitHonorQuotes(s, separator));
  }

  @Contract(pure = true)
  public static @Unmodifiable @NotNull List<String> split(@NotNull String s, @NotNull String separator) {
    return split(s, separator, true);
  }
  @Contract(pure = true)
  public static @Unmodifiable @NotNull List<CharSequence> split(@NotNull CharSequence s, @NotNull CharSequence separator) {
    return split(s, separator, true, true);
  }

  @Contract(pure = true)
  public static @Unmodifiable @NotNull List<String> split(@NotNull String s, @NotNull String separator, boolean excludeSeparator) {
    return split(s, separator, excludeSeparator, true);
  }

  @Contract(pure = true)
  public static @Unmodifiable @NotNull List<String> split(@NotNull String s, @NotNull String separator, boolean excludeSeparator, boolean excludeEmptyStrings) {
    //noinspection unchecked,rawtypes
    return (List)split((CharSequence)s, separator, excludeSeparator, excludeEmptyStrings);
  }

  @Contract(pure = true)
  public static @Unmodifiable @NotNull List<CharSequence> split(@NotNull CharSequence s, @NotNull CharSequence separator, boolean excludeSeparator, boolean excludeEmptyStrings) {
    if (separator.length() == 0) {
      return Collections.singletonList(s);
    }
    FreezableArrayList<CharSequence> result = new FreezableArrayList<>();
    int pos = 0;
    while (true) {
      int index = indexOf(s, separator, pos);
      if (index == -1) break;
      int nextPos = index + separator.length();
      CharSequence token = s.subSequence(pos, excludeSeparator ? index : nextPos);
      if (token.length() != 0 || !excludeEmptyStrings) {
        result.add(token);
      }
      pos = nextPos;
    }
    if (pos < s.length() || !excludeEmptyStrings && pos == s.length()) {
      result.add(s.subSequence(pos, s.length()));
    }
    return result.emptyOrFrozen();
  }

  @Contract(pure = true)
  public static @Unmodifiable @NotNull List<String> split(@NotNull String s,
                                                          @NotNull CharFilter separator,
                                                          boolean excludeSeparator,
                                                          boolean excludeEmptyStrings) {
    //noinspection unchecked,rawtypes
    return (List)split((CharSequence)s, separator, excludeSeparator, excludeEmptyStrings);
  }

  @Contract(pure = true)
  public static @Unmodifiable @NotNull List<CharSequence> split(@NotNull CharSequence s,
                                                                @NotNull CharFilter separator,
                                                                boolean excludeSeparator,
                                                                boolean excludeEmptyStrings) {
    FreezableArrayList<CharSequence> result = new FreezableArrayList<>();
    int pos = 0;
    int index = 0;
    while (index < s.length()) {
      if (separator.accept(s.charAt(index))) {
        CharSequence token = s.subSequence(pos, excludeSeparator ? index : index + 1);
        if (token.length() != 0 || !excludeEmptyStrings) {
          result.add(token);
        }
        pos = index + 1;
      }
      index++;
    }
    if (pos < s.length() || !excludeEmptyStrings && pos == s.length()) {
      result.add(s.subSequence(pos, s.length()));
    }
    return result.emptyOrFrozen();
  }

  @Contract(pure = true)
  public static @NotNull Iterable<String> tokenize(@NotNull String s, @NotNull String separators) {
    return tokenize(new StringTokenizer(s, separators));
  }

  @Contract(pure = true)
  public static @NotNull Iterable<String> tokenize(@NotNull StringTokenizer tokenizer) {
    return () -> new Iterator<String>() {
      @Override
      public boolean hasNext() {
        return tokenizer.hasMoreTokens();
      }

      @Override
      public String next() {
        return tokenizer.nextToken();
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }

  /**
   * @return list containing all words in {@code text}, or {@link Collections#emptyList()} if there are none.
   * The <b>word</b> here means the maximum sub-string consisting entirely of characters which are {@code Character.isJavaIdentifierPart(c)}.
   */
  @Contract(pure = true)
  public static @Unmodifiable @NotNull List<String> getWordsIn(@NotNull String text) {
    FreezableArrayList<String> result = null;
    int start = -1;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      boolean isIdentifierPart = Character.isJavaIdentifierPart(c);
      if (isIdentifierPart && start == -1) {
        start = i;
      }
      if (isIdentifierPart && i == text.length() - 1) {
        if (result == null) {
          result = new FreezableArrayList<>();
        }
        result.add(text.substring(start, i + 1));
      }
      else if (!isIdentifierPart && start != -1) {
        if (result == null) {
          result = new FreezableArrayList<>();
        }
        result.add(text.substring(start, i));
        start = -1;
      }
    }
    if (result == null) {
      return Collections.emptyList();
    }
    return result.emptyOrFrozen();
  }

  @Contract(pure = true)
  public static @Unmodifiable @NotNull List<TextRange> getWordIndicesIn(@NotNull String text) {
    return getWordIndicesIn(text, null);
  }

  /**
   * @param text text to get word ranges in.
   * @param separatorsSet if not null, only these characters will be considered as separators (i.e., not a part of word).
   *                   Otherwise {@link Character#isJavaIdentifierPart(char)} will be used to determine whether a symbol is part of word.
   * @return ranges of words in passed text.
   */
  @Contract(pure = true)
  public static @Unmodifiable @NotNull List<TextRange> getWordIndicesIn(@NotNull String text, @Nullable Set<Character> separatorsSet) {
    FreezableArrayList<TextRange> result = new FreezableArrayList<>();
    int start = -1;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      boolean isIdentifierPart = separatorsSet == null ? Character.isJavaIdentifierPart(c) : !separatorsSet.contains(c);
      if (isIdentifierPart && start == -1) {
        start = i;
      }
      if (isIdentifierPart && i == text.length() - 1) {
        result.add(new TextRange(start, i + 1));
      }
      else if (!isIdentifierPart && start != -1) {
        result.add(new TextRange(start, i));
        start = -1;
      }
    }
    return result.emptyOrFrozen();
  }

  @Contract(pure = true)
  public static @NotNull String join(String @NotNull [] strings, @NotNull String separator) {
    return join(strings, 0, strings.length, separator);
  }

  @Contract(pure = true)
  public static @NotNull String join(String @NotNull [] strings, int startIndex, int endIndex, @NotNull String separator) {
    StringBuilder result = new StringBuilder();
    for (int i = startIndex; i < endIndex; i++) {
      if (i > startIndex) result.append(separator);
      result.append(strings[i]);
    }
    return result.toString();
  }

  @Contract(pure = true)
  public static String @NotNull [] zip(String @NotNull [] strings1, String @NotNull [] strings2, String separator) {
    if (strings1.length != strings2.length) throw new IllegalArgumentException();

    String[] result = ArrayUtil.newStringArray(strings1.length);
    for (int i = 0; i < result.length; i++) {
      result[i] = strings1[i] + separator + strings2[i];
    }

    return result;
  }

  @Contract(pure = true)
  public static String @NotNull [] surround(String @NotNull [] strings, @NotNull String prefix, @NotNull String suffix) {
    String[] result = ArrayUtil.newStringArray(strings.length);
    for (int i = 0; i < result.length; i++) {
      result[i] = prefix + strings[i] + suffix;
    }
    return result;
  }

  @Contract(pure = true)
  public static @NotNull <T> String join(T @NotNull [] items, @NotNull Function<? super T, String> f, @NotNull String separator) {
    return Strings.join(items, f, separator);
  }

  @Contract(pure = true)
  public static @NotNull <T> String join(@NotNull Collection<? extends T> items,
                                         @NotNull Function<? super T, String> f,
                                         @NotNull String separator) {
    return Strings.join(items, f, separator);
  }

  @Contract(pure = true)
  public static @NotNull String join(@NotNull Iterable<?> items, @NotNull String separator) {
    return Strings.join(items, separator);
  }

  @Contract(pure = true)
  public static @NotNull <T> String join(@NotNull Iterable<? extends T> items,
                                         @NotNull Function<? super T, ? extends CharSequence> f,
                                         @NotNull String separator) {
    return Strings.join(items, f, separator);
  }

  @Contract(mutates = "param4")
  public static <T> void join(@NotNull Iterable<? extends T> items,
                              @NotNull Function<? super T, ? extends CharSequence> f,
                              @NotNull String separator,
                              @NotNull StringBuilder result) {
    Strings.join(items, f, separator, result);
  }

  @Contract(pure = true)
  public static @NotNull String join(@NotNull Collection<String> strings, @NotNull String separator) {
    return Strings.join(strings, separator);
  }

  @Contract(mutates = "param3")
  public static void join(@NotNull Collection<String> strings, @NotNull String separator, @NotNull StringBuilder result) {
    Strings.join(strings, separator, result);
  }

  @Contract(pure = true)
  public static @NotNull String join(int @NotNull [] strings, @NotNull String separator) {
    return Strings.join(strings, separator);
  }

  @Contract(pure = true)
  public static @NotNull String join(String @NotNull ... strings) {
    return Strings.join(strings);
  }

  @Contract(pure = true)
  public static @NotNull Collector<CharSequence, ?, String> joining() {
    return Collectors.joining(", ");
  }

  /**
   * Consider using {@link StringUtil#unquoteString(String)} instead.
   * Note: this method has an odd behavior:
   *   Quotes are removed even if leading and trailing quotes are different or
   *                           if there is only one quote (leading or trailing).
   */
  @Contract(pure = true)
  public static @NotNull String stripQuotesAroundValue(@NotNull String text) {
    int len = text.length();
    if (len > 0) {
      int from = isQuoteAt(text, 0) ? 1 : 0;
      int to = len > 1 && isQuoteAt(text, len - 1) ? len - 1 : len;
      if (from > 0 || to < len) {
        return text.substring(from, to);
      }
    }
    return text;
  }

  /** Formats given file size in metric (1 kB = 1000 B) units (example: {@code formatFileSize(1234) = "1.23 KB"}). */
  @Contract(pure = true)
  public static @NotNull String formatFileSize(long fileSize) {
    return Formats.formatFileSize(fileSize);
  }

  /** Formats given file size in metric (1 kB = 1000 B) units (example: {@code formatFileSize(1234, "") = "1.23KB"}). */
  @Contract(pure = true)
  public static @NotNull String formatFileSize(long fileSize, @NotNull String unitSeparator) {
    return Formats.formatFileSize(fileSize, unitSeparator);
  }

  /**
   * Formats duration given in milliseconds as a sum of time units (example: {@code formatDuration(123456) = "2 m 3 s 456 ms"}).
   * This method is intended to be used in non-localized contexts (primarily in log output).
   * @see com.intellij.ide.nls.NlsMessages NlsMessages for localized output.
   */
  @Contract(pure = true)
  public static @NotNull @NonNls String formatDuration(long duration) {
    return Formats.formatDuration(duration);
  }

  /**
   * Formats {@link Duration} as a sum of time units (calls {@link #formatDuration(long)} with duration converted to milliseconds)
   * This method is intended to be used in non-localized contexts (primarily in log output).
   * See com.intellij.ide.nls.NlsMessages for localized output.
   */
  @Contract(pure = true)
  public static @NotNull @NonNls String formatDuration(@NotNull Duration duration) {
    return Formats.formatDuration(duration);
  }

  /**
   * Returns unpluralized variant using English based heuristics like properties -> property, names -> name, children -> child.
   * Returns {@code null} if failed to match appropriate heuristic.
   *
   * @param word english word in plural form
   * @return name in singular form or {@code null} if failed to find one.
   */
  @Contract(pure = true)
  public static @Nullable String unpluralize(@NotNull String word) {
    return Strings.unpluralize(word);
  }

  @Contract(pure = true)
  public static boolean containsAlphaCharacters(@NotNull String value) {
    for (int i = 0; i < value.length(); i++) {
      if (Character.isLetter(value.charAt(i))) return true;
    }
    return false;
  }

  @Contract(pure = true)
  public static boolean containsAnyChar(@NotNull String value, @NotNull @NonNls String chars) {
    return Strings.containsAnyChar(value, chars);
  }

  @Contract(pure = true)
  public static boolean containsAnyChar(@NotNull String value,
                                        @NotNull String chars,
                                        int start, int end) {
    return Strings.containsAnyChar(value, chars, start, end);
  }

  @Contract(pure = true)
  public static boolean containsChar(@NotNull String value, char ch) {
    return Strings.containsChar(value, ch);
  }

  /**
   * Strip out all characters not accepted by given filter
   *
   * @param s      e.g. "/n    my String "
   * @param filter e.g. {@link CharFilter#NOT_WHITESPACE_FILTER}
   * @return stripped string e.g. "myString"
   */
  @Contract(pure = true)
  public static @NotNull String strip(@NotNull String s, @NotNull CharFilter filter) {
    StringBuilder result = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char ch = s.charAt(i);
      if (filter.accept(ch)) {
        result.append(ch);
      }
    }
    return result.toString();
  }

  /**
   * Trim all characters not accepted by given filter
   *
   * @param s      e.g. "/n    my string "
   * @param filter e.g. {@link CharFilter#NOT_WHITESPACE_FILTER}
   * @return trimmed string e.g. "my string"
   */
  @Contract(pure = true)
  public static @NotNull String trim(@NotNull String s, @NotNull CharFilter filter) {
    int start = 0;
    int end = s.length();

    for (; start < end; start++) {
      char ch = s.charAt(start);
      if (filter.accept(ch)) break;
    }

    for (; start < end; end--) {
      char ch = s.charAt(end - 1);
      if (filter.accept(ch)) break;
    }

    return s.substring(start, end);
  }

  @Contract(pure = true)
  public static @Unmodifiable @NotNull List<String> findMatches(@NotNull String s, @NotNull Pattern pattern) {
    return findMatches(s, pattern, 1);
  }

  @Contract(pure = true)
  public static @Unmodifiable @NotNull List<String> findMatches(@NotNull String s, @NotNull Pattern pattern, int groupIndex) {
    FreezableArrayList<String> result = new FreezableArrayList<>();
    Matcher m = pattern.matcher(s);
    while (m.find()) {
      String group = m.group(groupIndex);
      if (group != null) {
        result.add(group);
      }
    }
    return result.emptyOrFrozen();
  }

  /**
   * Find the position of the first character accepted by given filter.
   *
   * @param s      the string to search
   * @param filter search filter
   * @return position of the first character accepted or -1 if not found
   */
  @Contract(pure = true)
  public static int findFirst(@NotNull CharSequence s, @NotNull CharFilter filter) {
    for (int i = 0; i < s.length(); i++) {
      char ch = s.charAt(i);
      if (filter.accept(ch)) {
        return i;
      }
    }
    return -1;
  }

  @Contract(pure = true)
  public static @NotNull String replaceSubstring(@NotNull String string, @NotNull TextRange range, @NotNull String replacement) {
    return range.replace(string, replacement);
  }

  @Contract(pure = true)
  public static boolean startsWithWhitespace(@NotNull String text) {
    return !text.isEmpty() && Character.isWhitespace(text.charAt(0));
  }

  @Contract(pure = true)
  public static boolean isChar(CharSequence seq, int index, char c) {
    return index >= 0 && index < seq.length() && seq.charAt(index) == c;
  }

  @Contract(pure = true)
  public static boolean startsWith(@NotNull CharSequence text, @NotNull CharSequence prefix) {
    return StringUtilRt.startsWith(text, prefix);
  }

  @Contract(pure = true)
  public static boolean startsWith(@NotNull CharSequence text, int startIndex, @NotNull CharSequence prefix) {
    return Strings.startsWith(text, startIndex, prefix);
  }

  @Contract(pure = true)
  public static boolean endsWith(@NotNull CharSequence text, @NotNull CharSequence suffix) {
    return Strings.endsWith(text, suffix);
  }

  @Contract(pure = true)
  public static boolean endsWith(@NotNull CharSequence text, int start, int end, @NotNull CharSequence suffix) {
    if (start < 0 || end > text.length()) {
      throw new IllegalArgumentException("invalid offsets: start="+start+"; end="+end+"; text.length()="+text.length());
    }
    int suffixLen = suffix.length();
    int delta = end - suffixLen;
    if (delta < start) {
      return false;
    }

    for (int i = 0; i < suffixLen; i++) {
      if (text.charAt(delta + i) != suffix.charAt(i)) {
        return false;
      }
    }

    return true;
  }

  @Contract(pure = true)
  public static @NotNull String commonPrefix(@NotNull String s1, @NotNull String s2) {
    return s1.substring(0, commonPrefixLength(s1, s2));
  }

  @Contract(pure = true)
  public static int commonPrefixLength(@NotNull CharSequence s1, @NotNull CharSequence s2) {
    return commonPrefixLength(s1, s2, false);
  }

  @Contract(pure = true)
  public static int commonPrefixLength(@NotNull CharSequence s1, @NotNull CharSequence s2, boolean ignoreCase) {
    int i;
    int minLength = Math.min(s1.length(), s2.length());
    for (i = 0; i < minLength; i++) {
      if (!Strings.charsMatch(s1.charAt(i), s2.charAt(i), ignoreCase)) {
        break;
      }
    }
    return i;
  }

  @Contract(pure = true)
  public static @NotNull String commonSuffix(@NotNull String s1, @NotNull String s2) {
    return s1.substring(s1.length() - commonSuffixLength(s1, s2));
  }

  @Contract(pure = true)
  public static int commonSuffixLength(@NotNull CharSequence s1, @NotNull CharSequence s2) {
    int s1Length = s1.length();
    int s2Length = s2.length();
    if (s1Length == 0 || s2Length == 0) return 0;
    int i;
    for (i = 0; i < s1Length && i < s2Length; i++) {
      if (s1.charAt(s1Length - i - 1) != s2.charAt(s2Length - i - 1)) {
        break;
      }
    }
    return i;
  }

  /**
   * True if the target symbol {@code c} is contained in the given sequence at {@code [start; end)} interval.
   *
   * @param s     target char sequence to check
   * @param start start offset to use within the given char sequence (inclusive)
   * @param end   end offset to use within the given char sequence (exclusive)
   * @param c     target symbol to check
   * @return {@code true} if given symbol is contained at the target range of the given char sequence;
   * {@code false} otherwise
   */
  @Contract(pure = true)
  public static boolean contains(@NotNull CharSequence s, int start, int end, char c) {
    return Strings.contains(s, start, end, c);
  }

  @Contract(pure = true)
  public static boolean containsWhitespaces(@Nullable CharSequence s) {
    if (s == null) return false;

    for (int i = 0; i < s.length(); i++) {
      if (Character.isWhitespace(s.charAt(i))) return true;
    }
    return false;
  }

  @Contract(pure = true)
  public static int indexOf(@NotNull CharSequence s, char c) {
    return Strings.indexOf(s, c);
  }

  @Contract(pure = true)
  public static int indexOf(@NotNull CharSequence s, char c, int start) {
    return Strings.indexOf(s, c, start);
  }

  @Contract(pure = true)
  public static int indexOf(@NotNull CharSequence s, char c, int start, int end) {
    return Strings.indexOf(s, c, start, end);
  }

  @Contract(pure = true)
  public static boolean contains(@NotNull CharSequence sequence, @NotNull CharSequence infix) {
    return Strings.contains(sequence, infix);
  }

  @Contract(pure = true)
  public static int indexOf(@NotNull CharSequence sequence, @NotNull CharSequence infix) {
    return Strings.indexOf(sequence, infix);
  }

  @Contract(pure = true)
  public static int indexOf(@NotNull CharSequence sequence, @NotNull CharSequence infix, int start) {
    return Strings.indexOf(sequence, infix, start);
  }

  @Contract(pure = true)
  public static int indexOf(@NotNull CharSequence sequence, @NotNull CharSequence infix, int start, int end) {
    return Strings.indexOf(sequence, infix, start, end);
  }

  @Contract(pure = true)
  public static int indexOf(@NotNull CharSequence s, char c, int start, int end, boolean caseSensitive) {
    return Strings.indexOf(s, c, start, end, caseSensitive);
  }

  @Contract(pure = true)
  public static int indexOf(char @NotNull [] s, char c, int start, int end, boolean caseSensitive) {
    return Strings.indexOf(s, c, start, end, caseSensitive);
  }

  @Contract(pure = true)
  public static int indexOfSubstringEnd(@NotNull String text, @NotNull String subString) {
    int i = text.indexOf(subString);
    if (i == -1) return -1;
    return i + subString.length();
  }

  @Contract(pure = true)
  public static int indexOfAny(@NotNull String s, @NotNull String chars) {
    return Strings.indexOfAny(s, chars);
  }

  @Contract(pure = true)
  public static int indexOfAny(@NotNull CharSequence s, @NotNull String chars) {
    return Strings.indexOfAny(s, chars);
  }

  @Contract(pure = true)
  public static int indexOfAny(@NotNull String s, @NotNull String chars, int start, int end) {
    return Strings.indexOfAny(s, chars, start, end);
  }

  @Contract(pure = true)
  public static int indexOfAny(@NotNull CharSequence s, @NotNull String chars, int start, int end) {
    return Strings.indexOfAny(s, chars, start, end);
  }

  @Contract(pure = true)
  public static int lastIndexOfAny(@NotNull CharSequence s, @NotNull String chars) {
    for (int i = s.length() - 1; i >= 0; i--) {
      if (containsChar(chars, s.charAt(i))) return i;
    }
    return -1;
  }

  @Contract(pure = true)
  public static @Nullable String substringBefore(@NotNull String text, @NotNull String subString) {
    int i = text.indexOf(subString);
    if (i == -1) return null;
    return text.substring(0, i);
  }

  @Contract(pure = true)
  public static @NotNull String substringBeforeLast(@NotNull String text, @NotNull String subString) {
    int i = text.lastIndexOf(subString);
    if (i == -1) return text;
    return text.substring(0, i);
  }

  @Contract(pure = true)
  public static @NotNull String substringBeforeLast(@NotNull String text, @NotNull String subString, boolean includeLast) {
    int i = text.lastIndexOf(subString);
    if (i == -1) return text;
    return includeLast ? text.substring(0, i + subString.length()) : text.substring(0, i);
  }

  @Contract(pure = true)
  public static @Nullable String substringAfter(@NotNull String text, @NotNull String subString) {
    int i = text.indexOf(subString);
    if (i == -1) return null;
    return text.substring(i + subString.length());
  }

  @Contract(pure = true)
  public static @Nullable String substringAfterLast(@NotNull String text, @NotNull String subString) {
    int i = text.lastIndexOf(subString);
    if (i == -1) return null;
    return text.substring(i + subString.length());
  }

  /**
   * return the index of last occurrence of the given symbols at {@code [start; end)} sub-sequence of the given text.
   *
   * @param s     target text
   * @param c     target symbol which last occurrence we want to check
   * @param start start offset of the target text (inclusive)
   * @param end   end offset of the target text (exclusive)
   * @return index of the last occurrence of the given symbol at the target sub-sequence of the given text if any;
   * {@code -1} otherwise
   */
  @Contract(pure = true)
  public static int lastIndexOf(@NotNull CharSequence s, char c, int start, int end) {
    return StringUtilRt.lastIndexOf(s, c, start, end);
  }

  @Contract(pure = true)
  public static @NotNull String first(@NotNull String text, int maxLength, boolean appendEllipsis) {
    return text.length() > maxLength ? text.substring(0, maxLength) + (appendEllipsis ? "..." : "") : text;
  }

  @Contract(pure = true)
  public static @NotNull CharSequence first(@NotNull CharSequence text, int length, boolean appendEllipsis) {
    if (text.length() <= length) {
      return text;
    }
    if (appendEllipsis) {
      return text.subSequence(0, length) + "...";
    }
    return text.subSequence(0, length);
  }

  @Contract(pure = true)
  public static @NotNull CharSequence last(@NotNull CharSequence text, int length, boolean prependEllipsis) {
    if (text.length() <= length) {
      return text;
    }
    if (prependEllipsis) {
      return "..." + text.subSequence(text.length() - length, text.length());
    }
    return text.subSequence(text.length() - length, text.length());
  }

  @Contract(pure = true)
  public static @NotNull String firstLast(@NotNull String text, int length) {
    return text.length() > length
           ? text.subSequence(0, length / 2) + ELLIPSIS + text.subSequence(text.length() - length / 2 - 1, text.length())
           : text;
  }

  @Contract(pure = true)
  public static @NotNull String escapeChar(@NotNull String str, char character) {
    return escapeChars(str, character);
  }

  @Contract(pure = true)
  public static @NotNull String escapeChars(@NotNull String str, char @NotNull ... character) {
    StringBuilder buf = new StringBuilder(str);
    for (char c : character) {
      escapeChar(buf, c);
    }
    return buf.toString();
  }

  @Contract(mutates = "param1")
  public static void escapeChar(@NotNull StringBuilder buf, char character) {
    int idx = 0;
    while ((idx = indexOf(buf, character, idx)) >= 0) {
      buf.insert(idx, "\\");
      idx += 2;
    }
  }

  @Contract(pure = true)
  public static @NotNull String escapeQuotes(@NotNull String str) {
    return escapeChar(str, '"');
  }

  @Contract(mutates = "param1")
  public static void escapeQuotes(@NotNull StringBuilder buf) {
    escapeChar(buf, '"');
  }

  @Contract(pure = true)
  public static @NotNull String escapeSlashes(@NotNull String str) {
    return escapeChar(str, '/');
  }

  @Contract(pure = true)
  public static @NotNull String escapeBackSlashes(@NotNull String str) {
    return escapeChar(str, '\\');
  }

  @Contract(pure = true)
  public static @NotNull String unescapeBackSlashes(@NotNull String str) {
    StringBuilder buf = new StringBuilder(str.length());
    unescapeChar(buf, str, '\\');
    return buf.toString();
  }

  @Contract(pure = true)
  public static @NotNull String unescapeChar(@NotNull String str, char unescapeChar) {
    StringBuilder buf = new StringBuilder(str.length());
    unescapeChar(buf, str, unescapeChar);
    return buf.toString();
  }

  private static void unescapeChar(@NotNull StringBuilder buf, @NotNull String str, char unescapeChar) {
    int length = str.length();
    int last = length - 1;
    for (int i = 0; i < length; i++) {
      char ch = str.charAt(i);
      if (ch == '\\' && i != last) {
        //noinspection AssignmentToForLoopParameter
        i++;
        ch = str.charAt(i);
        if (ch != unescapeChar) buf.append('\\');
      }

      buf.append(ch);
    }
  }

  @Contract(mutates = "param1")
  public static void quote(@NotNull StringBuilder builder) {
    quote(builder, '\"');
  }

  @Contract(mutates = "param1")
  public static void quote(@NotNull StringBuilder builder, char quotingChar) {
    builder.insert(0, quotingChar);
    builder.append(quotingChar);
  }

  @Contract(pure = true)
  public static @NotNull String wrapWithDoubleQuote(@NotNull String str) {
    return '\"' + str + "\"";
  }

  /**
   * @deprecated Use {@link #unescapeXmlEntities(String)} instead
   */
  @Contract(value = "null -> null; !null -> !null",pure = true)
  @Deprecated
  public static String unescapeXml(@Nullable String text) {
    return text == null ? null : unescapeXmlEntities(text);
  }

  /**
   * @deprecated Use {@link #escapeXmlEntities(String)} instead
   */
  @Contract(value = "null -> null; !null -> !null",pure = true)
  @Deprecated
  public static String escapeXml(@Nullable String text) {
    return text == null ? null : escapeXmlEntities(text);
  }

  /**
   * @return {@code text} with some standard XML entities replaced with corresponding characters, e.g. '{@code &lt;}' replaced with '<'
   */
  @Contract(pure = true)
  public static @NotNull String unescapeXmlEntities(@NotNull String text) {
    return Strings.unescapeXmlEntities(text);
  }

  /**
   * @return {@code text} with some characters replaced with standard XML entities, e.g. '<' replaced with '{@code &lt;}'
   */
  @Contract(pure = true)
  public static @NotNull String escapeXmlEntities(@NotNull String text) {
    return Strings.escapeXmlEntities(text);
  }

  @Contract(pure = true)
  public static @NotNull String removeHtmlTags(@NotNull String htmlString) {
    return removeHtmlTags(htmlString, false);
  }

  @Contract(pure = true)
  public static @NotNull String removeHtmlTags(@NotNull String htmlString, boolean isRemoveStyleTag) {
    if (isEmpty(htmlString)) {
      return "";
    }

    MyHtml2Text parser = isRemoveStyleTag ? new MyHtml2Text(true) : new MyHtml2Text(false);
    try {
      parser.parse(new StringReader(htmlString));
    }
    catch (IOException e) {
      Logger.getInstance(StringUtil.class).error(e);
    }
    return parser.getText();
  }

  @Contract(pure = true)
  public static @NotNull @Nls String removeEllipsisSuffix(@NotNull @Nls String s) {
    if (s.endsWith(THREE_DOTS)) {
      return s.substring(0, s.length() - THREE_DOTS.length());
    }
    if (s.endsWith(ELLIPSIS)) {
      return s.substring(0, s.length() - ELLIPSIS.length());
    }
    return s;
  }

  private static final List<String> MN_QUOTED = Arrays.asList("&&", "__");
  private static final List<String> MN_CHARS = Arrays.asList("&", "_");

  @Contract(pure = true)
  public static @NotNull String escapeMnemonics(@NotNull String text) {
    return replace(text, MN_CHARS, MN_QUOTED);
  }

  @Contract(pure = true)
  public static @NlsSafe @NotNull String htmlEmphasize(@NotNull @Nls String text) {
    return HtmlChunk.tag("code").addText(text)
      .wrapWith("b").toString();
  }


  @Contract(pure = true)
  public static @NotNull String escapeToRegexp(@NotNull String text) {
    StringBuilder result = new StringBuilder(text.length());
    return escapeToRegexp(text, result).toString();
  }

  @Contract(mutates = "param2")
  public static @NotNull StringBuilder escapeToRegexp(@NotNull CharSequence text, @NotNull StringBuilder builder) {
    return Strings.escapeToRegexp(text, builder);
  }

  @Contract(pure = true)
  public static boolean isEscapedBackslash(@NotNull CharSequence text, int startOffset, int backslashOffset) {
    if (text.charAt(backslashOffset) != '\\') {
      return true;
    }
    boolean escaped = false;
    for (int i = startOffset; i < backslashOffset; i++) {
      if (text.charAt(i) == '\\') {
        escaped = !escaped;
      }
      else {
        escaped = false;
      }
    }
    return escaped;
  }

  @Contract(pure = true)
  public static @NotNull String replace(@NotNull String text, @NotNull List<String> from, @NotNull List<String> to) {
    return Strings.replace(text, from, to);
  }

  @Contract(pure = true)
  public static String @NotNull [] filterEmptyStrings(String @NotNull [] strings) {
    int emptyCount = 0;
    for (String string : strings) {
      if (string == null || string.isEmpty()) emptyCount++;
    }
    if (emptyCount == 0) return strings;

    String[] result = ArrayUtil.newStringArray(strings.length - emptyCount);
    int count = 0;
    for (String string : strings) {
      if (string == null || string.isEmpty()) continue;
      result[count++] = string;
    }

    return result;
  }

  @Contract(pure = true)
  public static int countNewLines(@NotNull CharSequence text) {
    return countChars(text, '\n');
  }

  @Contract(pure = true)
  public static int countChars(@NotNull CharSequence text, char c) {
    return Strings.countChars(text, c);
  }

  @Contract(pure = true)
  public static int countChars(@NotNull CharSequence text, char c, int offset, boolean stopAtOtherChar) {
    return Strings.countChars(text, c, offset, stopAtOtherChar);
  }

  @Contract(pure = true)
  public static int countChars(@NotNull CharSequence text, char c, int start, int end, boolean stopAtOtherChar) {
    return Strings.countChars(text, c, start, end, stopAtOtherChar);
  }

  /**
   * @param args Strings to join.
   * @return {@code null} if any of given Strings is {@code null}.
   */
  @Contract(pure = true)
  public static @Nullable String joinOrNull(String @NotNull ... args) {
    StringBuilder r = new StringBuilder();
    for (String arg : args) {
      if (arg == null) return null;
      r.append(arg);
    }
    return r.toString();
  }

  @Contract(pure = true)
  public static @Nullable String getPropertyName(@NotNull String methodName) {
    if (methodName.startsWith("get")) {
      return Introspector.decapitalize(methodName.substring(3));
    }
    if (methodName.startsWith("is")) {
      return Introspector.decapitalize(methodName.substring(2));
    }
    if (methodName.startsWith("set")) {
      return Introspector.decapitalize(methodName.substring(3));
    }
    return null;
  }

  @Contract(pure = true)
  public static boolean isJavaIdentifierStart(char c) {
    return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || Character.isJavaIdentifierStart(c);
  }

  @Contract(pure = true)
  public static boolean isJavaIdentifierPart(char c) {
    return c >= '0' && c <= '9' || c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || Character.isJavaIdentifierPart(c);
  }

  @Contract(pure = true)
  private static boolean isJavaIdentifierStart(int cp) {
    return cp >= 'a' && cp <= 'z' || cp >= 'A' && cp <= 'Z' || Character.isJavaIdentifierStart(cp);
  }

  @Contract(pure = true)
  private static boolean isJavaIdentifierPart(int cp) {
    return cp >= '0' && cp <= '9' || cp >= 'a' && cp <= 'z' || cp >= 'A' && cp <= 'Z' || Character.isJavaIdentifierPart(cp);
  }

  /**
   * @return true iff the string is a valid java identifier (according to JLS 3.8)
   */
  @Contract(pure = true)
  public static boolean isJavaIdentifier(@NotNull String text) {
    int len = text.length();
    if (len == 0) return false;
    int point = text.codePointAt(0);
    if (!isJavaIdentifierStart(point)) return false;
    int i = Character.charCount(point);

    while (i < len) {
      point = text.codePointAt(i);
      if (!isJavaIdentifierPart(point)) return false;
      i += Character.charCount(point);
    }
    return true;
  }

  /**
   * Checks if the char sequence slice is a valid java identifier (according to JLS 3.8)
   * @return true exactly iff {@code isJavaIdentifier(str.subSequence(startIncl, endExcl)) == true }
   */
  @Contract(pure = true)
  public static boolean isJavaIdentifier(@NotNull CharSequence str, int startIncl, int endExcl) {
    if (str.length() == 0) return false;
    Ref.BooleanRef isFirst = new Ref.BooleanRef();
    isFirst.element = true;
    return processCodePoints(str, startIncl, endExcl, codePoint -> {
      if (isFirst.element) {
        isFirst.element = false;
        if (!isJavaIdentifierStart(codePoint)) {
          return false;
        }
      } else {
        if (!isJavaIdentifierPart(codePoint)) {
          return false;
        }
      }
      return true;
    });
  }

  private interface CodePointProcessor {
    /**
     * @return true iff should continue
     */
    boolean acceptCodePoint(int codePoint);
  }


  private static boolean processCodePoints(@NotNull CharSequence str, int startIncl, int endExcl, CodePointProcessor processor) {
    int i = startIncl;
    while (i < endExcl) {
      char c1 = str.charAt(i++);
      if (!Character.isHighSurrogate(c1) || i >= endExcl) {
        if (!processor.acceptCodePoint(c1)) return false;
      }
      else {
        char c2 = str.charAt(i);
        if (Character.isLowSurrogate(c2)) {
          i++;
          if (!processor.acceptCodePoint(Character.toCodePoint(c1, c2))) return false;
        }
        else {
          if (!processor.acceptCodePoint(c2)) return false;
        }
      }
    }
    return true;
  }

  /**
   * Escape property name or key in property file. Unicode characters are escaped as well.
   *
   * @param input an input to escape
   * @param isKey if true, the rules for key escaping are applied. The leading space is escaped in that case.
   * @return an escaped string
   */
  @Contract(pure = true)
  public static @NotNull String escapeProperty(@NotNull String input, boolean isKey) {
    StringBuilder escaped = new StringBuilder(input.length());
    for (int i = 0; i < input.length(); i++) {
      char ch = input.charAt(i);
      switch (ch) {
        case ' ':
          if (isKey && i == 0) {
            // only the leading space has to be escaped
            escaped.append('\\');
          }
          escaped.append(' ');
          break;
        case '\t':
          escaped.append("\\t");
          break;
        case '\r':
          escaped.append("\\r");
          break;
        case '\n':
          escaped.append("\\n");
          break;
        case '\f':
          escaped.append("\\f");
          break;
        case '\\':
        case '#':
        case '!':
        case ':':
        case '=':
          escaped.append('\\');
          escaped.append(ch);
          break;
        default:
          if (20 < ch && ch < 0x7F) {
            escaped.append(ch);
          }
          else {
            escaped.append("\\u");
            escaped.append(Character.forDigit((ch >> 12) & 0xF, 16));
            escaped.append(Character.forDigit((ch >> 8) & 0xF, 16));
            escaped.append(Character.forDigit((ch >> 4) & 0xF, 16));
            escaped.append(Character.forDigit((ch) & 0xF, 16));
          }
          break;
      }
    }
    return escaped.toString();
  }

  @Contract(pure = true)
  public static @NotNull @NlsSafe String getQualifiedName(@Nullable @NonNls String packageName, @NotNull @NonNls String className) {
    if (packageName == null || packageName.isEmpty()) {
      return className;
    }
    return packageName + '.' + className;
  }

  /**
   * @see VersionComparatorUtil
   */
  @Contract(pure = true)
  public static int compareVersionNumbers(@Nullable String v1, @Nullable String v2) {
    // todo duplicates com.intellij.util.text.VersionComparatorUtil.compare
    // todo please refactor next time you make changes here
    if (v1 == null && v2 == null) {
      return 0;
    }
    if (v1 == null) {
      return -1;
    }
    if (v2 == null) {
      return 1;
    }

    String[] part1 = v1.split("[._\\-]");
    String[] part2 = v2.split("[._\\-]");

    int idx = 0;
    for (; idx < part1.length && idx < part2.length; idx++) {
      String p1 = part1[idx];
      String p2 = part2[idx];

      int cmp;
      if (p1.matches("\\d+") && p2.matches("\\d+")) {
        cmp = Integer.valueOf(p1).compareTo(Integer.valueOf(p2));
      }
      else {
        cmp = part1[idx].compareTo(part2[idx]);
      }
      if (cmp != 0) return cmp;
    }

    if (part1.length != part2.length) {
      boolean left = part1.length > idx;
      String[] parts = left ? part1 : part2;

      for (; idx < parts.length; idx++) {
        String p = parts[idx];
        int cmp;
        if (p.matches("\\d+")) {
          cmp = Integer.valueOf(p).compareTo(0);
        }
        else {
          cmp = 1;
        }
        if (cmp != 0) return left ? cmp : -cmp;
      }
    }
    return 0;
  }

  @Contract(pure = true)
  public static int getOccurrenceCount(@NotNull String text, char c) {
    int res = 0;
    int i = 0;
    while (i < text.length()) {
      i = text.indexOf(c, i);
      if (i >= 0) {
        res++;
        i++;
      }
      else {
        break;
      }
    }
    return res;
  }

  @Contract(pure = true)
  public static int getOccurrenceCount(@NotNull String text, @NotNull String s) {
    int res = 0;
    int i = 0;
    while (i < text.length()) {
      i = text.indexOf(s, i);
      if (i >= 0) {
        res++;
        i++;
      }
      else {
        break;
      }
    }
    return res;
  }

  @Contract(pure = true)
  public static @NotNull String fixVariableNameDerivedFromPropertyName(@NotNull String name) {
    if (isEmptyOrSpaces(name)) return name;
    char c = name.charAt(0);
    if (isVowel(c)) {
      return "an" + Character.toUpperCase(c) + name.substring(1);
    }
    return "a" + Character.toUpperCase(c) + name.substring(1);
  }

  @Contract(pure = true)
  public static @NotNull String sanitizeJavaIdentifier(@NotNull String name) {
    StringBuilder result = new StringBuilder(name.length());

    for (int i = 0; i < name.length(); i++) {
      char ch = name.charAt(i);
      if (Character.isJavaIdentifierPart(ch)) {
        if (result.length() == 0 && !Character.isJavaIdentifierStart(ch)) {
          result.append("_");
        }
        result.append(ch);
      }
    }

    return result.toString();
  }

  @Contract(pure = true)
  public static void assertValidSeparators(@NotNull CharSequence s) {
    char[] chars = CharArrayUtil.fromSequenceWithoutCopying(s);
    int slashRIndex = -1;

    if (chars != null) {
      for (int i = 0, len = s.length(); i < len; ++i) {
        if (chars[i] == '\r') {
          slashRIndex = i;
          break;
        }
      }
    }
    else {
      for (int i = 0, len = s.length(); i < len; i++) {
        if (s.charAt(i) == '\r') {
          slashRIndex = i;
          break;
        }
      }
    }

    if (slashRIndex != -1) {
      String context =
        String.valueOf(last(s.subSequence(0, slashRIndex), 10, true)) + first(s.subSequence(slashRIndex, s.length()), 10, true);
      context = escapeStringCharacters(context);
      throw new AssertionError("Wrong line separators: '" + context + "' at offset " + slashRIndex);
    }
  }

  @Contract(pure = true)
  public static @NotNull String tail(@NotNull String s, int idx) {
    return idx >= s.length() ? "" : s.substring(idx);
  }

  /**
   * Splits string by lines.
   *
   * @param string String to split
   * @return array of strings
   */
  @Contract(pure = true)
  public static String @NotNull [] splitByLines(@NotNull String string) {
    return splitByLines(string, true);
  }

  /**
   * Splits string by lines.
   * If several line separators are in a row, corresponding empty lines
   * are also added to result if {@code excludeEmptyStrings} is {@code false}.
   *
   * @param string String to split
   * @return array of strings
   */
  @Contract(pure = true)
  public static String @NotNull [] splitByLines(@NotNull String string, boolean excludeEmptyStrings) {
    return (excludeEmptyStrings ? Splitters.EOL_SPLIT_PATTERN : Splitters.EOL_SPLIT_PATTERN_WITH_EMPTY).split(string);
  }

  @Contract(pure = true)
  public static String @NotNull [] splitByLinesDontTrim(@NotNull String string) {
    return Splitters.EOL_SPLIT_DONT_TRIM_PATTERN.split(string);
  }

  /**
   * Splits string by lines, keeping all line separators at the line ends and in the empty lines.
   * <br> E.g., splitting text
   * <blockquote>
   *   foo\r\n<br>
   *   \n<br>
   *   bar\n<br>
   *   \r\n<br>
   *   baz\r<br>
   *   \r<br>
   * </blockquote>
   * will return the following array: foo\r\n, \n, bar\n, \r\n, baz\r, \r
   *
   */
  @Contract(pure = true)
  public static String @NotNull [] splitByLinesKeepSeparators(@NotNull String string) {
    return Splitters.EOL_SPLIT_KEEP_SEPARATORS.split(string);
  }
  @Contract(pure = true)
  public static @Unmodifiable @NotNull List<Pair<String, Integer>> getWordsWithOffset(@NotNull String s) {
    FreezableArrayList<Pair<String, Integer>> result = new FreezableArrayList<>();
    s += " ";
    StringBuilder name = new StringBuilder();
    int startInd = -1;
    for (int i = 0; i < s.length(); i++) {
      if (Character.isWhitespace(s.charAt(i))) {
        if (name.length() > 0) {
          result.add(Pair.create(name.toString(), startInd));
          name.setLength(0);
          startInd = -1;
        }
      }
      else {
        if (startInd == -1) {
          startInd = i;
        }
        name.append(s.charAt(i));
      }
    }
    return result.emptyOrFrozen();
  }

  @Contract(pure = true)
  public static int naturalCompare(@Nullable String string1, @Nullable String string2) {
    return NaturalComparator.INSTANCE.compare(string1, string2);
  }

  @Contract(pure = true)
  public static boolean isDecimalDigit(char c) {
    return Strings.isDecimalDigit(c);
  }

  @Contract("null -> false")
  public static boolean isNotNegativeNumber(@Nullable CharSequence s) {
    return Strings.isNotNegativeNumber(s);
  }

  @Contract(pure = true)
  public static int compare(@Nullable String s1, @Nullable String s2, boolean ignoreCase) {
    if (Strings.areSameInstance(s1, s2)) return 0;
    if (s1 == null) return -1;
    if (s2 == null) return 1;
    return ignoreCase ? s1.compareToIgnoreCase(s2) : s1.compareTo(s2);
  }

  @Contract(pure = true)
  public static int compare(@Nullable CharSequence s1, @Nullable CharSequence s2, boolean ignoreCase) {
    return Strings.compare(s1, s2, ignoreCase);
  }

  @Contract(pure = true)
  public static int comparePairs(@Nullable String s1, @Nullable String t1, @Nullable String s2, @Nullable String t2, boolean ignoreCase) {
    int compare = compare(s1, s2, ignoreCase);
    return compare != 0 ? compare : compare(t1, t2, ignoreCase);
  }

  @Contract(pure = true)
  public static boolean equals(@Nullable CharSequence s1, @Nullable CharSequence s2) {
    return StringUtilRt.equal(s1, s2, true);
  }

  @Contract(pure = true)
  public static boolean equalsIgnoreCase(@Nullable CharSequence s1, @Nullable CharSequence s2) {
    return StringUtilRt.equal(s1, s2, false);
  }

  @Contract(pure = true)
  public static boolean equalsIgnoreWhitespaces(@Nullable CharSequence s1, @Nullable CharSequence s2) {
    return Strings.equalsIgnoreWhitespaces(s1, s2);
  }

  @Contract(pure = true)
  public static boolean equalsTrimWhitespaces(@NotNull CharSequence s1, @NotNull CharSequence s2) {
    return Strings.equalsTrimWhitespaces(s1, s2);
  }

  /**
   * Collapses all white-space (including new lines) between non-white-space characters to a single space character.
   * Leading and trailing white space is removed.
   */
  @Contract(pure = true)
  public static @NotNull String collapseWhiteSpace(@NotNull CharSequence s) {
    StringBuilder result = null;
    int length = s.length();
    for (int i = 0; i < length;) {
      char ch = s.charAt(i);
      int ni = skipWhitespaceOrNewLineForward(s, i);
      if (ni == i+1 && (i == 0 || ch != ' ' || ni == length) || ni > i+1) {
        if (result == null) {
          result = new StringBuilder(length);
          result.append(s, 0, i);
        }
        if (i != 0 && ni != length) {
          result.append(' ');
        }
        i = ni;
      }
      else {
        if (result != null) {
          result.append(ch);
        }
        i++;
      }
    }
    return result == null ? s.toString() : result.toString();
  }

  @Contract(pure = true)
  public static boolean findIgnoreCase(@Nullable String toFind, String @NotNull ... where) {
    for (String string : where) {
      if (equalsIgnoreCase(toFind, string)) return true;
    }
    return false;
  }

  @Contract(pure = true)
  public static int compare(char c1, char c2, boolean ignoreCase) {
    // duplicating String.equalsIgnoreCase logic
    return Strings.compare(c1, c2, ignoreCase);
  }

  @Contract(pure = true)
  public static @NotNull String formatLinks(@NotNull String message) {
    Pattern linkPattern = Pattern.compile("http://[a-zA-Z\\d./\\-+]+");
    StringBuffer result = new StringBuffer();
    Matcher m = linkPattern.matcher(message);
    while (m.find()) {
      m.appendReplacement(result, "<a href=\"" + m.group() + "\">" + m.group() + "</a>");
    }
    m.appendTail(result);
    return result.toString();
  }

  @Contract(pure = true)
  public static boolean isHexDigit(char c) {
    return '0' <= c && c <= '9' || 'a' <= c && c <= 'f' || 'A' <= c && c <= 'F';
  }

  @Contract(pure = true)
  public static boolean isOctalDigit(char c) {
    return '0' <= c && c <= '7';
  }

  @Contract(pure = true)
  public static @NotNull String shortenTextWithEllipsis(@NotNull String text, int maxLength, int suffixLength) {
    return shortenTextWithEllipsis(text, maxLength, suffixLength, false);
  }

  @Contract(pure = true)
  public static @NotNull String trimMiddle(@NotNull String text, int maxLength) {
    return shortenTextWithEllipsis(text, maxLength, maxLength >> 1, true);
  }

  @Contract(pure = true)
  public static @NotNull String shortenTextWithEllipsis(@NotNull String text,
                                                        int maxLength,
                                                        int suffixLength,
                                                        @NotNull String symbol) {
    int textLength = text.length();
    if (textLength > maxLength) {
      int prefixLength = maxLength - suffixLength - symbol.length();
      assert prefixLength >= 0;
      return text.substring(0, prefixLength) + symbol + text.substring(textLength - suffixLength);
    }
    else {
      return text;
    }
  }

  @Contract(pure = true)
  public static @NotNull String shortenTextWithEllipsis(@NotNull String text,
                                                        int maxLength,
                                                        int suffixLength,
                                                        boolean useEllipsisSymbol) {
    String symbol = useEllipsisSymbol ? ELLIPSIS : "...";
    return shortenTextWithEllipsis(text, maxLength, suffixLength, symbol);
  }

  @Contract(pure = true)
  public static @NotNull String shortenPathWithEllipsis(@NotNull String path, int maxLength, boolean useEllipsisSymbol) {
    return shortenTextWithEllipsis(path, maxLength, (int)(maxLength * 0.7), useEllipsisSymbol);
  }

  @Contract(pure = true)
  public static @NotNull String shortenPathWithEllipsis(@NotNull String path, int maxLength) {
    return shortenPathWithEllipsis(path, maxLength, false);
  }

  @Contract(pure = true)
  public static boolean charsEqualIgnoreCase(char a, char b) {
    return Strings.charsEqualIgnoreCase(a, b);
  }

  @Contract(pure = true)
  public static char toUpperCase(char a) {
    return Strings.toUpperCase(a);
  }

  @Contract(value = "null -> null; !null -> !null", pure = true)
  public static String toUpperCase(String s) {
    return Strings.toUpperCase(s);
  }

  @Contract(pure = true)
  public static char toLowerCase(char a) {
    return Strings.toLowerCase(a);
  }

  @Contract(pure = true)
  public static boolean isUpperCase(@NotNull CharSequence sequence) {
    for (int i = 0; i < sequence.length(); i++) {
      if (!Character.isUpperCase(sequence.charAt(i))) return false;
    }
    return true;
  }

  @Contract(pure = true)
  public static boolean isLowerCase(@NotNull CharSequence sequence) {
    for (int i = 0; i < sequence.length(); i++) {
      if (!Character.isLowerCase(sequence.charAt(i))) return false;
    }
    return true;
  }

  @Contract(pure = true)
  public static @Nullable LineSeparator detectSeparators(@NotNull CharSequence text) {
    int index = indexOfAny(text, "\n\r");
    if (index == -1) return null;
    LineSeparator lineSeparator = getLineSeparatorAt(text, index);
    if (lineSeparator == null) {
      throw new AssertionError();
    }
    return lineSeparator;
  }

  @Contract(pure = true)
  public static @Nullable LineSeparator getLineSeparatorAt(@NotNull CharSequence text, int index) {
    if (index < 0 || index >= text.length()) {
      return null;
    }
    char ch = text.charAt(index);
    if (ch == '\r') {
      return index + 1 < text.length() && text.charAt(index + 1) == '\n' ? LineSeparator.CRLF : LineSeparator.CR;
    }
    return ch == '\n' ? LineSeparator.LF : null;
  }

  @Contract(pure = true)
  public static @NotNull String convertLineSeparators(@NotNull String text) {
    return Strings.convertLineSeparators(text);
  }

  @Contract(pure = true)
  public static @NotNull String convertLineSeparators(@NotNull String text, @NotNull String newSeparator) {
    return StringUtilRt.convertLineSeparators(text, newSeparator);
  }

  @Contract(pure = true)
  public static int parseInt(@Nullable String string, int defaultValue) {
    return StringUtilRt.parseInt(string, defaultValue);
  }

  @Contract(pure = true)
  public static long parseLong(@Nullable String string, long defaultValue) {
    return StringUtilRt.parseLong(string, defaultValue);
  }

  @Contract(pure = true)
  public static double parseDouble(@Nullable String string, double defaultValue) {
    return StringUtilRt.parseDouble(string, defaultValue);
  }

  @Contract(pure = true)
  public static <E extends Enum<E>> E parseEnum(@NotNull String string, E defaultValue, @NotNull Class<E> clazz) {
    try {
      return Enum.valueOf(clazz, string);
    }
    catch (Exception e) {
      return defaultValue;
    }
  }

  @Contract(pure = true)
  public static @NotNull @NlsSafe String getShortName(@NotNull Class<?> aClass) {
    return StringUtilRt.getShortName(aClass);
  }

  @Contract(pure = true)
  public static @NotNull @NlsSafe String getShortName(@NotNull @NonNls String fqName) {
    return StringUtilRt.getShortName(fqName);
  }

  @Contract(pure = true)
  public static @NotNull @NlsSafe String getShortName(@NotNull @NonNls String fqName, char separator) {
    return StringUtilRt.getShortName(fqName, separator);
  }

  /**
   * Equivalent for {@code getShortName(fqName).equals(shortName)}, but could be faster.
   *
   * @param fqName    fully-qualified name (dot-separated)
   * @param shortName a short name, which must not contain dots
   * @return true if specified short name is a short name of fully-qualified name
   */
  @Contract(pure = true)
  public static boolean isShortNameOf(@NotNull String fqName, @NotNull String shortName) {
    if (fqName.length() < shortName.length()) return false;
    if (fqName.length() == shortName.length()) return fqName.equals(shortName);
    int diff = fqName.length() - shortName.length();
    if (fqName.charAt(diff - 1) != '.') return false;
    return fqName.regionMatches(diff, shortName, 0, shortName.length());
  }

  /**
   * Strips class name from Object#toString if present.
   * To be used as custom data type renderer for java.lang.Object.
   * To activate just add {@code StringUtil.toShortString(this)}
   * expression in <em>Settings | Debugger | Data Views</em>.
   */
  @Contract("null->null;!null->!null")
  @SuppressWarnings("UnusedDeclaration")
  static String toShortString(@Nullable Object o) {
    if (o == null) return null;
    if (o instanceof CharSequence) return o.toString();
    String className = o.getClass().getName();
    String s = o.toString();
    if (!s.startsWith(className)) return s;
    return s.length() > className.length() && !Character.isLetter(s.charAt(className.length())) ?
           trimStart(s, className) : s;
  }

  /**
   * @param sequence original CharSequence
   * @param delayMillis max delay in milliseconds
   * @return a wrapped CharSequence that throws {@link ProcessCanceledException} if still accessed after delay.
   */
  @Contract(pure = true)
  public static @NotNull CharSequence newBombedCharSequence(@NotNull CharSequence sequence, long delayMillis) {
    long myTime = System.currentTimeMillis() + delayMillis;
    return new BombedCharSequence(sequence) {
      @Override
      protected void checkCanceled() {
        long l = System.currentTimeMillis();
        if (l >= myTime) {
          throw new ProcessCanceledException();
        }
      }
    };
  }

  @Contract(mutates = "param1")
  public static boolean trimEnd(@NotNull StringBuilder buffer, @NotNull CharSequence end) {
    if (endsWith(buffer, end)) {
      buffer.delete(buffer.length() - end.length(), buffer.length());
      return true;
    }
    return false;
  }

  /**
   * Say smallPart = "op" and bigPart="open". Method returns true for "Ope" and false for "ops"
   */
  @Contract(pure = true)
  public static boolean isBetween(@NotNull String string, @NotNull String smallPart, @NotNull String bigPart) {
    String s = toLowerCase(string);
    return s.startsWith(toLowerCase(smallPart)) && toLowerCase(bigPart).startsWith(s);
  }

  /**
   * Does the string have an uppercase character?
   * @param s  the string to test.
   * @return   true if the string has an uppercase character, false if not.
   */
  @Contract(pure = true)
  public static boolean hasUpperCaseChar(@NotNull String s) {
    for (char c : s.toCharArray()) {
      if (Character.isUpperCase(c)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Does the string have a lowercase character?
   * @param s  the string to test.
   * @return   true if the string has a lowercase character, false if not.
   */
  @Contract(pure = true)
  public static boolean hasLowerCaseChar(@NotNull String s) {
    for (char c : s.toCharArray()) {
      if (Character.isLowerCase(c)) {
        return true;
      }
    }
    return false;
  }

  @Contract(pure = true)
  public static String replaceUnicodeEscapeSequences(String text) {
    if (text == null) return null;
    final int length = text.length();
    StringBuilder sb = new StringBuilder(text.length());
    outer:
    for (int i = 0; i < length; i++) {
      char c = text.charAt(i);
      if (c == '\\') {
        int j = i + 1;
        boolean escape = true;
        while (j < length && (c = text.charAt(j)) == '\\') {
          escape = !escape;
          j++;
        }
        if (!escape || c != 'u') {
          sb.append(text, i, j);
          i = j - 1;
          continue;
        }
        while (j < length && (c = text.charAt(j)) == 'u') j++;
        if (j > length - 4) {
          sb.append(text, i, j);
          i = j - 1;
          continue;
        }
        for (int k = 0; k < 4; k++) {
          if (!isHexDigit(text.charAt(j + k))) {
            sb.append(text, i, j + k);
            i = j + k - 1;
            continue outer;
          }
        }
        final char d = (char)Integer.parseInt(text.substring(j, j + 4), 16);
        sb.append(d);
        i = j + 3;
      }
      else {
        sb.append(c);
      }
    }

    return sb.toString();
  }

  /**
   * Expirable CharSequence. Very useful to control external library execution time,
   * i.e. when java.util.regex.Pattern match goes out of control.
   */
  public abstract static class BombedCharSequence implements CharSequence {
    private final CharSequence delegate;
    private int i;
    private boolean myDefused;

    protected BombedCharSequence(@NotNull CharSequence sequence) {
      delegate = sequence;
    }

    @Override
    public int length() {
      check();
      return delegate.length();
    }

    @Override
    public char charAt(int i) {
      check();
      return delegate.charAt(i);
    }

    protected void check() {
      if (myDefused) {
        return;
      }
      if ((++i & 1023) == 0) {
        checkCanceled();
      }
    }

    public final void defuse() {
       myDefused = true;
    }

    @Override
    public @NotNull String toString() {
      check();
      return delegate.toString();
    }

    protected abstract void checkCanceled();

    @Override
    public @NotNull CharSequence subSequence(int start, int end) {
      check();
      CharSequence subSequence = delegate.subSequence(start, end);
      BombedCharSequence bombedParent = this instanceof ChildBombedCharSequence ? ((ChildBombedCharSequence)this).myBombedParent : this;
      return new ChildBombedCharSequence(subSequence, bombedParent);
    }
  }

  private static final class ChildBombedCharSequence extends BombedCharSequence {
    private final BombedCharSequence myBombedParent;

    ChildBombedCharSequence(@NotNull CharSequence sequence,
                            @NotNull BombedCharSequence bombedParent) {
      super(sequence);
      myBombedParent = bombedParent;
    }

    @Override
    protected void check() {
      myBombedParent.check();
    }

    @Override
    protected void checkCanceled() {
      throw new UnsupportedOperationException();
    }
  }

  @Contract(pure = true)
  public static @NotNull String toHexString(byte @NotNull [] bytes) {
    String digits = "0123456789abcdef";
    StringBuilder sb = new StringBuilder(2 * bytes.length);
    for (byte b : bytes) {
      sb.append(digits.charAt((b >> 4) & 0xf)).append(digits.charAt(b & 0xf));
    }
    return sb.toString();
  }

  @Contract(pure = true)
  public static byte @NotNull [] parseHexString(@NotNull String str) {
    int len = str.length();
    if (len % 2 != 0) throw new IllegalArgumentException("Non-even-length: " + str);
    byte[] bytes = new byte[len / 2];
    for (int i = 0; i < len; i += 2) {
      bytes[i / 2] = (byte)((Character.digit(str.charAt(i), 16) << 4) + Character.digit(str.charAt(i + 1), 16));
    }
    return bytes;
  }

  /**
   * @return {@code true} if the passed string is not {@code null} and not empty
   * and contains only latin upper- or lower-case characters and digits; {@code false} otherwise.
   */
  @Contract(pure = true)
  public static boolean isLatinAlphanumeric(@Nullable CharSequence str) {
    if (isEmpty(str)) {
      return false;
    }
    for (int i = 0; i < str.length(); i++) {
      char c = str.charAt(i);
      if ((c < 'A' || c > 'Z') && (c < 'a' || c > 'z') && !Character.isDigit(c)) {
        return false;
      }
    }
    return true;
  }

  /**
   * @return {@code true} if the passed string is not {@code null} and not empty
   * and contains only digits; {@code false} otherwise.
   */
  @Contract(pure = true)
  public static boolean isNumeric(@Nullable CharSequence str) {
    if (isEmpty(str)) {
      return false;
    }

    for (int i = 0; i < str.length(); i++) {
      if (!Character.isDigit(str.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  @Contract(value = "null -> null; !null->!null", pure = true)
  public static String internEmptyString(String s) {
    return s == null ? null : s.isEmpty() ? "" : s;
  }

  /**
   * Finds the next position in the supplied CharSequence which is neither a space nor a tab.
   *
   * @param text text
   * @param pos  starting position
   * @return position of the first non-whitespace character after or equal to pos; or the length of the CharSequence
   * if no non-whitespace character found
   */
  @Contract(pure = true)
  public static int skipWhitespaceForward(@NotNull CharSequence text, int pos) {
    int length = text.length();
    while (pos < length && isWhitespaceOrTab(text.charAt(pos))) {
      pos++;
    }
    return pos;
  }

  /**
   * Finds the next position in the supplied CharSequence which is neither a space, a new line nor a tab.
   *
   * @param text text
   * @param pos  starting position
   * @return position of the first non-whitespace character after or equal to pos; or the length of the CharSequence
   * if no non-whitespace character found
   */
  @Contract(pure = true)
  public static int skipWhitespaceOrNewLineForward(@NotNull CharSequence text, int pos) {
    int length = text.length();
    while (pos < length && isWhitespaceTabOrNewLine(text.charAt(pos))) {
      pos++;
    }
    return pos;
  }

  /**
   * Finds the previous position in the supplied CharSequence which is neither a space nor a tab.
   *
   * @param text text
   * @param pos  starting position
   * @return position of the character before or equal to pos which has no space or tab before;
   * or zero if no non-whitespace character found
   */
  @Contract(pure = true)
  public static int skipWhitespaceBackward(@NotNull CharSequence text, int pos) {
    while (pos > 0 && isWhitespaceOrTab(text.charAt(pos - 1))) {
      pos--;
    }
    return pos;
  }

  /**
   * Finds the previous position in the supplied CharSequence which is neither a space, a new line nor a tab.
   *
   * @param text text
   * @param pos  starting position
   * @return position of the character before or equal to pos which has no space or tab before;
   * or zero if no non-whitespace character found
   */
  @Contract(pure = true)
  public static int skipWhitespaceOrNewLineBackward(@NotNull CharSequence text, int pos) {
    while (pos > 0 && isWhitespaceTabOrNewLine(text.charAt(pos - 1))) {
      pos--;
    }
    return pos;
  }

  private static boolean isWhitespaceOrTab(char c) {
    return c == ' ' || c == '\t';
  }

  private static boolean isWhitespaceTabOrNewLine(char c) {
    return c == ' ' || c == '\t' || c == '\n';
  }

  @Contract("null,!null,_ -> false; !null,null,_ -> false; null,null,_ -> true")
  public static boolean equal(@Nullable CharSequence s1, @Nullable CharSequence s2, boolean caseSensitive) {
    return StringUtilRt.equal(s1, s2, caseSensitive);
  }

  @Contract(pure = true)
  public static @NotNull String convertLineSeparators(@NotNull String text, boolean keepCarriageReturn) {
    return StringUtilRt.convertLineSeparators(text, keepCarriageReturn);
  }

  @Contract(pure = true)
  public static @NotNull CharSequence convertLineSeparators(@NotNull CharSequence text, @NotNull String newSeparator) {
    return StringUtilRt.convertLineSeparators(text, newSeparator);
  }

  @Contract(pure = true)
  public static @NotNull String convertLineSeparators(@NotNull String text, @NotNull String newSeparator, int @Nullable [] offsetsToKeep) {
    return StringUtilRt.convertLineSeparators(text, newSeparator, offsetsToKeep);
  }

  @Contract(pure = true)
  public static @NotNull String convertLineSeparators(@NotNull String text,
                                                      @NotNull String newSeparator,
                                                      int @Nullable [] offsetsToKeep,
                                                      boolean keepCarriageReturn) {
    return StringUtilRt.convertLineSeparators(text, newSeparator, offsetsToKeep, keepCarriageReturn);
  }

  @Contract(pure = true)
  public static boolean startsWithIgnoreCase(@NotNull String str, int startOffset, @NotNull String prefix) {
    return StringUtilRt.startsWithIgnoreCase(str, startOffset, prefix);
  }

  @Contract(pure = true)
  public static boolean endsWithIgnoreCase(@NotNull CharSequence text, @NotNull CharSequence suffix) {
    return StringUtilRt.endsWithIgnoreCase(text, suffix);
  }

  @Contract(pure = true)
  public static @NotNull String formatFileSize(long fileSize, @NotNull String unitSeparator, int rank) {
    return StringUtilRt.formatFileSize(fileSize, unitSeparator, rank);
  }

  /**
   * @see StringUtilRt#formatFileSize(long, String, int, boolean)
   */
  @Contract(pure = true)
  public static @NotNull String formatFileSize(long fileSize, @NotNull String unitSeparator, int rank, boolean fixedFractionPrecision) {
    return StringUtilRt.formatFileSize(fileSize, unitSeparator, rank, fixedFractionPrecision);
  }

  @Contract(pure = true)
  public static int rankForFileSize(long fileSize) {
    return StringUtilRt.rankForFileSize(fileSize);
  }
}
