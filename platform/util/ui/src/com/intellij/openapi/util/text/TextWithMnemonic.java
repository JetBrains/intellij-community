// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.text;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.*;

import java.awt.event.KeyEvent;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An immutable object which represents a text string with mnemonic character.
 */
public final class TextWithMnemonic {
  private static final Logger LOG = Logger.getInstance(TextWithMnemonic.class);

  public static final TextWithMnemonic EMPTY = new TextWithMnemonic("", -1, "");
  private static final Pattern MNEMONIC = Pattern.compile(" ?\\(_?[A-Z]\\)");

  private final @NotNull @Nls String text;
  /**
   * Mnemonic index (-1 = no mnemonic)
   */
  private final int mnemonicIndex;
  /**
   * A text that can be appended to myText to display a mnemonic that doesn't belong to the text naturally.
   * Ex: "Help (P)" - " (P)" will be extracted into a suffix.
   */
  private final @NotNull @Nls String mnemonicSuffix;

  private TextWithMnemonic(@NotNull @Nls String text, int mnemonicIndex, @NotNull @Nls String mnemonicSuffix) {
    assert mnemonicIndex >= 0 || mnemonicSuffix.isEmpty();
    assert mnemonicIndex >= -1 && mnemonicIndex < text.length() + mnemonicSuffix.length();
    this.text = text;
    this.mnemonicIndex = mnemonicIndex;
    this.mnemonicSuffix = mnemonicSuffix;
  }

  /**
   * @return plain text without mnemonic
   */
  public @NotNull @Nls String getText() {
    return getText(false);
  }

  /**
   * @param withMnemonicSuffix if true, add the mnemonic suffix (but without mnemonic)
   * @return plain text without mnemonic
   */
  public @NotNull @Nls String getText(boolean withMnemonicSuffix) {
    return format(false, null, withMnemonicSuffix);
  }

  /**
   * @return an extended key code for a mnemonic character, or {@link KeyEvent#VK_UNDEFINED} if mnemonic is not set
   */
  public int getMnemonicCode() {
    char ch = getMnemonicChar();
    return ch == KeyEvent.CHAR_UNDEFINED ? KeyEvent.VK_UNDEFINED : KeyEvent.getExtendedKeyCodeForChar(ch);
  }

  /**
   * @return a mnemonic character, or {@link KeyEvent#CHAR_UNDEFINED} if mnemonic is not set
   */
  public char getMnemonicChar() {
    if (mnemonicIndex < 0) return KeyEvent.CHAR_UNDEFINED;
    int index = mnemonicIndex - text.length();
    return index < 0 ? text.charAt(mnemonicIndex) : mnemonicSuffix.charAt(index);
  }

  /**
   * @return a mnemonic index if it's set; -1 otherwise
   */
  public int getMnemonicIndex() {
    return mnemonicIndex;
  }

  /**
   * @return true if mnemonic is set
   */
  public boolean hasMnemonic() {
    return mnemonicIndex >= 0;
  }

  /**
   * Drops a mnemonic
   * @return a TextWithMnemonic object where mnemonic is not set
   */
  public TextWithMnemonic dropMnemonic(boolean forceRemove) {
    if (!hasMnemonic()) return this;
    if (!forceRemove) return fromPlainText(text);

    Matcher matcher = MNEMONIC.matcher(text);
    if (matcher.find()) {
      //noinspection HardCodedStringLiteral
      return fromPlainText(matcher.replaceAll(""));
    }
    return this;
  }

  /**
   * @param index mnemonic index within the {@link #getText() text}, or {@code -1} to remove mnemonic
   * @return new {@code TextWithMnemonic} object with updated mnemonic index, or the same instance otherwise
   * @throws IndexOutOfBoundsException if mnemonic index cannot be used
   */
  public @NotNull TextWithMnemonic withMnemonicIndex(int index) {
    if (index == mnemonicIndex) return this;
    if (index >= -1) {
      String text = getText();
      if (index == -1) return fromPlainText(text);
      if (index < text.length() - getEllipsisLength(text)) return fromPlainTextWithIndex(text, index);
    }
    throw new IndexOutOfBoundsException(String.valueOf(index));
  }

  /**
   * Appends given a text to the current text.
   *
   * @param textToAppend text to append. Appended text is treated as a plain text, without mnemonic, so mnemonic position is unchanged.
   * @return TextWithMnemonic object which text is the concatenation of this object text and supplied text.
   */
  public TextWithMnemonic append(@NotNull @Nls String textToAppend) {
    return new TextWithMnemonic(text + textToAppend,
                                mnemonicIndex < text.length() ? mnemonicIndex : mnemonicIndex + textToAppend.length(),
                                mnemonicSuffix);
  }

  /**
   * Replaces the first occurrence of a given target text with the given replacement text.
   *
   * @param target the target text to be replaced
   * @param replacement the replacement text which is treated as a plain text, without mnemonic.
   * @return TextWithMnemonic object. The resulting mnemonic position could be adjusted if the mnemonic was located after the replacement.
   *          If the mnemonic was inside the target text, then it's dropped. Return this object if the target text was not found.
   */
  public TextWithMnemonic replaceFirst(@NotNull String target, @Nls @NotNull String replacement) {
    int index = text.indexOf(target);
    if (index == -1) {
      return this;
    }
    String resultText = text.substring(0, index) + replacement + text.substring(index + target.length());
    int resultIndex = mnemonicIndex < index ? mnemonicIndex :
                      mnemonicIndex >= index + target.length() ? mnemonicIndex - target.length() + replacement.length() :
                      -1;
    return new TextWithMnemonic(resultText, resultIndex, mnemonicSuffix);
  }

  @ApiStatus.Internal
  public static @Nullable TextWithMnemonic fromMnemonicText(@NotNull @Nls String text) {
    return fromMnemonicText(text, true);
  }

  /**
   * @param text a text with a mnemonic specified by the {@link UIUtil#MNEMONIC MNEMONIC} marker
   * @return new {@code TextWithMnemonic} object, or {@code null} if mnemonic is not specified in the given text
   * @see UIUtil#replaceMnemonicAmpersand
   */
  @ApiStatus.Internal
  public static @Nullable TextWithMnemonic fromMnemonicText(@NotNull @Nls String text, boolean reportInvalidMnemonics) {
    int pos = text.indexOf(UIUtil.MNEMONIC);
    if (pos < 0) return null;
    String str = text.substring(pos + 1);

    Exception error = null;
    // Don't write text in log to avoid possible sensitive information exposure
    String errorDetails = reportInvalidMnemonics ? " in text: " + text : "";
    if (str.isEmpty()) {
      error = new IllegalArgumentException("unexpected mnemonic marker" + errorDetails);
    }
    if (str.indexOf(UIUtil.MNEMONIC) >= 0) {
      error = new IllegalArgumentException("several mnemonic markers" + errorDetails);
    }

    if (error != null) {
      if (reportInvalidMnemonics) {
        LOG.error(error);
      }
      else {
        LOG.warn(error);
      }
      return null;
    }

    return fromPlainTextWithIndex(pos > 0 ? text.substring(0, pos) + str : str, pos);
  }

  /**
   * Creates a TextWithMnemonic object from a plain text without mnemonic.
   * @param text a plain text to create a TextWithMnemonic object from
   * @return new TextWithMnemonic object which has no mnemonic
   */
  @Contract(pure = true)
  public static @NotNull TextWithMnemonic fromPlainText(@NotNull @Nls String text) {
    return text.isEmpty() ? EMPTY : new TextWithMnemonic(text, -1, "");
  }

  /**
   * Creates a TextWithMnemonic object from a plain text without mnemonic.
   * @param text a plain text to create a TextWithMnemonic object from
   * @param mnemonicChar mnemonic character (0 = absent mnemonic)
   * @return new TextWithMnemonic object which has given mnemonic character.
   * If the text doesn't contain the supplied character, then mnemonicChar is appended in parentheses.
   */
  @Contract(pure = true)
  public static @NotNull TextWithMnemonic fromPlainText(@NotNull @Nls String text, char mnemonicChar) {
    if (mnemonicChar == 0) {
      return fromPlainText(text);
    }
    mnemonicChar = Character.toUpperCase(mnemonicChar);
    for (int i = 0; i < text.length(); i++) {
      if (Character.toUpperCase(text.charAt(i)) == mnemonicChar) {
        return new TextWithMnemonic(text, i, "");
      }
    }
    int ellipsisLength = getEllipsisLength(text);
    String suffix = "(" + mnemonicChar + ")" + text.substring(text.length() - ellipsisLength);
    text = text.substring(0, text.length() - ellipsisLength);
    return new TextWithMnemonic(text, text.length() + 1, suffix);
  }

  /**
   * Creates new {@code TextWithMnemonic} object from a plain text with a mnemonic specified by its index.
   *
   * @param text  a plain text
   * @param index a mnemonic index in the given text, or {@code -1} if it is not needed
   * @return new {@code TextWithMnemonic} object that may have a mnemonic
   */
  public static @NotNull TextWithMnemonic fromPlainTextWithIndex(@NotNull @Nls String text, int index) {
    if (index < 0) return fromPlainText(text);
    if (index < text.length()) {
      // try to extract a mnemonic suffix
      int pos = text.length() - getEllipsisLength(text) - 3; // the length of "(M)"
      if (pos >= 0 && index == (pos + 1) && text.charAt(pos) == '(' && text.charAt(pos + 2) == ')') {
        while (pos > 0 && text.charAt(pos - 1) == ' ') pos--; // skip spaces
        return new TextWithMnemonic(text.substring(0, pos), index, text.substring(pos));
      }
    }
    return new TextWithMnemonic(text, index, "");
  }

  /**
   * Parses a text in text-with-mnemonic format.
   * A mnemonic is prepended either with '_', or with '&' or with '\x1B' character.
   * To escape '_' or '&' before the actual mnemonic the character must be duplicated.
   * The characters after the actual mnemonic should not be escaped.
   * E.g. "A__b_c__d" in text-with-mnemonic format will be displayed as "A_bc__d" with mnemonic 'c'.
   *
   * @param text text to parse
   * @return TextWithMnemonic object which corresponds to the parsed text.
   */
  @Contract(pure = true)
  public static @NotNull TextWithMnemonic parse(@NotNull @Nls String text) {
    TextWithMnemonic mnemonic = text.isEmpty() ? EMPTY : fromMnemonicText(text);
    if (mnemonic != null) {
      return mnemonic;
    }

    if (text.contains("_") || text.contains("&")) {
      @Nls StringBuilder plainText = new StringBuilder();
      int mnemonicIndex = -1;

      int backShift = 0;
      for (int i = 0; i < text.length(); i++) {
        char ch = text.charAt(i);
        if (mnemonicIndex == -1 && (ch == '_' || ch == '&')) {
          //noinspection AssignmentToForLoopParameter
          i++;
          if (i >= text.length()) break;
          ch = text.charAt(i);
          if (ch != '_' && ch != '&') {
            mnemonicIndex = i - 1 - backShift;
          }
          else {
            backShift++;
          }
        }
        plainText.append(ch);
      }
      return fromPlainTextWithIndex(plainText.toString(), mnemonicIndex);
    }
    return fromPlainText(text);
  }

  private static int getEllipsisLength(String text) {
    if (text.endsWith("...")) {
      return 3;
    }
    if (text.endsWith("…")) {
      return 1;
    }
    return 0;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TextWithMnemonic mnemonic = (TextWithMnemonic)o;
    return mnemonicIndex == mnemonic.mnemonicIndex &&
           text.equals(mnemonic.text) &&
           mnemonicSuffix.equals(mnemonic.mnemonicSuffix);
  }

  @Override
  public int hashCode() {
    return (text.hashCode() * 31 + mnemonicIndex) * 31 + mnemonicSuffix.hashCode();
  }

  /**
   * @return text in text-with-mnemonic format. Parsing back this text using {@link #parse(String)} method would create
   * a TextWithMnemonic object which is equal to this.
   */
  @Override
  public @Nls String toString() {
    return format(true, '_', true);
  }

  /**
   * The power version of {@code getText} and {@code toString}
   * <p>
   *   Just a utility function that covers various edge cases. Some quirks:
   *   <ul>
   *     <li>If {@code escapeMnemonics} is {@code true}, and there's an actual mnemonic character,
   *     and {@code mnemonicPrefix} is not {@code null}, then only the part of the string up to the actual
   *     mnemonic character is escaped. This is a legacy implementation detail. If the string does <em>not</em>
   *     contain a mnemonic character, then everything will be escaped.</li>
   *     <li>If {@code withSuffix} is false, but there's an actual suffix, then {@code mnemonicPrefix} is ignored and not used,
   *     the resulting string will not contain the mnemonic (because it was in the suffix).</li>
   *     <li>The combination of {@code escapeMnemonics == false} and {@code mnemonicPrefix != null} only makes sense
   *     if you know in advance that the string doesn't actually contain {@code mnemonicPrefix} and you don't want other mnemonic
   *     prefixes (e.g. {@code '_'} or {@code '&'}) to be escaped. Usually used with non-printable prefixes such as
   *     {@link UIUtil#MNEMONIC}.</li>
   *   </ul>
   * </p>
   * @param escapeMnemonics if true, then mnemonic characters up to the actual mnemonic will be escaped
   * @param mnemonicPrefix if specified, will be inserted before the mnemonic character
   * @param withSuffix      if true, then the returned text will include the mnemonic suffix, if any
   * @see #getText(boolean)
   * @see #toString()
   * @return formatted string
   */
  @ApiStatus.Internal
  public @NotNull @Nls String format(boolean escapeMnemonics, @Nullable Character mnemonicPrefix, boolean withSuffix) {
    String completeText;
    boolean completeTextIncludesMnemonics;
    if (mnemonicSuffix.isEmpty()) {
      completeText = text;
      completeTextIncludesMnemonics = mnemonicIndex >= 0 && mnemonicIndex < text.length();
    }
    else if (withSuffix) {
      completeText = text + mnemonicSuffix;
      completeTextIncludesMnemonics = mnemonicIndex >= 0 && mnemonicIndex < completeText.length();
    }
    else {
      int ellipsisLength = getEllipsisLength(mnemonicSuffix);
      completeText = ellipsisLength == 0 ? text : text + mnemonicSuffix.substring(mnemonicSuffix.length() - ellipsisLength);
      completeTextIncludesMnemonics = mnemonicIndex >= 0 && mnemonicIndex < text.length();
    }
    if (mnemonicPrefix != null && completeTextIncludesMnemonics) {
      String firstPart = completeText.substring(0, mnemonicIndex);
      String secondPart = completeText.substring(mnemonicIndex);
      return escapeMnemonics // A legacy implementation quirk: we don't escape anything after the actual mnemonic.
             ? StringUtil.escapeMnemonics(firstPart) + mnemonicPrefix + secondPart
             : firstPart + mnemonicPrefix + secondPart;
    }
    else {
      return escapeMnemonics ? StringUtil.escapeMnemonics(completeText) : completeText;
    }
  }
}
