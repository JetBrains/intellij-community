// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages;

import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
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
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.reference.SoftReference;
import com.intellij.usageView.UsageTreeColors;
import com.intellij.usageView.UsageTreeColorsScheme;
import com.intellij.usages.impl.SyntaxHighlighterOverEditorHighlighter;
import com.intellij.usages.impl.rules.UsageType;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ChunkExtractor {

  private static final Logger LOG = Logger.getInstance(ChunkExtractor.class);

  static final int MAX_LINE_LENGTH_TO_SHOW = 200;
  static final int OFFSET_BEFORE_TO_SHOW_WHEN_LONG_LINE = 40;
  static final int OFFSET_AFTER_TO_SHOW_WHEN_LONG_LINE = 100;

  private final EditorColorsScheme myColorsScheme;
  private final Document myDocument;
  private long myDocumentStamp;
  private final SyntaxHighlighterOverEditorHighlighter myHighlighter;

  private static final class WeakFactory {

    private WeakReference<Map<PsiFile, ChunkExtractor>> myRef;

    @NotNull
    Map<PsiFile, ChunkExtractor> getValue() {
      Map<PsiFile, ChunkExtractor> cur = SoftReference.dereference(myRef);
      if (cur != null) return cur;
      Map<PsiFile, ChunkExtractor> result = FactoryMap.create(psiFile -> new ChunkExtractor(psiFile));
      myRef = new WeakReference<>(result);
      return result;
    }
  }

  private static final ThreadLocal<WeakFactory> ourExtractors = ThreadLocal.withInitial(() -> new WeakFactory());

  static TextChunk @NotNull [] extractChunks(@NotNull PsiFile file, @NotNull UsageInfo2UsageAdapter usageAdapter) {
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
    FileType fileType = file.getFileType();
    SyntaxHighlighter highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(fileType, project, file.getVirtualFile());
    highlighter = highlighter == null ? new PlainSyntaxHighlighter() : highlighter;
    myHighlighter = new SyntaxHighlighterOverEditorHighlighter(highlighter, file.getVirtualFile(), project);
    myDocumentStamp = -1;
  }

  public static int getStartOffset(List<? extends RangeMarker> rangeMarkers) {
    LOG.assertTrue(!rangeMarkers.isEmpty());
    int minStart = Integer.MAX_VALUE;
    for (RangeMarker rangeMarker : rangeMarkers) {
      if (!rangeMarker.isValid()) continue;
      int startOffset = rangeMarker.getStartOffset();
      if (startOffset < minStart) minStart = startOffset;
    }
    return minStart == Integer.MAX_VALUE ? -1 : minStart;
  }

  private TextChunk @NotNull [] extractChunks(@NotNull UsageInfo2UsageAdapter usageInfo2UsageAdapter, @NotNull PsiFile file) {
    int absoluteStartOffset = usageInfo2UsageAdapter.getNavigationOffset();
    if (absoluteStartOffset == -1) return TextChunk.EMPTY_ARRAY;

    Document visibleDocument = myDocument instanceof DocumentWindow
                               ? ((DocumentWindow)myDocument).getDelegate()
                               : myDocument;
    int visibleStartOffset = myDocument instanceof DocumentWindow
                             ? ((DocumentWindow)myDocument).injectedToHost(absoluteStartOffset)
                             : absoluteStartOffset;

    int lineNumber = myDocument.getLineNumber(absoluteStartOffset);
    int visibleLineNumber = visibleDocument.getLineNumber(visibleStartOffset);
    List<TextChunk> result = new ArrayList<>();
    appendPrefix(result, visibleLineNumber);

    int fragmentToShowStart = myDocument.getLineStartOffset(lineNumber);
    int fragmentToShowEnd = fragmentToShowStart < myDocument.getTextLength() ? myDocument.getLineEndOffset(lineNumber) : 0;
    if (fragmentToShowStart > fragmentToShowEnd) return TextChunk.EMPTY_ARRAY;

    CharSequence chars = myDocument.getCharsSequence();
    if (fragmentToShowEnd - fragmentToShowStart > MAX_LINE_LENGTH_TO_SHOW) {
      int lineStartOffset = fragmentToShowStart;
      fragmentToShowStart = Math.max(lineStartOffset, absoluteStartOffset - OFFSET_BEFORE_TO_SHOW_WHEN_LONG_LINE);

      int lineEndOffset = fragmentToShowEnd;
      Segment segment = usageInfo2UsageAdapter.getUsageInfo().getSegment();
      int usage_length = segment != null ? segment.getEndOffset() - segment.getStartOffset() : 0;
      fragmentToShowEnd = Math.min(lineEndOffset, absoluteStartOffset + usage_length + OFFSET_AFTER_TO_SHOW_WHEN_LONG_LINE);

      // if we search something like a word, then expand shown context from one symbol before / after at least for word boundary
      // this should not cause restarts of the lexer as the tokens are usually words
      if (usage_length > 0 &&
          StringUtil.isJavaIdentifierStart(chars.charAt(absoluteStartOffset)) &&
          StringUtil.isJavaIdentifierStart(chars.charAt(absoluteStartOffset + usage_length - 1))) {
        while (fragmentToShowEnd < lineEndOffset && StringUtil.isJavaIdentifierStart(chars.charAt(fragmentToShowEnd - 1))) {
          ++fragmentToShowEnd;
        }
        while (fragmentToShowStart > lineStartOffset && StringUtil.isJavaIdentifierStart(chars.charAt(fragmentToShowStart))) {
          --fragmentToShowStart;
        }
        if (fragmentToShowStart != lineStartOffset) ++fragmentToShowStart;
        if (fragmentToShowEnd != lineEndOffset) --fragmentToShowEnd;
      }
    }
    if (myDocument instanceof DocumentWindow) {
      List<TextRange> editable = InjectedLanguageManager.getInstance(file.getProject())
        .intersectWithAllEditableFragments(file, new TextRange(fragmentToShowStart, fragmentToShowEnd));
      for (TextRange range : editable) {
        appendTextChunks(usageInfo2UsageAdapter, chars, range.getStartOffset(), range.getEndOffset(), true, result);
      }
      return result.toArray(TextChunk.EMPTY_ARRAY);
    }
    appendTextChunks(usageInfo2UsageAdapter, chars, fragmentToShowStart, fragmentToShowEnd, true, result);
    return result.toArray(TextChunk.EMPTY_ARRAY);
  }

  public void appendTextChunks(@NotNull UsageInfo2UsageAdapter usageInfo2UsageAdapter,
                               @NotNull CharSequence chars,
                               int start,
                               int end,
                               boolean selectUsageWithBold,
                               @NotNull List<? super @NotNull TextChunk> result) {
    processTokens(chars, start, end, (startOffset, endOffset, textAttributesKeys) -> {
      processIntersectingRange(usageInfo2UsageAdapter, chars, startOffset, endOffset, textAttributesKeys, selectUsageWithBold, result);
      return true;
    });
  }

  /**
   * @deprecated use {@link #appendTextChunks(UsageInfo2UsageAdapter, CharSequence, int, int, boolean, List)}
   */
  @Deprecated
  public TextChunk @NotNull [] createTextChunks(@NotNull UsageInfo2UsageAdapter usageInfo2UsageAdapter,
                                                @NotNull CharSequence chars,
                                                int start,
                                                int end,
                                                boolean selectUsageWithBold,
                                                @NotNull List<? super TextChunk> result) {
    appendTextChunks(usageInfo2UsageAdapter, chars, start, end, selectUsageWithBold, result);
    return result.toArray(TextChunk.EMPTY_ARRAY);
  }

  @FunctionalInterface
  private interface TokenHighlightProcessor {
    boolean process(int startOffset, int endOffset, @NotNull TextAttributesKey @NotNull [] textAttributesKeys);
  }

  private void processTokens(@NotNull CharSequence chars, int start, int end, @NotNull TokenHighlightProcessor tokenHighlightProcessor) {
    Lexer lexer = myHighlighter.getHighlightingLexer();
    SyntaxHighlighterOverEditorHighlighter highlighter = myHighlighter;

    LOG.assertTrue(start <= end);

    int i = StringUtil.indexOf(chars, '\n', start, end);
    if (i != -1) end = i;

    if (myDocumentStamp != myDocument.getModificationStamp()) {
      highlighter.restart(chars);
      myDocumentStamp = myDocument.getModificationStamp();
    }
    else if (lexer.getTokenType() == null || lexer.getTokenStart() > start) {
      highlighter.resetPosition(0);  // todo restart from nearest position with initial state
    }

    boolean isBeginning = true;

    for (; lexer.getTokenType() != null; lexer.advance()) {
      int hiStart = lexer.getTokenStart();
      int hiEnd = lexer.getTokenEnd();
      if (hiStart >= end) {
        break;
      }
      hiStart = Math.max(hiStart, start);
      hiEnd = Math.min(hiEnd, end);
      if (hiStart >= hiEnd) {
        continue;
      }
      if (isBeginning) {
        String text = chars.subSequence(hiStart, hiEnd).toString();
        if (text.trim().isEmpty()) {
          continue;
        }
      }
      isBeginning = false;
      IElementType tokenType = lexer.getTokenType();
      TextAttributesKey[] tokenHighlights = highlighter.getTokenHighlights(tokenType);
      if (!tokenHighlightProcessor.process(hiStart, hiEnd, tokenHighlights)) {
        return;
      }
    }
  }

  private void processIntersectingRange(@NotNull UsageInfo2UsageAdapter usageInfo2UsageAdapter,
                                        @NotNull CharSequence chars,
                                        int hiStart,
                                        int hiEnd,
                                        TextAttributesKey @NotNull [] tokenHighlights,
                                        boolean selectUsageWithBold,
                                        @NotNull List<? super @NotNull TextChunk> result) {
    TextAttributes originalAttrs = convertAttributes(tokenHighlights);
    if (selectUsageWithBold) {
      originalAttrs.setFontType(Font.PLAIN);
    }

    int[] lastOffset = {hiStart};
    usageInfo2UsageAdapter.processRangeMarkers(segment -> {
      int usageStart = segment.getStartOffset();
      int usageEnd = segment.getEndOffset();
      if (rangeIntersect(lastOffset[0], hiEnd, usageStart, usageEnd)) {
        addChunk(chars, lastOffset[0], Math.max(lastOffset[0], usageStart), originalAttrs, false, result);
        addChunk(chars, Math.max(lastOffset[0], usageStart), Math.min(hiEnd, usageEnd), originalAttrs, selectUsageWithBold, result);
        lastOffset[0] = usageEnd;
        return usageEnd <= hiEnd;
      }
      return true;
    });
    if (lastOffset[0] < hiEnd) {
      addChunk(chars, lastOffset[0], hiEnd, originalAttrs, false, result);
    }
  }

  @Nullable UsageType deriveUsageTypeFromHighlighting(@NotNull CharSequence chars, int usageStartOffset, int usageEndOffset) {
    Ref<UsageType> result = new Ref<>();
    processTokens(chars, usageStartOffset, usageEndOffset, (__1, __2, tokenAttributeKeys) -> {
      UsageType usageType = deriveUsageTypeFromHighlighting(tokenAttributeKeys);
      if (usageType != null) {
        result.set(usageType);
        return false;
      }
      return true;
    });
    return result.get();
  }

  private static @Nullable UsageType deriveUsageTypeFromHighlighting(@NotNull TextAttributesKey @NotNull [] tokenAttributeKeys) {
    return isHighlightedAsString(tokenAttributeKeys)
           ? UsageType.LITERAL_USAGE
           : isHighlightedAsComment(tokenAttributeKeys)
             ? UsageType.COMMENT_USAGE
             : null;
  }

  public static boolean isHighlightedAsComment(TextAttributesKey @NotNull ... keys) {
    for (TextAttributesKey key : keys) {
      if (key == DefaultLanguageHighlighterColors.DOC_COMMENT ||
          key == DefaultLanguageHighlighterColors.LINE_COMMENT ||
          key == DefaultLanguageHighlighterColors.BLOCK_COMMENT) {
        return true;
      }
      if (key == null) {
        continue;
      }
      TextAttributesKey fallbackAttributeKey = key.getFallbackAttributeKey();
      if (fallbackAttributeKey != null && isHighlightedAsComment(fallbackAttributeKey)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isHighlightedAsString(TextAttributesKey @NotNull ... keys) {
    for (TextAttributesKey key : keys) {
      if (key == DefaultLanguageHighlighterColors.STRING) {
        return true;
      }
      if (key == null) {
        continue;
      }
      TextAttributesKey fallbackAttributeKey = key.getFallbackAttributeKey();
      if (fallbackAttributeKey != null && isHighlightedAsString(fallbackAttributeKey)) {
        return true;
      }
    }
    return false;
  }

  private static void addChunk(@NotNull CharSequence chars,
                               int start,
                               int end,
                               @NotNull TextAttributes originalAttrs,
                               boolean bold,
                               @NotNull List<? super @NotNull TextChunk> result) {
    if (start < end) {
      TextAttributes attrs = bold
                             ? TextAttributes.merge(originalAttrs, new TextAttributes(null, null, null, null, Font.BOLD))
                             : originalAttrs;
      result.add(new TextChunk(attrs, new String(CharArrayUtil.fromSequence(chars, start, end))));
    }
  }

  private static boolean rangeIntersect(int s1, int e1, int s2, int e2) {
    return s2 < s1 && s1 < e2 || s2 < e1 && e1 < e2
           || s1 < s2 && s2 < e1 || s1 < e2 && e2 < e1
           || s1 == s2 && e1 == e2;
  }

  @NotNull
  private TextAttributes convertAttributes(TextAttributesKey @NotNull [] keys) {
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

  private static void appendPrefix(@NotNull List<? super TextChunk> result, int lineNumber) {
    result.add(new TextChunk(UsageTreeColors.NUMBER_OF_USAGES_ATTRIBUTES.toTextAttributes(), String.valueOf(lineNumber + 1)));
  }
}
