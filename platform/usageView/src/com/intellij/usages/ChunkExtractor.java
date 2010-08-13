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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.usageView.UsageTreeColors;
import com.intellij.usageView.UsageTreeColorsScheme;
import com.intellij.util.containers.FactoryMap;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author peter
 */
public class ChunkExtractor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.usages.ChunkExtractor");

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

  public static TextChunk[] extractChunks(PsiElement element, List<RangeMarker> rangeMarkers) {
    return ourExtractors.get().getValue().get(element.getContainingFile()).extractChunks(rangeMarkers);
  }


  private ChunkExtractor(PsiFile file) {
    myColorsScheme = UsageTreeColorsScheme.getInstance().getScheme();

    myDocument = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
    LOG.assertTrue(myDocument != null);
    final FileType fileType = file.getFileType();
    final SyntaxHighlighter highlighter =
      SyntaxHighlighter.PROVIDER.create(fileType, file.getProject(), file.getVirtualFile());
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

  public TextChunk[] extractChunks(List<RangeMarker> rangeMarkers) {
    final ArrayList<RangeMarker> markers = new ArrayList<RangeMarker>(rangeMarkers.size());
    for (RangeMarker rangeMarker : rangeMarkers) {
      if (rangeMarker.isValid()) markers.add(rangeMarker);
    }
    int absoluteStartOffset = getStartOffset(markers);
    assert absoluteStartOffset != -1;

    final int lineNumber = myDocument.getLineNumber(absoluteStartOffset);
    final int columnNumber = absoluteStartOffset - myDocument.getLineStartOffset(lineNumber);

    Collections.sort(markers, RangeMarker.BY_START_OFFSET);
    final int lineStartOffset = myDocument.getLineStartOffset(lineNumber);
    final int lineEndOffset = lineStartOffset < myDocument.getTextLength() ? myDocument.getLineEndOffset(lineNumber) : 0;
    if (lineStartOffset > lineEndOffset) return TextChunk.EMPTY_ARRAY;

    final CharSequence chars = myDocument.getCharsSequence();
    if (myLexer.getTokenStart() > absoluteStartOffset) {
      myLexer.start(chars);
    }
    final List<TextChunk> result = new ArrayList<TextChunk>();
    appendPrefix(result, lineNumber, columnNumber);
    return createTextChunks(markers, chars, lineStartOffset, lineEndOffset, result);
  }

  private TextChunk[] createTextChunks(final List<RangeMarker> markers,
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

        processIntersectingRange(markers, chars, hiStart, hiEnd, tokenHighlights, result);
      }
      finally {
        lexer.advance();
      }
    }

    return result.toArray(new TextChunk[result.size()]);
  }

  private void processIntersectingRange(List<RangeMarker> markers,
                                        CharSequence chars,
                                        int hiStart,
                                        int hiEnd,
                                        TextAttributesKey[] tokenHighlights,
                                        List<TextChunk> result) {
    TextAttributes originalAttrs = convertAttributes(tokenHighlights);
    int lastOffset = hiStart;
    for(RangeMarker rangeMarker: markers) {
      int usageStart = rangeMarker.getStartOffset();
      int usageEnd = rangeMarker.getEndOffset();
      if (rangeMarker.isValid() && rangeIntersect(lastOffset, hiEnd, usageStart, usageEnd)) {
        addChunk(chars, lastOffset, Math.max(lastOffset, usageStart), originalAttrs, false, result);
        addChunk(chars, Math.max(lastOffset, usageStart), Math.min(hiEnd, usageEnd), originalAttrs, true, result);
        if (usageEnd > hiEnd) {
          return;
        }
        lastOffset = usageEnd;
      }
    }
    if (lastOffset < hiEnd) {
      addChunk(chars, lastOffset, hiEnd, originalAttrs, false, result);
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
