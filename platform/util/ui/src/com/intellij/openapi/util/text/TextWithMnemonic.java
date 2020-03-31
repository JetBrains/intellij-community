// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.text;

import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * An immutable object which represents a text string with mnemonic character.
 */
public final class TextWithMnemonic {
  @NotNull private final String myText;
  private final int myMnemonicIndex;

  private TextWithMnemonic(@NotNull String text, int mnemonicIndex) {
    myText = StringUtil.internEmptyString(text);
    myMnemonicIndex = mnemonicIndex;
  }

  /**
   * @return plain text without mnemonic
   */
  @NotNull
  public String getText() {
    return myText;
  }

  /**
   * @return a mnemonic character (upper-cased) if mnemonic is set; 0 otherwise
   */
  public int getMnemonic() {
    return hasMnemonic() ? Character.toUpperCase(myText.charAt(myMnemonicIndex)) : 0;
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
  public TextWithMnemonic dropMnemonic() {
    return hasMnemonic() ? fromPlainText(myText) : this;
  }

  /**
   * Sets mnemonic at given index
   * @param index index, must be within the {@link #getText() text} string.
   * @return a TextWithMnemonic object with mnemonic set at given index
   */
  public TextWithMnemonic setMnemonicAt(int index) {
    if (index < 0 || index >= myText.length()) {
      throw new IndexOutOfBoundsException(String.valueOf(index));
    }
    return index == myMnemonicIndex ? this : new TextWithMnemonic(myText, index);
  }

  /**
   * Appends given text to the current text.
   * 
   * @param textToAppend text to append. Appended text is treated as a plain text, without mnemonic, so mnemonic position is unchanged.
   * @return TextWithMnemonic object which text is the concatenation of this object text and supplied text.
   */
  public TextWithMnemonic append(@NotNull String textToAppend) {
    return new TextWithMnemonic(myText + textToAppend, myMnemonicIndex);
  }

  /**
   * Replaces the first occurrence of given target text with the given replacement text.
   * 
   * @param target the target text to be replaced
   * @param replacement the replacement text which is treated as a plain text, without mnemonic.
   * @return TextWithMnemonic object. The resulting mnemonic position could be adjusted if the mnemonic was located after the replacement.
   *          If the mnemonic was inside the target text then it's dropped. Returns this object if the target text was not found.
   */
  public TextWithMnemonic replaceFirst(@NotNull String target, @NotNull String replacement) {
    int index = myText.indexOf(target);
    if (index == -1) {
      return this;
    }
    String resultText = myText.substring(0, index) + replacement + myText.substring(index + target.length());
    int resultIndex = myMnemonicIndex < index ? myMnemonicIndex : 
                      myMnemonicIndex >= index + target.length() ? myMnemonicIndex - target.length() + replacement.length() :
                      -1;
    return new TextWithMnemonic(resultText, resultIndex);
  }

  /**
   * Creates a TextWithMnemonic object from a plain text without mnemonic.
   * @param text a plain text to create a TextWithMnemonic object from 
   * @return new TextWithMnemonic object which has no mnemonic
   */
  @NotNull
  @Contract(pure = true)
  public static TextWithMnemonic fromPlainText(@NotNull String text) {
    return new TextWithMnemonic(text, -1);
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
  public static TextWithMnemonic parse(@NotNull String text) {
    if (text.indexOf(UIUtil.MNEMONIC) >= 0) {
      text = text.replace(UIUtil.MNEMONIC, '&');
    }

    if (text.contains("_") || text.contains("&")) {
      StringBuilder plainText = new StringBuilder();
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
      return new TextWithMnemonic(plainText.toString(), mnemonicIndex);
    }
    return fromPlainText(text);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TextWithMnemonic mnemonic = (TextWithMnemonic)o;
    return myMnemonicIndex == mnemonic.myMnemonicIndex &&
           myText.equals(mnemonic.myText);
  }

  @Override
  public int hashCode() {
    return myText.hashCode() * 31 + myMnemonicIndex;
  }

  /**
   * @return text in text-with-mnemonic format. Parsing back this text using {@link #parse(String)} method would create
   * a TextWithMnemonic object which is equal to this.
   */
  @Override
  public String toString() {
    if (myMnemonicIndex > -1) {
      String prefix = StringUtil.escapeMnemonics(myText.substring(0, myMnemonicIndex));
      String suffix = myText.substring(myMnemonicIndex);
      return prefix + "_" + suffix;
    }
    return StringUtil.escapeMnemonics(myText);
  }
}
