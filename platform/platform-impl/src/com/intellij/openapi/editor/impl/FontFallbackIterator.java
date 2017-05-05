/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl;
import com.intellij.util.text.CharArrayIterator;
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
 * each {@link #start(char[], int, int)} invocation initiates a new iteration. 
 * All required font-related properties for a given iteration should be set before correspongin 
 * {@link #start(char[], int, int)} invocation.
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
public class FontFallbackIterator {
  private final BreakAtEveryCharacterIterator myTrivialBreaker = new BreakAtEveryCharacterIterator();

  private FontPreferences myFontPreferences = new FontPreferencesImpl();
  private int myFontStyle = Font.PLAIN;
  private FontRenderContext myFontRenderContext;

  private char[] myText;
  private BreakIterator myIterator;
  private int myStart;
  private int myEnd;
  private FontInfo myFontInfo;
  private FontInfo myNextFontInfo;

  public FontFallbackIterator setPreferredFonts(@NotNull FontPreferences fontPreferences) {
    myFontPreferences = fontPreferences;
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

  public void start(@NotNull char[] text, int start, int end) {
    assert 0 <= start && start <= end && end <= text.length : "Text length: " + text.length + ", start: " + start + ", end: " + end;
    myText = text;
    myIterator = getBreakIterator(text, start, end);
    myStart = myEnd = myIterator.first();
    assert myStart == start;
    myFontInfo = myNextFontInfo = null;
    advance();
  }

  private BreakIterator getBreakIterator(char[] text, int start, int end) {
    for (int i = start; i < end; i++) {
      if (text[i] >= 0x300 /* start of Combining Diacritical Marks block */) {
        // text is complex - use standard Unicode algorithm
        BreakIterator breakIterator = BreakIterator.getCharacterInstance(); // locale-dependent
        breakIterator.setText(new CharArrayIterator(text, start, end));
        return breakIterator;
      }
    }
    // text is simple - can be treated char-by-char
    myTrivialBreaker.setRange(start, end);
    return myTrivialBreaker;
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
      myNextFontInfo = getFontAbleToDisplay(myEnd, end);
      if (myFontInfo == null) myFontInfo = myNextFontInfo;
      if (myNextFontInfo.equals(myFontInfo)) {
        myEnd = end;
      }
      else {
        return;
      }
    }
  }

  private FontInfo getFontAbleToDisplay(int start, int end) {
    return ComplementaryFontsRegistry.getFontAbleToDisplay(myText, start, end, myFontStyle, myFontPreferences, myFontRenderContext);
  }

  public int getStart() {
    return myStart;
  }
  
  public int getEnd() {
    return myEnd;
  }

  @NotNull
  public FontInfo getFontInfo() {
    if (myFontRenderContext == null) {
      throw new IllegalStateException("FontRenderContext must be set to generate FontInfo");
    }
    return myFontInfo;
  }
  
  @NotNull
  public Font getFont() {
    return myFontInfo.getFont();
  }

  private static class BreakAtEveryCharacterIterator extends BreakIterator {
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
