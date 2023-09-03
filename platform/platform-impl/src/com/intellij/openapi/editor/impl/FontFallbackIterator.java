// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl;
import com.intellij.util.text.CharArrayIterator;
import com.intellij.util.text.CharSequenceIterator;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.text.BreakIterator;
import java.text.CharacterIterator;

/**
 * This iterator finds font(s) that can display a given text. 
 * Text's characters are grouped into ranges that can be displayed using the same font.
 * <p>
 * Instances can be reused for different texts or preferred fonts - 
 * each {@code start} method invocation initiates a new iteration. 
 * All required font-related properties for a given iteration should be set before corresponding
 * {@code start} method invocation.
 * <p>
 * Sample usage scenario:
 * <code><pre>
 *   char[] text = getSourceText();
 *   FontFallbackIterator it = new FontFallbackIterator();
 *   it.start(text, 0, text.length);
 *   while (!it.atEnd()) {
 *     processFragment(new String(text, it.getStart(), it.getEnd()), it.getFont()));
 *     it.advance();
 *   }
 * </pre></code>
 */
public final class FontFallbackIterator {
  private static final char COMPLEX_CHAR_START = 0x300; // start of Combining Diacritical Marks block
  
  private final BreakAtEveryCharacterIterator myTrivialBreaker = new BreakAtEveryCharacterIterator();

  private FontPreferences myFontPreferences = new FontPreferencesImpl();
  private int myFontStyle = Font.PLAIN;
  private FontRenderContext myFontRenderContext;

  private char[] myTextAsArray;
  private CharSequence myTextAsCharSequence;
  private BreakIterator myIterator;
  private int myStart;
  private int myEnd;
  private FontInfo myFontInfo;
  private FontInfo myNextFontInfo;

  public FontFallbackIterator setPreferredFonts(@NotNull FontPreferences fontPreferences) {
    myFontPreferences = fontPreferences;
    return this;
  }

  public FontFallbackIterator setPreferredFont(@NotNull String familyName, int size) {
    FontPreferencesImpl preferences = new FontPreferencesImpl();
    preferences.register(familyName, size);
    myFontPreferences = preferences;
    return this;
  }

  public FontFallbackIterator setFontStyle(@JdkConstants.FontStyle int fontStyle) {
    myFontStyle = fontStyle;
    return this;
  }

  public FontFallbackIterator setFontRenderContext(@Nullable FontRenderContext fontRenderContext) {
    myFontRenderContext = fontRenderContext;
    return this;
  }

  public void start(@NotNull CharSequence text, int start, int end) {
    assert 0 <= start && start <= end && end <= text.length() : "Text length: " + text.length() + ", start: " + start + ", end: " + end;
    CharacterIterator characterIterator = null;
    for (int i = start; i < end; i++) {
      if (text.charAt(i) >= COMPLEX_CHAR_START) {
        characterIterator = new CharSequenceIterator(text, start, end);
        break;
      }
    }
    doStart(text, null, characterIterator, start, end);
  }

  public void start(char @NotNull [] text, int start, int end) {
    assert 0 <= start && start <= end && end <= text.length : "Text length: " + text.length + ", start: " + start + ", end: " + end;
    CharacterIterator characterIterator = null;
    for (int i = start; i < end; i++) {
      if (text[i] >= COMPLEX_CHAR_START) {
        characterIterator = new CharArrayIterator(text, start, end);
        break;
      }
    }
    doStart(null, text, characterIterator, start, end);
  }

  private void doStart(CharSequence textAsCharSequence, char[] textAsArray, CharacterIterator characterIterator, int start, int end) {
    myTextAsCharSequence = textAsCharSequence;
    myTextAsArray = textAsArray;
    if (characterIterator == null) {
      myTrivialBreaker.setRange(start, end);
      myIterator = myTrivialBreaker;
    }
    else {
      myIterator = BreakIterator.getCharacterInstance(); // locale-dependent
      myIterator.setText(characterIterator);
    }
    myStart = myEnd = myIterator.first();
    assert myStart == start;
    myFontInfo = myNextFontInfo = null;
    advance();
  }

  public boolean atEnd() {
    return myStart == myEnd;
  }

  public void advance() {
    myStart = myEnd;
    myEnd = myIterator.current();
    myFontInfo = myNextFontInfo;
    int end;
    while ((end = myIterator.next()) != BreakIterator.DONE) {
      if (isFormatChar(myEnd, end) && myFontInfo != null) myNextFontInfo = myFontInfo;
      else myNextFontInfo = getFontAbleToDisplay(myEnd, end);
      if (myFontInfo == null) myFontInfo = myNextFontInfo;
      if (myNextFontInfo.equals(myFontInfo)) {
        myEnd = end;
      }
      else {
        return;
      }
    }
  }

  /**
   * We make format chars stick to the last font
   * See JBR FontRunIterator#isSameRun
   */
  private boolean isFormatChar(int start, int end) {
    if (end - start == 1) {
      int charCode = myTextAsCharSequence == null ? myTextAsArray[start] : myTextAsCharSequence.charAt(start);
      // From CMap#getFormatCharGlyph
      if (charCode >= 0x200c) {
        if ((charCode <= 0x200f) ||
            (charCode >= 0x2028 && charCode <= 0x202e) ||
            (charCode >= 0x206a && charCode <= 0x206f)) {
          return true;
        }
      }
    }
    return false;
  }

  private FontInfo getFontAbleToDisplay(int start, int end) {
    if (myTextAsCharSequence == null) {
      return ComplementaryFontsRegistry.getFontAbleToDisplay(myTextAsArray, start, end, 
                                                             myFontStyle, myFontPreferences, myFontRenderContext);
    }
    else {
      return ComplementaryFontsRegistry.getFontAbleToDisplay(myTextAsCharSequence, start, end,
                                                             myFontStyle, myFontPreferences, myFontRenderContext);
    }
  }

  public int getStart() {
    return myStart;
  }
  
  public int getEnd() {
    return myEnd;
  }

  public @NotNull FontInfo getFontInfo() {
    if (myFontRenderContext == null) {
      throw new IllegalStateException("FontRenderContext must be set to generate FontInfo");
    }
    return myFontInfo;
  }
  
  public @NotNull Font getFont() {
    return myFontInfo.getFont();
  }

  private static final class BreakAtEveryCharacterIterator extends BreakIterator {
    private int myStart;
    private int myEnd;
    private int myCurrent;

    public void setRange(int start, int end) {
      myStart = start;
      myEnd = end;
    }

    @Override
    public int first() {
      return myCurrent = myStart;
    }

    @Override
    public int last() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int next(int n) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int next() {
      return myCurrent >= myEnd ? DONE : ++myCurrent;
    }

    @Override
    public int previous() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int following(int offset) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int current() {
      return myCurrent;
    }

    @Override
    public CharacterIterator getText() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setText(CharacterIterator newText) {
      throw new UnsupportedOperationException();
    }
  }
}
