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
private const val FIRST_PRINTABLE_ASCII: Char = ' '
private const val LAST_PRINTABLE_ASCII: Char = '~'

internal class FontFallbackIteratorImpl private constructor(
  private val fontLookup: FontLookup,
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
    RegistryFontLookup(preferences),
    context,
    style,
  )

  constructor(
    lookup: FontLookup,
    context: FontRenderContext?,
    @FontStyle style: Int,
  ) : this(
    lookup,
    context,
    style,
    BreakAtEveryCharacterIterator(),
  )

  fun withFontPreferences(preferences: FontPreferences): FontFallbackIteratorImpl {
    return withFontLookup(RegistryFontLookup(preferences))
  }

  fun withFontLookup(lookup: FontLookup): FontFallbackIteratorImpl {
    if (fontLookup == lookup) {
      return this
    }
    return copyWith(lookup, fontRenderContext, fontStyle)
  }

  fun withFontRenderContext(context: FontRenderContext?): FontFallbackIteratorImpl {
    if (fontRenderContext === context) {
      return this
    }
    return copyWith(fontLookup, context, fontStyle)
  }

  fun withFontStyle(@FontStyle style: Int): FontFallbackIteratorImpl {
    if (fontStyle == style) {
      return this
    }
    return copyWith(fontLookup, fontRenderContext, style)
  }

  fun start(text: CharSequence, rangeStart: Int, rangeEnd: Int) {
    assertRange(text.length, rangeStart, rangeEnd)
    var characterIterator: CharacterIterator? = null
    var printableAscii = true
    for (i in rangeStart until rangeEnd) {
      val c = text[i]
      if (c >= COMPLEX_CHAR_START) {
        characterIterator = CharSequenceIterator(text, rangeStart, rangeEnd)
        printableAscii = false
        break
      }
      if (c < FIRST_PRINTABLE_ASCII || c > LAST_PRINTABLE_ASCII) {
        printableAscii = false
      }
    }
    doStart(text, null, characterIterator, printableAscii, rangeStart, rangeEnd)
  }

  fun start(text: CharArray, rangeStart: Int, rangeEnd: Int) {
    assertRange(text.size, rangeStart, rangeEnd)
    var characterIterator: CharacterIterator? = null
    var printableAscii = true
    for (i in rangeStart until rangeEnd) {
      val c = text[i]
      if (c >= COMPLEX_CHAR_START) {
        characterIterator = CharArrayIterator(text, rangeStart, rangeEnd)
        printableAscii = false
        break
      }
      if (c < FIRST_PRINTABLE_ASCII || c > LAST_PRINTABLE_ASCII) {
        printableAscii = false
      }
    }
    doStart(null, text, characterIterator, printableAscii, rangeStart, rangeEnd)
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
    lookup: FontLookup,
    context: FontRenderContext?,
    @FontStyle style: Int,
  ): FontFallbackIteratorImpl {
    val copy = FontFallbackIteratorImpl(lookup, context, style, trivialBreaker)
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
    printableAscii: Boolean,
    rangeStart: Int,
    rangeEnd: Int,
  ) {
    this.textAsCharSequence = textAsCharSequence
    this.textAsArray = textAsArray
    if (printableAscii && rangeStart < rangeEnd) {
      val font = fontLookup.printableAsciiFont(fontStyle, fontRenderContext)
      if (font != null) {
        startOneRange(rangeStart, rangeEnd, font)
        return
      }
    }
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
   * Makes the range from [rangeStart] to [rangeEnd] the current range with [font]. The next [advance] ends the iteration.
   */
  private fun startOneRange(rangeStart: Int, rangeEnd: Int, font: FontInfo) {
    trivialBreaker.setRange(rangeEnd, rangeEnd)
    breakIterator = trivialBreaker
    start = rangeStart
    end = trivialBreaker.first()
    fontInfo = font
    nextFontInfo = font
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
    val chars = textAsArray
    if (chars != null) {
      return chars[index]
    } else {
      return textAsCharSequence!![index]
    }
  }

  private fun getFontAbleToDisplay(breakStart: Int, breakEnd: Int): FontInfo {
    val chars = textAsArray
    return if (chars != null) {
      fontLookup.fontAbleToDisplay(chars, breakStart, breakEnd, fontStyle, fontRenderContext)
    } else {
      fontLookup.fontAbleToDisplay(textAsCharSequence!!, breakStart, breakEnd, fontStyle, fontRenderContext)
    }
  }
}
