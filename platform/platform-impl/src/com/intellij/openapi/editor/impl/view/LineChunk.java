// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.ex.DocumentText;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.FontFallbackIterator;
import com.intellij.openapi.editor.impl.FontInfo;
import com.intellij.openapi.editor.markup.FontFamilies;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.util.BitUtil;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.ui.JdkConstants.FontStyle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class LineChunk {
  private static final Logger LOG = Logger.getInstance(LineChunk.class);

  private List<LineFragment> fragments; // in logical order
  private final int startOffset;
  private final int endOffset;

  /**
   * The largest ascent of the fonts of the font families in this chunk, see {@link TextFonts#ascent()}, or -1 before the first layout.
   */
  private int fontFamiliesAscent = -1;

  /**
   * The largest descent of the fonts of the font families in this chunk, see {@link TextFonts#descent()}, or -1 before the first layout.
   */
  private int fontFamiliesDescent = -1;

  LineChunk(int startOffset, int endOffset) {
    this(startOffset, endOffset, null);
  }

  LineChunk(int startOffset, int endOffset, @Nullable List<LineFragment> fragments) {
    this.startOffset = startOffset;
    this.endOffset = endOffset;
    this.fragments = fragments;
  }

  int getStartOffset() {
    return startOffset;
  }

  int getEndOffset() {
    return endOffset;
  }

  int fragmentCount() {
    return fragments.size();
  }

  LineFragment getFragment(int index) {
    return fragments.get(index);
  }

  void clearFragments() {
    fragments = null;
  }

  /**
   * Returns the largest ascent of the fonts of the font families in this chunk, or -1 before its first layout.
   * The editor fonts count as 0. The value stays after {@link #clearFragments()}, and each layout writes it again.
   */
  int getFontFamiliesAscent() {
    return fontFamiliesAscent;
  }

  /**
   * Returns the largest descent of the fonts of the font families in this chunk, or -1 before its first layout.
   * The editor fonts count as 0. The value stays after {@link #clearFragments()}, and each layout writes it again.
   */
  int getFontFamiliesDescent() {
    return fontFamiliesDescent;
  }

  boolean isReal() {
    return true;
  }

  void addFragments(
    char[] chars,
    byte level,
    @NotNull FontFallbackIterator ffi,
    @NotNull EditorView view
  ) {
    fragments = new ArrayList<>();
    addFragments(chars, getStartOffset(), getEndOffset(), level, null, null, ffi, view);
  }

  void ensureLayout(@NotNull EditorView view, @NotNull DocumentText document, int line, byte level) {
    if (isReal()) {
      view.getTextLayoutCache().onChunkAccess(this);
    }
    if (fragments != null) {
      return;
    }
    assert isReal();
    fragments = new ArrayList<>();
    EditorImpl editor = view.getEditor();
    int lineStartOffset = document.lineStartOffset(line);
    int start = lineStartOffset + getStartOffset();
    int end = lineStartOffset + getEndOffset();
    if (LOG.isDebugEnabled()) {
      LOG.debug("Text layout for " + editor.getVirtualFile() + " (" + start + "-" + end + ")");
    }
    IterationState it = new IterationState(
      editor,
      start,
      end,
      null,
      false,
      true,
      false,
      false
    );
    EditorFontResolver fontResolver = view.getFontResolver();
    FontFallbackIterator ffi = new FontFallbackIterator(
      fontResolver.editorFonts(),
      fontResolver.fontRenderContext(),
      Font.PLAIN
    );
    Font specialCharacterFont = fontResolver.specialCharacterFont();
    char[] chars = CharArrayUtil.fromSequence(document.chars(), start, end);
    @FontStyle int currentFontType = Font.PLAIN;
    Color currentColor = null;
    TextFonts currentFonts = fontResolver.editorFonts();
    FontFamilies lastFontFamilies = null;
    TextFonts lastFonts = currentFonts;
    int maxAscent = 0;
    int maxDescent = 0;
    int currentStart = start;
    while (!it.atEnd()) {
      TextAttributes attributes = it.getMergedAttributes();
      @FontStyle int fontType = attributes.getFontType();
      Color color = attributes.getForegroundColor();
      FontFamilies fontFamilies = attributes.getFontFamilies();
      if (fontFamilies != lastFontFamilies) {
        lastFonts = fontsOf(fontFamilies, fontResolver, editor, start, end);
        lastFontFamilies = fontFamilies;
        maxAscent = Math.max(maxAscent, lastFonts.ascent());
        maxDescent = Math.max(maxDescent, lastFonts.descent());
      }
      if (fontType != currentFontType || !color.equals(currentColor) || lastFonts != currentFonts) {
        int tokenStart = it.getStartOffset();
        if (tokenStart > currentStart) {
          addFragments(
            chars,
            currentStart - start,
            tokenStart - start,
            level,
            specialCharacterFont,
            view.getTabFragment(),
            ffi,
            view
          );
        }
        currentStart = tokenStart;
        currentColor = color;
        currentFontType = fontType;
        currentFonts = lastFonts;
        ffi.setFontStyle(currentFontType);
        ffi.setFontLookup(currentFonts);
      }
      it.advance();
    }
    if (end > currentStart) {
      addFragments(
        chars,
        currentStart - start,
        end - start,
        level,
        specialCharacterFont,
        view.getTabFragment(),
        ffi,
        view
      );
    }
    fontFamiliesAscent = maxAscent;
    fontFamiliesDescent = maxDescent;
    view.getSizeManager().textLayoutPerformed(start, end);
    assert !fragments.isEmpty();
  }

  @NotNull LineChunk subChunk(
    @NotNull EditorView view,
    @NotNull DocumentText document,
    int line,
    int targetStartOffset,
    int targetEndOffset,
    byte level,
    @Nullable Runnable quickEvaluationListener
  ) {
    assert isReal();
    assert targetStartOffset < getEndOffset();
    assert targetEndOffset > getStartOffset();
    int start = Math.max(getStartOffset(), targetStartOffset);
    int end = Math.min(getEndOffset(), targetEndOffset);
    if (quickEvaluationListener != null && fragments == null) {
      quickEvaluationListener.run();
      int startColumn = view.offsetToLogicalColumn(line, start);
      int endColumn = view.offsetToLogicalColumn(line, end);
      var approximationFragment = new ApproximationFragment(end - start, endColumn - startColumn, view.getMaxCharWidth());
      return new SyntheticLineChunk(start, end, Collections.singletonList(approximationFragment));
    }
    if (start == getStartOffset() && end == getEndOffset()) {
      return this;
    }
    ensureLayout(view, document, line, level);
    LineChunk chunk0 = new SyntheticLineChunk(start, end, new ArrayList<>());
    int offset = getStartOffset();
    for (LineFragment fragment : fragments) {
      if (end <= offset) {
        break;
      }
      int endOffset = offset + fragment.getLength();
      if (start < endOffset) {
        int subStart = Math.max(start, offset) - offset;
        int subEnd = Math.min(end, endOffset) - offset;
        LineFragment subFragment = fragment.subFragment(subStart, subEnd);
        chunk0.fragments.add(subFragment);
      }
      offset = endOffset;
    }
    return chunk0;
  }

  /**
   * Returns the fonts of the font families. An unresolved value gets the editor fonts, and the editor resolves it later.
   * The chunk from {@code start} to {@code end} is the text that the editor lays out again then.
   */
  private static TextFonts fontsOf(
    @Nullable FontFamilies fontFamilies,
    EditorFontResolver fontResolver,
    EditorImpl editor,
    int start,
    int end
  ) {
    if (fontFamilies == null) {
      return fontResolver.editorFonts();
    }
    TextFonts fonts = fontResolver.resolvedFonts(fontFamilies);
    if (fonts == null) {
      editor.recordUnresolvedFontFamilies(fontFamilies, start, end);
      return fontResolver.editorFonts();
    }
    return fonts;
  }

  private void addFragments(
    char[] text,
    int start,
    int end,
    byte level,
    @Nullable Font specialCharacterFont,
    @Nullable TabFragment tabFragment,
    @NotNull FontFallbackIterator ffi,
    @NotNull EditorView view
  ) {
    EditorViewSnapshot viewSnapshot = null;
    assert start < end;
    boolean isRtl = BitUtil.isSet(level, 1);
    int last = start;
    for (int i = start; i < end; i++) {
      char c = text[i];
      LineFragment specialFragment = null;
      if (c == '\t') {
        assert level == 0;
        specialFragment = tabFragment;
      } else if (specialCharacterFont != null) {
        if (viewSnapshot == null) {
          viewSnapshot = view.getSnapshot();
        }
        // only BMP special chars are supported currently, so there's no need to check for surrogate pairs
        specialFragment = SpecialCharacterFragment.create(viewSnapshot, specialCharacterFont, c, text, i);
      }
      if (specialFragment != null) {
        addFragmentsNoTabs(text, last, i, isRtl, ffi, view);
        fragments.add(specialFragment);
        last = i + 1;
      }
    }
    addFragmentsNoTabs(text, last, end, isRtl, ffi, view);
    assert fragmentCount() > 0;
  }

  private void addFragmentsNoTabs(
    char[] text,
    int start,
    int end,
    boolean isRtl,
    FontFallbackIterator ffi,
    EditorView view
  ) {
    if (start >= end) {
      return;
    }
    for (ffi.start(text, start, end); !ffi.atEnd(); ffi.advance()) {
      int start0 = ffi.getStart();
      int end0 = ffi.getEnd();
      FontInfo fontInfo = ffi.getFontInfo();
      if (start0 < end0) {
        assert fontInfo != null;
        createTextFragments(text, start0, end0, isRtl, fontInfo, view);
      }
    }
  }

  private void createTextFragments(
    char[] lineChars,
    int start,
    int end,
    boolean isRtl,
    FontInfo fontInfo,
    EditorView view
  ) {
    boolean needsLayout = isRtl || fontInfo.getFont().hasLayoutAttributes();
    boolean nonLatinText = false;
    if (!needsLayout) {
      if (containsSurrogatePairs(lineChars, start, end - 1) || // no need to check last character for high surrogate
          Font.textRequiresLayout(lineChars, start, end)) {
        needsLayout = true;
        nonLatinText = true;
      }
    }
    if (!needsLayout) {
      LineFragment fragment = new SimpleTextFragment(lineChars, start, end, fontInfo, view);
      fragments.add(fragment);
      return;
    }
    int lastOffset = start;
    if (nonLatinText || containsNonLatinText(lineChars, start, end)) {
      // Split text by scripts. JDK does this as well inside 'Font.layoutGlyphVector',
      // but doing it here effectively disables brace matching logic in 'layoutGlyphVector',
      // which breaks ligatures in some cases (see JBR-10).
      Character.UnicodeScript lastScript = Character.UnicodeScript.COMMON;
      for (int i = start; i < end; i++) {
        int c = Character.codePointAt(lineChars, i, end);
        if (Character.isSupplementaryCodePoint(c)) {
          //noinspection AssignmentToForLoopParameter
          i++;
        }
        Character.UnicodeScript script = Character.UnicodeScript.of(c);
        if (script != Character.UnicodeScript.COMMON &&
            script != Character.UnicodeScript.INHERITED &&
            script != Character.UnicodeScript.UNKNOWN) {
          if (lastScript != script && lastScript != Character.UnicodeScript.COMMON) {
            LineFragment fragment = new ComplexTextFragment(lineChars, lastOffset, i, isRtl, fontInfo, view);
            fragments.add(fragment);
            lastOffset = i;
          }
          lastScript = script;
        }
      }
    }
    LineFragment fragment = new ComplexTextFragment(lineChars, lastOffset, end, isRtl, fontInfo, view);
    fragments.add(fragment);
  }

  private static boolean containsSurrogatePairs(char[] chars, int start, int end) {
    for (int i = start; i < end; i++) {
      if (Character.isHighSurrogate(chars[i]) &&
          Character.isLowSurrogate(chars[i + 1])) {
        return true;
      }
    }
    return false;
  }

  private static boolean containsNonLatinText(char[] chars, int start, int end) {
    for (int i = start; i < end; i++) {
      if (chars[i] >= 0x2ea /* first non-Latin code point */) {
        return true;
      }
    }
    return false;
  }
}
