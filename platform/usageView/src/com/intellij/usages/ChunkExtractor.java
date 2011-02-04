/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.usages;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.usageView.UsageTreeColors;
import com.intellij.usageView.UsageTreeColorsScheme;
import com.intellij.util.Processor;
import com.intellij.util.containers.FactoryMap;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author peter
 */
public class ChunkExtractor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.usages.ChunkExtractor");
  public static final int MAX_LINE_TO_SHOW = 140;
  public static final int OFFSET_BEFORE_TO_SHOW_WHEN_LONG_LINE = MAX_LINE_TO_SHOW / 2;
  public static final int OFFSET_AFTER_TO_SHOW_WHEN_LONG_LINE = MAX_LINE_TO_SHOW / 2;

  private final EditorColorsScheme myColorsScheme;

  private final Document myDocument;
  private final SyntaxHighlighter myHighlighter;

  private final Lexer myLexer;

  private abstract static class WeakFactory<T> {
    private WeakReference<T> myRef;

    @NotNull
    protected abstract T create();

    @NotNull
    public T getValue() {
      final T cur = myRef == null ? null : myRef.get();
      if (cur != null) return cur;
      final T result = create();
      myRef = new WeakReference<T>(result);
      return result;
    }
  }

  private static final ThreadLocal<WeakFactory<Map<PsiFile, ChunkExtractor>>> ourExtractors = new ThreadLocal<WeakFactory<Map<PsiFile, ChunkExtractor>>>() {
    @Override
    protected WeakFactory<Map<PsiFile, ChunkExtractor>> initialValue() {
      return new WeakFactory<Map<PsiFile, ChunkExtractor>>() {
        @NotNull
        @Override
        protected Map<PsiFile, ChunkExtractor> create() {
          return new FactoryMap<PsiFile, ChunkExtractor>() {
            @Override
            protected ChunkExtractor create(PsiFile key) {
              return new ChunkExtractor(key);
            }
          };
        }
      };
    }
  };

  public static TextChunk[] extractChunks(PsiFile file, UsageInfo2UsageAdapter usageAdapter) {
    return ourExtractors.get().getValue().get(file).extractChunks(usageAdapter);
  }


  private ChunkExtractor(@NotNull PsiFile file) {
    myColorsScheme = UsageTreeColorsScheme.getInstance().getScheme();

    myDocument = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
    LOG.assertTrue(myDocument != null);
    final FileType fileType = file.getFileType();
    final SyntaxHighlighter highlighter = SyntaxHighlighter.PROVIDER.create(fileType, file.getProject(), file.getVirtualFile());
    myHighlighter = highlighter == null ? new PlainSyntaxHighlighter() : highlighter;
    myLexer = myHighlighter.getHighlightingLexer();
    myLexer.start(myDocument.getCharsSequence());
  }

  public static int getStartOffset(final List<RangeMarker> rangeMarkers) {
    LOG.assertTrue(!rangeMarkers.isEmpty());
    int minStart = Integer.MAX_VALUE;
    for (RangeMarker rangeMarker : rangeMarkers) {
      if (!rangeMarker.isValid()) continue;
      final int startOffset = rangeMarker.getStartOffset();
      if (startOffset < minStart) minStart = startOffset;
    }
    return minStart == Integer.MAX_VALUE ? -1 : minStart;
  }

  private TextChunk[] extractChunks(UsageInfo2UsageAdapter usageInfo2UsageAdapter) {
    int absoluteStartOffset = usageInfo2UsageAdapter.getNavigationOffset();
    assert absoluteStartOffset != -1;

    final int lineNumber = myDocument.getLineNumber(absoluteStartOffset);
    final int columnNumber = absoluteStartOffset - myDocument.getLineStartOffset(lineNumber);

    int lineStartOffset = myDocument.getLineStartOffset(lineNumber);
    int lineEndOffset = lineStartOffset < myDocument.getTextLength() ? myDocument.getLineEndOffset(lineNumber) : 0;
    if (lineStartOffset > lineEndOffset) return TextChunk.EMPTY_ARRAY;

    final CharSequence chars = myDocument.getCharsSequence();
    if (myLexer.getTokenStart() > absoluteStartOffset) {
      myLexer.start(chars);
    }
    final List<TextChunk> result = new ArrayList<TextChunk>();
    appendPrefix(result, lineNumber, columnNumber);
    if (lineEndOffset - lineStartOffset > MAX_LINE_TO_SHOW) {
      lineStartOffset = Math.max(lineStartOffset, absoluteStartOffset - OFFSET_BEFORE_TO_SHOW_WHEN_LONG_LINE);
      lineEndOffset = Math.min(lineEndOffset, absoluteStartOffset + OFFSET_AFTER_TO_SHOW_WHEN_LONG_LINE);
    }
    return createTextChunks(usageInfo2UsageAdapter, chars, lineStartOffset, lineEndOffset, result);
  }

  private TextChunk[] createTextChunks(final UsageInfo2UsageAdapter usageInfo2UsageAdapter,
                                       final CharSequence chars,
                                       int start,
                                       int end,
                                       final List<TextChunk> result) {
    final Lexer lexer = myLexer;
    final SyntaxHighlighter highlighter = myHighlighter;

    LOG.assertTrue(start <= end);

    int i = StringUtil.indexOf(chars, '\n', start, end);
    if (i != -1) end = i;

    boolean isBeginning = true;

    while (lexer.getTokenType() != null) {
      try {
        int hiStart = lexer.getTokenStart();
        int hiEnd = lexer.getTokenEnd();

        if (hiStart >= end) break;

        hiStart = Math.max(hiStart, start);
        hiEnd = Math.min(hiEnd, end);
        if (hiStart >= hiEnd) { continue; }

        String text = chars.subSequence(hiStart, hiEnd).toString();
        if (isBeginning && text.trim().length() == 0) continue;
        isBeginning = false;
        IElementType tokenType = lexer.getTokenType();
        TextAttributesKey[] tokenHighlights = highlighter.getTokenHighlights(tokenType);

        processIntersectingRange(usageInfo2UsageAdapter, chars, hiStart, hiEnd, tokenHighlights, result);
      }
      finally {
        lexer.advance();
      }
    }

    return result.toArray(new TextChunk[result.size()]);
  }

  private void processIntersectingRange(UsageInfo2UsageAdapter usageInfo2UsageAdapter,
                                        final CharSequence chars,
                                        int hiStart,
                                        final int hiEnd,
                                        TextAttributesKey[] tokenHighlights,
                                        final List<TextChunk> result) {
    final TextAttributes originalAttrs = convertAttributes(tokenHighlights);
    final int[] lastOffset = {hiStart};
    usageInfo2UsageAdapter.processRangeMarkers(new Processor<Segment>() {
      @Override
      public boolean process(Segment segment) {
        int usageStart = segment.getStartOffset();
        int usageEnd = segment.getEndOffset();
        if (rangeIntersect(lastOffset[0], hiEnd, usageStart, usageEnd)) {
          addChunk(chars, lastOffset[0], Math.max(lastOffset[0], usageStart), originalAttrs, false, result);
          addChunk(chars, Math.max(lastOffset[0], usageStart), Math.min(hiEnd, usageEnd), originalAttrs, true, result);
          lastOffset[0] = usageEnd;
          if (usageEnd > hiEnd) {
            return false;
          }
        }
        return true;
      }
    });
    if (lastOffset[0] < hiEnd) {
      addChunk(chars, lastOffset[0], hiEnd, originalAttrs, false, result);
    }
  }

  private static void addChunk(CharSequence chars, int start, int end, TextAttributes originalAttrs, boolean bold, List<TextChunk> result) {
    if (start >= end) return;

    TextAttributes attrs = bold
                           ? TextAttributes.merge(originalAttrs, new TextAttributes(null, null, null, null, Font.BOLD))
                           : originalAttrs;
    result.add(new TextChunk(attrs, new String(chars.subSequence(start, end).toString())));
  }

  private static boolean rangeIntersect(int s1, int e1, int s2, int e2) {
    return s2 < s1 && s1 < e2 || s2 < e1 && e1 < e2
           || s1 < s2 && s2 < e1 || s1 < e2 && e2 < e1
           || s1 == s2 && e1 == e2;
  }

  private TextAttributes convertAttributes(TextAttributesKey[] keys) {
    TextAttributes attrs = myColorsScheme.getAttributes(HighlighterColors.TEXT);

    for (TextAttributesKey key : keys) {
      TextAttributes attrs2 = myColorsScheme.getAttributes(key);
      if (attrs2 != null) {
        attrs = TextAttributes.merge(attrs, attrs2);
      }
    }

    attrs = attrs.clone();
    attrs.setFontType(Font.PLAIN);
    return attrs;
  }

  private void appendPrefix(List<TextChunk> result, int lineNumber, int columnNumber) {
    StringBuilder buffer = new StringBuilder("(");
    buffer.append(lineNumber + 1);
    buffer.append(": ");
    buffer.append(columnNumber + 1);
    buffer.append(") ");
    TextChunk prefixChunk = new TextChunk(myColorsScheme.getAttributes(UsageTreeColors.USAGE_LOCATION), buffer.toString());
    result.add(prefixChunk);
  }
}
