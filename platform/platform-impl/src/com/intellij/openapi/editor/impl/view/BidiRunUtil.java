// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import com.intellij.lang.CodeDocumentationAwareCommenter;
import com.intellij.lang.Commenter;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageCommenters;
import com.intellij.openapi.editor.bidi.BidiRegionsSeparator;
import com.intellij.openapi.editor.bidi.LanguageBidiRegionsSeparator;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.psi.StringEscapesTokenTypes;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.Bidi;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class BidiRunUtil {
  private static final String WHITESPACE_CHARS = " \t";

  static List<LineBidiRun> createRuns(@NotNull EditorView view, char @NotNull [] text, int startOffsetInEditor) {
    int textLength = text.length;
    if (view.getEditor().myDisableRtl || !Bidi.requiresBidi(text, 0, textLength)) {
      return Collections.singletonList(new LineBidiRun(textLength));
    }
    return createRunsBidi(view, text, startOffsetInEditor, textLength);
  }

  private static @NotNull List<LineBidiRun> createRunsBidi(
    @NotNull EditorView view,
    char[] text,
    int startOffsetInEditor,
    int textLength
  ) {
    view.getEditor().bidiTextFound();

    List<LineBidiRun> runs = new ArrayList<>();
    int flags = view.getBidiFlags();
    if (startOffsetInEditor >= 0) {
      // skipping indent
      int relLastOffset = 0;
      while (relLastOffset < text.length && WHITESPACE_CHARS.indexOf(text[relLastOffset]) >= 0) {
        relLastOffset++;
      }
      addRuns(runs, text, 0, relLastOffset, flags);
      // running bidi algorithm separately for text fragments corresponding to different lexer tokens
      IElementType lastToken = null;
      HighlighterIterator iterator = view.getHighlighter().createIterator(startOffsetInEditor + relLastOffset);
      while (!iterator.atEnd() && iterator.getStart() - startOffsetInEditor < textLength) {
        int iteratorRelStart = alignToCodePointBoundary(text, iterator.getStart() - startOffsetInEditor);
        int iteratorRelEnd = alignToCodePointBoundary(text, iterator.getEnd() - startOffsetInEditor);
        int relStartOffset = Math.max(relLastOffset, iteratorRelStart);
        //noinspection MathClampMigration
        int relEndOffset = Math.min(textLength, Math.max(relStartOffset, iteratorRelEnd));
        IElementType currentToken = iterator.getTokenType();
        int[] boundaries = getCommentPrefixAndOrSuffixBoundaries(text, relStartOffset, relEndOffset, currentToken);
        if (boundaries != null) {
          // for comments, we process prefixes and suffixes separately from comment text
          addRuns(runs, text, relLastOffset, relStartOffset, flags);
          addRuns(runs, text, relStartOffset, boundaries[0], flags);
          addRuns(runs, text, boundaries[0], boundaries[1], flags);
          lastToken = null;
          relLastOffset = boundaries[1];
        } else if (distinctTokens(lastToken, currentToken)) {
          addRuns(runs, text, relLastOffset, relStartOffset, flags);
          lastToken = currentToken;
          relLastOffset = relStartOffset;
        }
        iterator.advance();
      }
      addRuns(runs, text, relLastOffset, textLength, flags);
    } else {
      addRuns(runs, text, 0, textLength, flags);
    }
    for (LineBidiRun run : runs) {
      assert !isInsideSurrogatePair(text, run.getStartOffset());
      assert !isInsideSurrogatePair(text, run.getEndOffset());
    }
    return runs;
  }

  private static void addRuns(@NotNull List<LineBidiRun> runs, char[] text, int start, int end, int flags) {
    if (start < end && !Bidi.requiresBidi(text, start, end)) {
      addOrMergeRun(runs, new LineBidiRun(start, end, (byte) 0, null));
      return;
    }
    int afterLastTabPosition = start;
    for (int i = start; i < end; i++) {
      if (text[i] == '\t') {
        addRunsNoTabs(runs, text, afterLastTabPosition, i, flags);
        afterLastTabPosition = i + 1;
        addOrMergeRun(runs, new LineBidiRun(i, i + 1, (byte) 0, null));
      }
    }
    addRunsNoTabs(runs, text, afterLastTabPosition, end, flags);
  }

  private static void addRunsNoTabs(@NotNull List<LineBidiRun> runs, char[] text, int start, int end, int flags) {
    if (start >= end) {
      return;
    }
    Bidi bidi = new Bidi(text, start, null, 0, end - start, flags);
    int runCount = bidi.getRunCount();
    for (int i = 0; i < runCount; i++) {
      LineBidiRun run = new LineBidiRun(
        start + bidi.getRunStart(i),
        start + bidi.getRunLimit(i),
        (byte) bidi.getRunLevel(i),
        null
      );
      addOrMergeRun(runs, run);
    }
  }

  private static void addOrMergeRun(@NotNull List<LineBidiRun> runs, @NotNull LineBidiRun run) {
    int size = runs.size();
    if (size > 0 && runs.get(size - 1).getLevel() == 0 && run.getLevel() == 0) {
      LineBidiRun lastRun = runs.remove(size - 1);
      assert lastRun.getEndOffset() == run.getStartOffset();
      runs.add(new LineBidiRun(lastRun.getStartOffset(), run.getEndOffset(), (byte)0, null));
    } else {
      runs.add(run);
    }
  }

  private static int[] getCommentPrefixAndOrSuffixBoundaries(char[] text, int start, int end, @Nullable IElementType token) {
    if (token == null) {
      return null;
    }
    Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(token.getLanguage());
    if (!(commenter instanceof CodeDocumentationAwareCommenter cdaCommenter)) {
      return null;
    }
    if (token.equals(cdaCommenter.getLineCommentTokenType())) {
      String prefix = cdaCommenter.getLineCommentPrefix();
      if (prefix != null) {
        // some commenters (e.g. for Python) include space in comment prefix
        prefix = prefix.stripTrailing();
      }
      if (isValidSuffixOrPrefix(prefix) && CharArrayUtil.regionMatches(text, start, end, prefix)) {
        int start0 = Math.min(end, CharArrayUtil.shiftForward(text, start + prefix.length(), WHITESPACE_CHARS));
        return new int[]{start0, end};
      }
    } else if (token.equals(cdaCommenter.getBlockCommentTokenType())) {
      String prefix = cdaCommenter.getBlockCommentPrefix();
      String suffix = cdaCommenter.getBlockCommentSuffix();
      if (!isValidSuffixOrPrefix(prefix) || !isValidSuffixOrPrefix(suffix)) {
        return null;
      }
      int[] result = new int[]{start, end};
      boolean hasPrefixOrSuffix = false;
      if (CharArrayUtil.regionMatches(text, start, end, prefix)) {
        result[0] = start + prefix.length();
        hasPrefixOrSuffix = true;
      }
      if (CharArrayUtil.regionMatches(text, end - suffix.length(), end, suffix)) {
        result[1] = end - suffix.length();
        hasPrefixOrSuffix = true;
      }
      if (hasPrefixOrSuffix && result[0] < result[1]) {
        result[0] = Math.min(result[1], CharArrayUtil.shiftForward(text, result[0], WHITESPACE_CHARS));
        result[1] = Math.max(result[0], CharArrayUtil.shiftBackward(text, result[1] - 1, WHITESPACE_CHARS) + 1);
        return result;
      }
    }
    return null;
  }

  private static boolean distinctTokens(@Nullable IElementType token1, @Nullable IElementType token2) {
    if (token1 == token2) {
      return false;
    }
    if (token1 == null || token2 == null) {
      return true;
    }
    if (StringEscapesTokenTypes.STRING_LITERAL_ESCAPES.contains(token1) ||
        StringEscapesTokenTypes.STRING_LITERAL_ESCAPES.contains(token2)) {
      return false;
    }
    if (token1 != TokenType.WHITE_SPACE &&
        token2 != TokenType.WHITE_SPACE &&
        !token1.getLanguage().is(token2.getLanguage())) {
      return true;
    }
    Language language = token1.getLanguage();
    if (language == Language.ANY) {
      language = token2.getLanguage();
    }
    BidiRegionsSeparator separator = LanguageBidiRegionsSeparator.getInstance().forLanguage(language);
    return separator.createBorderBetweenTokens(token1, token2);
  }

  private static boolean isValidSuffixOrPrefix(String value) {
    return value != null &&
           !value.isEmpty() &&
           !Character.isLowSurrogate(value.charAt(0)) &&
           !Character.isHighSurrogate(value.charAt(value.length() - 1));
  }

  private static int alignToCodePointBoundary(char[] text, int offset) {
    return isInsideSurrogatePair(text, offset) ? offset - 1 : offset;
  }

  private static boolean isInsideSurrogatePair(char[] text, int offset) {
    return offset > 0 &&
           offset < text.length &&
           Character.isHighSurrogate(text[offset - 1]) &&
           Character.isLowSurrogate(text[offset]);
  }
}
