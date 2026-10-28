// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.colors.FontPreferences
import com.intellij.util.text.CharArrayIterator
import com.intellij.util.text.CharSequenceIterator
import com.intellij.util.ui.JdkConstants.FontStyle
import java.awt.Font
import java.awt.font.FontRenderContext
import java.text.BreakIterator
import java.text.CharacterIterator

private const val COMPLEX_CHAR_START: Char = '\u0300' // start of Combining Diacritical Marks block

internal class FontFallbackIteratorImpl private constructor(
  private val fontPreferences: FontPreferences,
  private val fontRenderContext: FontRenderContext?,
  @FontStyle private val fontStyle: Int,
  private val trivialBreaker: BreakAtEveryCharacterIterator,
) {
  private var textAsArray: CharArray? = null
  private var textAsCharSequence: CharSequence? = null
  private var breakIterator: BreakIterator = trivialBreaker
  private var start: Int = 0
  private var end: Int = 0
  private var fontInfo: FontInfo? = null
  private var nextFontInfo: FontInfo? = null

  constructor(
    preferences: FontPreferences,
    context: FontRenderContext?,
    @FontStyle style: Int,
  ) : this(
    preferences,
    context,
    style,
    BreakAtEveryCharacterIterator(),
  )

  fun withFontPreferences(preferences: FontPreferences): FontFallbackIteratorImpl {
    if (fontPreferences === preferences) {
      return this
    }
    return copyWith(preferences, fontRenderContext, fontStyle)
  }

  fun withFontRenderContext(context: FontRenderContext?): FontFallbackIteratorImpl {
    if (fontRenderContext === context) {
      return this
    }
    return copyWith(fontPreferences, context, fontStyle)
  }

  fun withFontStyle(@FontStyle style: Int): FontFallbackIteratorImpl {
    if (fontStyle == style) {
      return this
    }
    return copyWith(fontPreferences, fontRenderContext, style)
  }

  fun start(text: CharSequence, rangeStart: Int, rangeEnd: Int) {
    assertRange(text.length, rangeStart, rangeEnd)
    var characterIterator: CharacterIterator? = null
    for (i in rangeStart until rangeEnd) {
      if (text[i] >= COMPLEX_CHAR_START) {
        characterIterator = CharSequenceIterator(text, rangeStart, rangeEnd)
        break
      }
    }
    doStart(text, null, characterIterator, rangeStart, rangeEnd)
  }

  fun start(text: CharArray, rangeStart: Int, rangeEnd: Int) {
    assertRange(text.size, rangeStart, rangeEnd)
    var characterIterator: CharacterIterator? = null
    for (i in rangeStart until rangeEnd) {
      if (text[i] >= COMPLEX_CHAR_START) {
        characterIterator = CharArrayIterator(text, rangeStart, rangeEnd)
        break
      }
    }
    doStart(null, text, characterIterator, rangeStart, rangeEnd)
  }

  fun atEnd(): Boolean {
    return start == end
  }

  fun advance() {
    start = end
    end = breakIterator.current()
    fontInfo = nextFontInfo
    while (true) {
      val breakEnd = breakIterator.next()
      if (breakEnd == BreakIterator.DONE) {
        return
      }
      if (isFormatChar(end, breakEnd) && fontInfo != null) {
        nextFontInfo = fontInfo
      } else {
        nextFontInfo = getFontAbleToDisplay(end, breakEnd)
      }
      if (fontInfo == null) {
        fontInfo = nextFontInfo
      }
      if (nextFontInfo != fontInfo) {
        return
      }
      end = breakEnd
    }
  }

  fun getStart(): Int {
    return start
  }

  fun getEnd(): Int {
    return end
  }

  fun getFontInfo(): FontInfo {
    checkNotNull(fontRenderContext) {
      "FontRenderContext must be set to generate FontInfo"
    }
    return currentFontInfo()
  }

  fun getFont(): Font {
    return currentFontInfo().font
  }

  private fun currentFontInfo(): FontInfo {
    return checkNotNull(fontInfo) {
      "The iterator has no current range"
    }
  }

  private fun copyWith(
    preferences: FontPreferences,
    context: FontRenderContext?,
    @FontStyle style: Int,
  ): FontFallbackIteratorImpl {
    val copy = FontFallbackIteratorImpl(preferences, context, style, trivialBreaker)
    copy.textAsArray = textAsArray
    copy.textAsCharSequence = textAsCharSequence
    copy.breakIterator = breakIterator
    copy.start = start
    copy.end = end
    copy.fontInfo = fontInfo
    copy.nextFontInfo = nextFontInfo
    return copy
  }

  private fun assertRange(textLength: Int, rangeStart: Int, rangeEnd: Int) {
    assert(rangeStart in 0..rangeEnd && rangeEnd <= textLength) {
      "Text length: $textLength, start: $rangeStart, end: $rangeEnd"
    }
  }

  private fun doStart(
    textAsCharSequence: CharSequence?,
    textAsArray: CharArray?,
    characterIterator: CharacterIterator?,
    rangeStart: Int,
    rangeEnd: Int,
  ) {
    this.textAsCharSequence = textAsCharSequence
    this.textAsArray = textAsArray
    if (characterIterator == null) {
      trivialBreaker.setRange(rangeStart, rangeEnd)
      breakIterator = trivialBreaker
    } else {
      val iterator = BreakIterator.getCharacterInstance() // locale-dependent
      iterator.text = characterIterator
      breakIterator = iterator
    }
    start = breakIterator.first()
    end = start
    assert(start == rangeStart)
    fontInfo = null
    nextFontInfo = null
    advance()
  }

  /**
   * We make format chars stick to the last font
   * See JBR FontRunIterator#isSameRun
   */
  private fun isFormatChar(breakStart: Int, breakEnd: Int): Boolean {
    if (breakEnd - breakStart != 1) {
      return false
    }
    val charCode = charAt(breakStart).code
    // From CMap#getFormatCharGlyph
    return charCode in 0x200c..0x200f ||
           charCode in 0x2028..0x202e ||
           charCode in 0x206a..0x206f
  }

  private fun charAt(index: Int): Char {
    val chars = textAsCharSequence
    if (chars == null) {
      return textAsArray!![index]
    } else {
      return chars[index]
    }
  }

  private fun getFontAbleToDisplay(breakStart: Int, breakEnd: Int): FontInfo {
    val chars = textAsCharSequence
    return if (chars == null) {
      ComplementaryFontsRegistry.getFontAbleToDisplay(
        textAsArray!!,
        breakStart,
        breakEnd,
        fontStyle,
        fontPreferences,
        fontRenderContext,
      )
    } else {
      ComplementaryFontsRegistry.getFontAbleToDisplay(
        chars,
        breakStart,
        breakEnd,
        fontStyle,
        fontPreferences,
        fontRenderContext,
      )
    }
  }
}
