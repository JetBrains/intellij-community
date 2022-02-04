// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.text;

import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.awt.event.KeyEvent;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An immutable object which represents a text string with mnemonic character.
 */
public final class TextWithMnemonic {
  public static final Pattern MNEMONIC = Pattern.compile(" ?\\(_?[A-Z]\\)");

  @NotNull private final @Nls String myText;
  /**
   * Mnemonic index (-1 = no mnemonic)
   */
  private final int myMnemonicIndex;
  /**
   * A text that can be appended to myText to display a mnemonic that doesn't belong to the text naturally. 
   * Ex: "Help (P)" - " (P)" will be extracted into a suffix.
   */
  private final @NotNull @Nls String myMnemonicSuffix;

  private TextWithMnemonic(@NotNull @Nls String text, int mnemonicIndex, @NotNull @Nls String mnemonicSuffix) {
    assert mnemonicIndex >= 0 || mnemonicSuffix.isEmpty();
    assert mnemonicIndex >= -1 && mnemonicIndex < text.length() + mnemonicSuffix.length();
    myText = StringUtil.internEmptyString(text);
    myMnemonicIndex = mnemonicIndex;
    myMnemonicSuffix = mnemonicSuffix;
  }

  /**
   * @return plain text without mnemonic
   */
  @NotNull
  public @Nls String getText() {
    return myText + myMnemonicSuffix.substring(myMnemonicSuffix.length() - getEllipsisLength(myMnemonicSuffix));
  }

  /**
   * @param withMnemonicSuffix if true add the mnemonic suffix (but without mnemonic)
   * @return plain text without mnemonic
   */
  @NotNull
  public @Nls String getText(boolean withMnemonicSuffix) {
    return withMnemonicSuffix ? myText + myMnemonicSuffix : getText();
  }

  /**
   * @return a mnemonic character (upper-cased) if mnemonic is set; 0 otherwise
   * @deprecated use {@link #getMnemonicChar} or {@link #getMnemonicCode} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2023.1")
  public int getMnemonic() {
    char ch = getMnemonicChar();
    return ch == KeyEvent.CHAR_UNDEFINED ? 0 : Character.toUpperCase(ch);
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
    if (myMnemonicIndex < 0) return KeyEvent.CHAR_UNDEFINED;
    int index = myMnemonicIndex - myText.length();
    return index < 0 ? myText.charAt(myMnemonicIndex) : myMnemonicSuffix.charAt(index);
  }

  /**
   * @return a mnemonic index if it's set; -1 otherwise
   */
  public int getMnemonicIndex() {
    return myMnemonicIndex;
  }

  /**
   * @return true if mnemonic is set
   */
  public boolean hasMnemonic() {
    return myMnemonicIndex >= 0;
  }

  /**
   * Drops a mnemonic
   * @return a TextWithMnemonic object where mnemonic is not set
   */
  public TextWithMnemonic dropMnemonic(boolean forceRemove) {
    if (!hasMnemonic()) return this;
    if (!forceRemove) return fromPlainText(myText);

    Matcher matcher = MNEMONIC.matcher(myText);
    if (matcher.find()) {
      //noinspection HardCodedStringLiteral
      return fromPlainText(matcher.replaceAll(""));
    }
    return this;
  }

  /**
   * Sets mnemonic at given index
   * @param index index, must be within the {@link #getText() text} string.
   * @return a TextWithMnemonic object with mnemonic set at given index
   */
  public TextWithMnemonic setMnemonicAt(int index) {
    if (index < 0 || index >= myText.length() + myMnemonicSuffix.length()) {
      throw new IndexOutOfBoundsException(String.valueOf(index));
    }
    return index == myMnemonicIndex ? this : new TextWithMnemonic(myText, index, myMnemonicSuffix);
  }

  /**
   * Appends given text to the current text.
   * 
   * @param textToAppend text to append. Appended text is treated as a plain text, without mnemonic, so mnemonic position is unchanged.
   * @return TextWithMnemonic object which text is the concatenation of this object text and supplied text.
   */
  public TextWithMnemonic append(@NotNull @Nls String textToAppend) {
    return new TextWithMnemonic(myText + textToAppend,
                                myMnemonicIndex < myText.length() ? myMnemonicIndex : myMnemonicIndex + textToAppend.length(),
                                myMnemonicSuffix);
  }

  /**
   * Replaces the first occurrence of given target text with the given replacement text.
   * 
   * @param target the target text to be replaced
   * @param replacement the replacement text which is treated as a plain text, without mnemonic.
   * @return TextWithMnemonic object. The resulting mnemonic position could be adjusted if the mnemonic was located after the replacement.
   *          If the mnemonic was inside the target text then it's dropped. Returns this object if the target text was not found.
   */
  public TextWithMnemonic replaceFirst(@NotNull String target, @Nls @NotNull String replacement) {
    int index = myText.indexOf(target);
    if (index == -1) {
      return this;
    }
    String resultText = myText.substring(0, index) + replacement + myText.substring(index + target.length());
    int resultIndex = myMnemonicIndex < index ? myMnemonicIndex : 
                      myMnemonicIndex >= index + target.length() ? myMnemonicIndex - target.length() + replacement.length() :
                      -1;
    return new TextWithMnemonic(resultText, resultIndex, myMnemonicSuffix);
  }

  /**
   * Creates a TextWithMnemonic object from a plain text without mnemonic.
   * @param text a plain text to create a TextWithMnemonic object from 
   * @return new TextWithMnemonic object which has no mnemonic
   */
  @NotNull
  @Contract(pure = true)
  public static TextWithMnemonic fromPlainText(@NotNull @Nls String text) {
    return new TextWithMnemonic(text, -1, "");
  }

  /**
   * Creates a TextWithMnemonic object from a plain text without mnemonic.
   * @param text a plain text to create a TextWithMnemonic object from
   * @param mnemonicChar mnemonic character (0 = absent mnemonic)
   * @return new TextWithMnemonic object which has given mnemonic character. 
   * If the text doesn't contain the supplied character then mnemonicChar is appended in parentheses.
   */
  @NotNull
  @Contract(pure = true)
  public static TextWithMnemonic fromPlainText(@NotNull @Nls String text, char mnemonicChar) {
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
   * Parses a text in text-with-mnemonic format.
   * A mnemonic is prepended either with '_', or with '&' or with '\x1B' character.
   * To escape '_' or '&' before the actual mnemonic the character must be duplicated.
   * The characters after the actual mnemonic should not be escaped. 
   * E.g. "A__b_c__d" in text-with-mnemonic format will be displayed as "A_bc__d" with mnemonic 'c'.
   * 
   * @param text text to parse
   * @return TextWithMnemonic object which corresponds to the parsed text.
   */
  @NotNull
  @Contract(pure = true)
  public static TextWithMnemonic parse(@NotNull @Nls String text) {
    if (text.indexOf(UIUtil.MNEMONIC) >= 0) {
      text = text.replace(UIUtil.MNEMONIC, '&');
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
      String plain = plainText.toString();
      int length = plain.length() - getEllipsisLength(plain);
      if (length > 3 && mnemonicIndex == length - 2 && plain.charAt(length - 1) == ')' && plain.charAt(length - 3) == '(') {
        int pos = length - 3;
        while (pos > 0 && plain.charAt(pos - 1) == ' ') {
          pos--;
        }
        return new TextWithMnemonic(plain.substring(0, pos), mnemonicIndex, plain.substring(pos));
      }
      return new TextWithMnemonic(plain, mnemonicIndex, "");
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
    return myMnemonicIndex == mnemonic.myMnemonicIndex &&
           myText.equals(mnemonic.myText) &&
           myMnemonicSuffix.equals(mnemonic.myMnemonicSuffix);
  }

  @Override
  public int hashCode() {
    return (myText.hashCode() * 31 + myMnemonicIndex) * 31 + myMnemonicSuffix.hashCode();
  }

  /**
   * @return text in text-with-mnemonic format. Parsing back this text using {@link #parse(String)} method would create
   * a TextWithMnemonic object which is equal to this.
   */
  @Nls
  @Override
  public String toString() {
    if (myMnemonicIndex > -1) {
      String completeText = myText + myMnemonicSuffix;
      String prefix = StringUtil.escapeMnemonics(completeText.substring(0, myMnemonicIndex));
      String suffix = completeText.substring(myMnemonicIndex);
      return prefix + "_" + suffix;
    }
    return StringUtil.escapeMnemonics(myText);
  }
}
