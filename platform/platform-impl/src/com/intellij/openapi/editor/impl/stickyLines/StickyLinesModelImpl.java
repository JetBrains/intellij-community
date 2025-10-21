// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.stickyLines;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.jetbrains.annotations.ApiStatus.Internal;

@Internal
public final class StickyLinesModelImpl implements StickyLinesModel {
  private static final Logger LOG = Logger.getInstance(StickyLinesModelImpl.class);
  private static final Key<SourceID> STICKY_LINE_SOURCE = Key.create("editor.sticky.lines.source");
  private static final Key<StickyLinesModelImpl> STICKY_LINES_MODEL_KEY = Key.create("editor.sticky.lines.model");
  private static final Key<StickyLineImpl> STICKY_LINE_IMPL_KEY = Key.create("editor.sticky.line.impl");
  private static final String STICKY_LINE_MARKER = "STICKY_LINE_MARKER";
  private static final TextAttributesKey STICKY_LINE_ATTRIBUTE = TextAttributesKey.createTextAttributesKey(
    STICKY_LINE_MARKER
  );

  public static boolean isStickyLine(@NotNull RangeHighlighter highlighter) {
    TextAttributesKey key = highlighter.getTextAttributesKey();
    if (key != null && STICKY_LINE_MARKER.equals(key.getExternalName())) {
      return true;
    }
    return false;
  }

  /**
   * IJPL-26873 skip sticky line highlighter in all editors.
   *
   * <p>Even though the sticky highlighter does not contain text attributes,
   * {@code highlighter.getTextAttributes(editor.getColorsScheme())} may return non-null attributes.
   * Which means incorrect text color.
   */
  public static void skipInAllEditors(@NotNull RangeHighlighter highlighter) {
    highlighter.setEditorFilter(StickyLinesModelImpl::alwaysFalsePredicate);
  }

  static @Nullable StickyLinesModelImpl getModel(@NotNull Project project, @NotNull Document document) {
    if (project.isDisposed()) {
      String editors = editorsAsString(document);
      LOG.error(
        """
          ______________________________________________________________________________________
          getting sticky lines model when project is already disposed
          disposed project: %s
          editors:
          %s
          ______________________________________________________________________________________
          """.formatted(project, editors),
        new Throwable()
      );
      return null;
    }
    MarkupModel markupModel = DocumentMarkupModel.forDocument(document, project, false);
    if (markupModel == null) {
      String editors = editorsAsString(document);
      // TODO: it should be error but in test `RunWithComposeHotReloadRunGutterSmokeTest` happens something crazy:
      //  editor and markup model do not exist inside `doApplyInformationToEditor`
      LOG.warn /*error*/ (
        """
          ______________________________________________________________________________________
          getting sticky lines model when markup model is not created
          editors:
          %s
          ______________________________________________________________________________________
          """.formatted(editors),
        new Throwable()
      );
      return null;
    }
    return getModel(markupModel);
  }

  static @NotNull StickyLinesModelImpl getModel(@NotNull MarkupModel markupModel) {
    StickyLinesModelImpl stickyModel = markupModel.getUserData(STICKY_LINES_MODEL_KEY);
    if (stickyModel == null) {
      stickyModel = new StickyLinesModelImpl((MarkupModelEx) markupModel);
      markupModel.putUserData(STICKY_LINES_MODEL_KEY, stickyModel);
    }
    return stickyModel;
  }

  private final MarkupModelEx myMarkupModel;
  private final List<Listener> myListeners;
  private boolean myIsCleared;

  private StickyLinesModelImpl(MarkupModelEx markupModel) {
    myMarkupModel = markupModel;
    myListeners = new ArrayList<>();
    myIsCleared = false;
  }

  @Override
  public @NotNull StickyLine addStickyLine(@NotNull SourceID source, int startOffset, int endOffset, @Nullable String debugText) {
    if (startOffset >= endOffset) {
      throw new IllegalArgumentException(String.format(
        "sticky line endOffset %s should be less than startOffset %s", startOffset, endOffset
      ));
    }
    RangeHighlighter highlighter = myMarkupModel.addRangeHighlighter(
      STICKY_LINE_ATTRIBUTE,
      startOffset,
      endOffset,
      0, // value should be less than SYNTAX because of bug in colors scheme IJPL-149486
      HighlighterTargetArea.EXACT_RANGE
    );
    StickyLineImpl stickyLine = new StickyLineImpl(highlighter.getDocument(), highlighter, debugText);
    highlighter.putUserData(STICKY_LINE_IMPL_KEY, stickyLine);
    highlighter.putUserData(STICKY_LINE_SOURCE, source);
    skipInAllEditors(highlighter);
    myIsCleared = false;
    return stickyLine;
  }

  @Override
  public void removeStickyLine(@NotNull StickyLine stickyLine) {
    RangeMarker rangeMarker = ((StickyLineImpl)stickyLine).rangeMarker();
    myMarkupModel.removeHighlighter((RangeHighlighter) rangeMarker);
  }

  @Override
  public void processStickyLines(int startOffset, int endOffset, @NotNull Processor<? super @NotNull StickyLine> processor) {
    processStickyLines(null, startOffset, endOffset, processor);
  }

  @Override
  public void processStickyLines(@NotNull SourceID source, @NotNull Processor<? super @NotNull StickyLine> processor) {
    processStickyLines(source, 0, myMarkupModel.getDocument().getTextLength(), processor);
  }

  @Override
  public @NotNull List<@NotNull StickyLine> getAllStickyLines() {
    ArrayList<StickyLine> stickyLines = new ArrayList<>();
    processStickyLines(
      0,
      myMarkupModel.getDocument().getTextLength(),
      line -> {
        stickyLines.add(line);
        return true;
      }
    );
    return stickyLines;
  }

  @Override
  public void addListener(@NotNull Listener listener) {
    myListeners.add(listener);
  }

  @Override
  public void removeListener(@NotNull Listener listener) {
    myListeners.remove(listener);
  }

  @Override
  public void notifyLinesUpdate() {
    for (Listener listener : myListeners) {
      listener.linesUpdated();
    }
  }

  @Override
  public void removeAllStickyLines(@Nullable Project project) {
    if (myIsCleared) {
      return;
    }
    for (StickyLine line : getAllStickyLines()) {
      removeStickyLine(line);
    }
    if (project != null) {
      restartStickyLinesPass(project);
    }
    myIsCleared = true;
  }

  private void processStickyLines(
    @Nullable SourceID source,
    int startOffset,
    int endOffset,
    @NotNull Processor<? super StickyLine> processor
  ) {
    myMarkupModel.processRangeHighlightersOverlappingWith(
      startOffset,
      endOffset,
      highlighter -> {
        if (STICKY_LINE_ATTRIBUTE.equals(highlighter.getTextAttributesKey()) && isSuitableSource(highlighter, source)) {
          StickyLineImpl stickyLine = highlighter.getUserData(STICKY_LINE_IMPL_KEY);
          if (stickyLine == null) {
            // probably it is a zombie highlighter
            stickyLine = new StickyLineImpl(highlighter.getDocument(), highlighter, "StickyZombie");
          }
          return processor.process(stickyLine);
        } else {
          return true;
        }
      }
    );
  }

  private static boolean isSuitableSource(RangeHighlighterEx highlighter, @Nullable SourceID source) {
    return source == null || source.equals(highlighter.getUserData(STICKY_LINE_SOURCE));
  }

  private static boolean alwaysFalsePredicate(@NotNull Editor editor) {
    return false;
  }

  private void restartStickyLinesPass(@NotNull Project project) {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      ReadAction.run(() -> {
        if (!project.isDisposed()) {
          var collector = new StickyLinesCollector(project, myMarkupModel.getDocument());
          collector.forceCollectPass();
        }
      });
    });
  }

  private static @NotNull String editorsAsString(@NotNull Document document) {
    return Arrays.stream(EditorFactory.getInstance().getEditors(document))
      .map(editor -> editor.toString() + "\n" + editor.getProject())
      .collect(Collectors.joining("\n"));
  }

  private record StickyLineImpl(
    @NotNull Document document,
    @NotNull RangeMarker rangeMarker,
    @Nullable String debugText
  ) implements StickyLine {

    @Override
    public int primaryLine() {
      return document.getLineNumber(rangeMarker.getStartOffset());
    }

    @Override
    public int scopeLine() {
      return document.getLineNumber(rangeMarker.getEndOffset());
    }

    @Override
    public int navigateOffset() {
      return rangeMarker.getStartOffset();
    }

    @Override
    public @NotNull TextRange textRange() {
      return rangeMarker.getTextRange();
    }

    @Override
    public @Nullable String debugText() {
      return debugText;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) return true;
      if (!(other instanceof StickyLineImpl impl)) return false;
      return textRange().equals(impl.textRange());
    }

    @Override
    public int hashCode() {
      return textRange().hashCode();
    }

    @Override
    public int compareTo(@NotNull StickyLine other) {
      TextRange range = textRange();
      TextRange otherRange = other.textRange();
      int compare = Integer.compare(range.getStartOffset(), otherRange.getStartOffset());
      if (compare != 0) {
        return compare;
      }
      // reverse order
      return Integer.compare(otherRange.getEndOffset(), range.getEndOffset());
    }

    @Override
    public @NotNull String toString() {
      String prefix = debugText == null ? "" : debugText;
      return prefix + "(" + primaryLine() + ", " + scopeLine() + ")";
    }
  }
}
