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

import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
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
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.usageView.UsageTreeColors;
import com.intellij.usageView.UsageTreeColorsScheme;
import com.intellij.util.Processor;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.StringFactory;
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
  public static final int MAX_LINE_LENGTH_TO_SHOW = 200;
  public static final int OFFSET_BEFORE_TO_SHOW_WHEN_LONG_LINE = 1;
  public static final int OFFSET_AFTER_TO_SHOW_WHEN_LONG_LINE = 1;

  private final EditorColorsScheme myColorsScheme;

  private final Document myDocument;
  private long myDocumentStamp;
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
            protected ChunkExtractor create(PsiFile psiFile) {
              return new ChunkExtractor(psiFile);
            }
          };
        }
      };
    }
  };

  @NotNull 
  public static TextChunk[] extractChunks(@NotNull PsiFile file, @NotNull UsageInfo2UsageAdapter usageAdapter) {
    return getExtractor(file).extractChunks(usageAdapter, file);
  }

  @NotNull
  public static ChunkExtractor getExtractor(@NotNull PsiFile file) {
    return ourExtractors.get().getValue().get(file);
  }

  private ChunkExtractor(@NotNull PsiFile file) {
    myColorsScheme = UsageTreeColorsScheme.getInstance().getScheme();

    Project project = file.getProject();
    myDocument = PsiDocumentManager.getInstance(project).getDocument(file);
    LOG.assertTrue(myDocument != null);
    final FileType fileType = file.getFileType();
    final SyntaxHighlighter highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(fileType, project, file.getVirtualFile());
    myHighlighter = highlighter == null ? new PlainSyntaxHighlighter() : highlighter;
    myLexer = myHighlighter.getHighlightingLexer();
    myLexer.start(myDocument.getCharsSequence());
    myDocumentStamp = myDocument.getModificationStamp();
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

  @NotNull 
  private TextChunk[] extractChunks(@NotNull UsageInfo2UsageAdapter usageInfo2UsageAdapter, @NotNull PsiFile file) {
    int absoluteStartOffset = usageInfo2UsageAdapter.getNavigationOffset();
    if (absoluteStartOffset == -1) return TextChunk.EMPTY_ARRAY;

    Document visibleDocument = myDocument instanceof DocumentWindow ? ((DocumentWindow)myDocument).getDelegate() : myDocument;
    int visibleStartOffset = myDocument instanceof DocumentWindow ? ((DocumentWindow)myDocument).injectedToHost(absoluteStartOffset) : absoluteStartOffset;

    int lineNumber = myDocument.getLineNumber(absoluteStartOffset);
    int visibleLineNumber = visibleDocument.getLineNumber(visibleStartOffset);
    int visibleColumnNumber = visibleStartOffset - visibleDocument.getLineStartOffset(visibleLineNumber);
    final List<TextChunk> result = new ArrayList<TextChunk>();
    appendPrefix(result, visibleLineNumber, visibleColumnNumber);

    int fragmentToShowStart = myDocument.getLineStartOffset(lineNumber);
    int fragmentToShowEnd = fragmentToShowStart < myDocument.getTextLength() ? myDocument.getLineEndOffset(lineNumber) : 0;
    if (fragmentToShowStart > fragmentToShowEnd) return TextChunk.EMPTY_ARRAY;

    final CharSequence chars = myDocument.getCharsSequence();
    if (fragmentToShowEnd - fragmentToShowStart > MAX_LINE_LENGTH_TO_SHOW) {
      final int lineStartOffset = fragmentToShowStart;
      fragmentToShowStart = Math.max(lineStartOffset, absoluteStartOffset - OFFSET_BEFORE_TO_SHOW_WHEN_LONG_LINE);

      final int lineEndOffset = fragmentToShowEnd;
      Segment segment = usageInfo2UsageAdapter.getUsageInfo().getSegment();
      int usage_length = segment != null ? segment.getEndOffset() - segment.getStartOffset():0;
      fragmentToShowEnd = Math.min(lineEndOffset, absoluteStartOffset + usage_length + OFFSET_AFTER_TO_SHOW_WHEN_LONG_LINE);

      // if we search something like a word, then expand shown context from one symbol before / after at least for word boundary
      // this should not cause restarts of the lexer as the tokens are usually words
      if (usage_length > 0 &&
          StringUtil.isJavaIdentifierStart(chars.charAt(absoluteStartOffset)) &&
          StringUtil.isJavaIdentifierStart(chars.charAt(absoluteStartOffset + usage_length - 1))) {
        while(fragmentToShowEnd < lineEndOffset && StringUtil.isJavaIdentifierStart(chars.charAt(fragmentToShowEnd - 1))) ++fragmentToShowEnd;
        while(fragmentToShowStart > lineStartOffset && StringUtil.isJavaIdentifierStart(chars.charAt(fragmentToShowStart))) --fragmentToShowStart;
        if (fragmentToShowStart != lineStartOffset) ++fragmentToShowStart;
        if (fragmentToShowEnd != lineEndOffset) --fragmentToShowEnd;
      }
    }
    if (myDocument instanceof DocumentWindow) {
      List<TextRange> editable = InjectedLanguageManager.getInstance(file.getProject())
        .intersectWithAllEditableFragments(file, new TextRange(fragmentToShowStart, fragmentToShowEnd));
      for (TextRange range : editable) {
        createTextChunks(usageInfo2UsageAdapter, chars, range.getStartOffset(), range.getEndOffset(), true, result);
      }
      return result.toArray(new TextChunk[result.size()]);
    }
    return createTextChunks(usageInfo2UsageAdapter, chars, fragmentToShowStart, fragmentToShowEnd, true, result);
  }

  @NotNull
  public TextChunk[] createTextChunks(@NotNull UsageInfo2UsageAdapter usageInfo2UsageAdapter,
                                      @NotNull CharSequence chars,
                                      int start,
                                      int end,
                                      boolean selectUsageWithBold,
                                      @NotNull List<TextChunk> result) {
    final Lexer lexer = myLexer;
    final SyntaxHighlighter highlighter = myHighlighter;

    LOG.assertTrue(start <= end);

    int i = StringUtil.indexOf(chars, '\n', start, end);
    if (i != -1) end = i;

    if (lexer.getTokenStart() > start || myDocumentStamp != myDocument.getModificationStamp()) {
      lexer.start(chars);
      myDocumentStamp = myDocument.getModificationStamp();
    }
    boolean isBeginning = true;

    for(;lexer.getTokenType() != null; lexer.advance()) {
      int hiStart = lexer.getTokenStart();
      int hiEnd = lexer.getTokenEnd();

      if (hiStart >= end) break;

      hiStart = Math.max(hiStart, start);
      hiEnd = Math.min(hiEnd, end);
      if (hiStart >= hiEnd) { continue; }

      String text = chars.subSequence(hiStart, hiEnd).toString();
      if (isBeginning && text.trim().isEmpty()) continue;
      isBeginning = false;
      IElementType tokenType = lexer.getTokenType();
      TextAttributesKey[] tokenHighlights = highlighter.getTokenHighlights(tokenType);

      processIntersectingRange(usageInfo2UsageAdapter, chars, hiStart, hiEnd, tokenHighlights, selectUsageWithBold, result);
    }

    return result.toArray(new TextChunk[result.size()]);
  }

  private void processIntersectingRange(@NotNull UsageInfo2UsageAdapter usageInfo2UsageAdapter,
                                        @NotNull final CharSequence chars,
                                        int hiStart,
                                        final int hiEnd,
                                        @NotNull TextAttributesKey[] tokenHighlights,
                                        final boolean selectUsageWithBold,
                                        @NotNull final List<TextChunk> result) {
    final TextAttributes originalAttrs = convertAttributes(tokenHighlights);
    if (selectUsageWithBold) {
      originalAttrs.setFontType(Font.PLAIN);
    }

    final int[] lastOffset = {hiStart};
    usageInfo2UsageAdapter.processRangeMarkers(new Processor<Segment>() {
      @Override
      public boolean process(Segment segment) {
        int usageStart = segment.getStartOffset();
        int usageEnd = segment.getEndOffset();
        if (rangeIntersect(lastOffset[0], hiEnd, usageStart, usageEnd)) {
          addChunk(chars, lastOffset[0], Math.max(lastOffset[0], usageStart), originalAttrs, false, result);
          addChunk(chars, Math.max(lastOffset[0], usageStart), Math.min(hiEnd, usageEnd), originalAttrs, selectUsageWithBold, result);
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

  private static void addChunk(@NotNull CharSequence chars,
                               int start,
                               int end,
                               @NotNull TextAttributes originalAttrs,
                               boolean bold,
                               @NotNull List<TextChunk> result) {
    if (start >= end) return;

    TextAttributes attrs = bold
                           ? TextAttributes.merge(originalAttrs, new TextAttributes(null, null, null, null, Font.BOLD))
                           : originalAttrs;
    result.add(new TextChunk(attrs, StringFactory.createShared(CharArrayUtil.fromSequence(chars, start, end))));
  }

  private static boolean rangeIntersect(int s1, int e1, int s2, int e2) {
    return s2 < s1 && s1 < e2 || s2 < e1 && e1 < e2
           || s1 < s2 && s2 < e1 || s1 < e2 && e2 < e1
           || s1 == s2 && e1 == e2;
  }

  @NotNull
  private TextAttributes convertAttributes(@NotNull TextAttributesKey[] keys) {
    TextAttributes attrs = myColorsScheme.getAttributes(HighlighterColors.TEXT);

    for (TextAttributesKey key : keys) {
      TextAttributes attrs2 = myColorsScheme.getAttributes(key);
      if (attrs2 != null) {
        attrs = TextAttributes.merge(attrs, attrs2);
      }
    }

    attrs = attrs.clone();
    return attrs;
  }

  private void appendPrefix(@NotNull List<TextChunk> result, int lineNumber, int columnNumber) {
    String prefix = "(" + (lineNumber + 1) + ": " + (columnNumber + 1) + ") ";
    TextChunk prefixChunk = new TextChunk(myColorsScheme.getAttributes(UsageTreeColors.USAGE_LOCATION), prefix);
    result.add(prefixChunk);
  }
}
