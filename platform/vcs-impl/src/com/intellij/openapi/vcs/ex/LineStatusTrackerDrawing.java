/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.ex;

import com.intellij.codeInsight.hint.EditorFragmentComponent;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.diff.comparison.ByWord;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.fragments.DiffFragment;
import com.intellij.diff.util.BackgroundTaskUtil;
import com.intellij.diff.util.DiffDrawUtil;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.TextDiffType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.diff.DiffColors;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.markup.ActiveGutterRenderer;
import com.intellij.openapi.editor.markup.LineMarkerRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.actions.ShowNextChangeMarkerAction;
import com.intellij.openapi.vcs.actions.ShowPrevChangeMarkerAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredSideBorder;
import com.intellij.ui.HintHint;
import com.intellij.ui.HintListener;
import com.intellij.ui.LightweightHint;
import com.intellij.util.Function;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;

/**
 * @author irengrig
 */
public class LineStatusTrackerDrawing {
  private LineStatusTrackerDrawing() {
  }

  static TextAttributes getAttributesFor(final Range range) {
    return new TextAttributes() {
      @Override
      public Color getErrorStripeColor() {
        return getDiffColor(range);
      }
    };
  }

  private static void paintGutterFragment(final Editor editor, final Graphics g, final Rectangle r, final Range range) {
    final EditorGutterComponentEx gutter = ((EditorEx)editor).getGutterComponentEx();
    Color gutterColor = getDiffGutterColor(range);
    Color borderColor = getDiffGutterBorderColor();

    final int x = r.x + r.width - 3;
    final int endX = gutter.getWhitespaceSeparatorOffset();

    final int y = lineToY(editor, range.getLine1());
    final int endY = lineToY(editor, range.getLine2());

    if (range.getInnerRanges() == null) { // Mode.DEFAULT
      if (y != endY) {
        paintRect(g, gutterColor, borderColor, x, y, endX, endY);
      }
      else {
        paintTriangle(g, gutterColor, borderColor, x, endX, y);
      }
    }
    else { // Mode.SMART
      if (y == endY) {
        paintTriangle(g, gutterColor, borderColor, x, endX, y);
      }
      else {
        List<Range.InnerRange> innerRanges = range.getInnerRanges();
        for (Range.InnerRange innerRange : innerRanges) {
          if (innerRange.getType() == Range.DELETED) continue;

          int start = lineToY(editor, innerRange.getLine1());
          int end = lineToY(editor, innerRange.getLine2());

          paintRect(g, getDiffColor(innerRange), null, x, start, endX, end);
        }

        for (int i = 0; i < innerRanges.size(); i++) {
          Range.InnerRange innerRange = innerRanges.get(i);
          if (innerRange.getType() != Range.DELETED) continue;

          int start;
          int end;

          if (i == 0) {
            start = lineToY(editor, innerRange.getLine1());
            end = lineToY(editor, innerRange.getLine2()) + 5;
          }
          else if (i == innerRanges.size() - 1) {
            start = lineToY(editor, innerRange.getLine1()) - 5;
            end = lineToY(editor, innerRange.getLine2());
          }
          else {
            start = lineToY(editor, innerRange.getLine1()) - 3;
            end = lineToY(editor, innerRange.getLine2()) + 3;
          }

          paintRect(g, getDiffColor(innerRange), null, x, start, endX, end);
        }

        paintRect(g, null, borderColor, x, y, endX, endY);
      }
    }
  }

  private static int lineToY(@NotNull Editor editor, int line) {
    Document document = editor.getDocument();
    if (line >= document.getLineCount()) {
      int y = lineToY(editor, document.getLineCount() - 1);
      return y + editor.getLineHeight() * (line - document.getLineCount() + 1);
    }
    return editor.logicalPositionToXY(editor.offsetToLogicalPosition(document.getLineStartOffset(line))).y;
  }

  private static void paintRect(@NotNull Graphics g, @Nullable Color color, @Nullable Color borderColor, int x1, int y1, int x2, int y2) {
    if (color != null) {
      g.setColor(color);
      g.fillRect(x1, y1, x2 - x1, y2 - y1);
    }
    if (borderColor != null) {
      g.setColor(borderColor);
      UIUtil.drawLine(g, x1, y1, x2 - 1, y1);
      UIUtil.drawLine(g, x1, y1, x1, y2 - 1);
      UIUtil.drawLine(g, x1, y2 - 1, x2 - 1, y2 - 1);
    }
  }

  private static void paintTriangle(@NotNull Graphics g, @Nullable Color color, @Nullable Color borderColor, int x1, int x2, int y) {
    int size = 4;

    final int[] xPoints = new int[]{x1, x1, x2};
    final int[] yPoints = new int[]{y - size, y + size, y};

    if (color != null) {
      g.setColor(color);
      g.fillPolygon(xPoints, yPoints, xPoints.length);
    }
    if (borderColor != null) {
      g.setColor(borderColor);
      g.drawPolygon(xPoints, yPoints, xPoints.length);
    }
  }

  public static LineMarkerRenderer createRenderer(final Range range, final LineStatusTracker tracker) {
    return new ActiveGutterRenderer() {
      public void paint(final Editor editor, final Graphics g, final Rectangle r) {
        paintGutterFragment(editor, g, r, range);
      }

      public void doAction(final Editor editor, final MouseEvent e) {
        e.consume();
        final JComponent comp = (JComponent)e.getComponent(); // shall be EditorGutterComponent, cast is safe.
        final JLayeredPane layeredPane = comp.getRootPane().getLayeredPane();
        final Point point = SwingUtilities.convertPoint(comp, ((EditorEx)editor).getGutterComponentEx().getWidth(), e.getY(), layeredPane);
        showActiveHint(range, editor, point, tracker);
      }

      public boolean canDoAction(final MouseEvent e) {
        final EditorGutterComponentEx gutter = (EditorGutterComponentEx)e.getComponent();
        return e.getX() > gutter.getLineMarkerFreePaintersAreaOffset();
      }
    };
  }

  public static void showActiveHint(@NotNull Range range,
                                    @NotNull Editor editor,
                                    @Nullable Point mousePosition,
                                    @NotNull LineStatusTracker tracker) {
    final Disposable disposable = Disposer.newDisposable();

    List<DiffFragment> wordDiff = computeWordDiff(range, tracker);

    installEditorHighlighters(range, editor, tracker, wordDiff, disposable);
    Pair<JComponent, Integer> editorComponent = createEditorComponent(range, editor, tracker, wordDiff);

    ActionToolbar toolbar = buildToolbar(range, editor, tracker, disposable);
    PopupPanel popupPanel = new PopupPanel(editor, toolbar, editorComponent.first, editorComponent.second);

    LightweightHint hint = new LightweightHint(popupPanel);
    HintListener closeListener = new HintListener() {
      public void hintHidden(final EventObject event) {
        Disposer.dispose(disposable);
      }
    };
    hint.addHintListener(closeListener);

    int line = editor.getCaretModel().getLogicalPosition().line;
    Point point = HintManagerImpl.getHintPosition(hint, editor, new LogicalPosition(line, 0), HintManager.UNDER);
    if (mousePosition != null) { // show right after the nearest line
      int lineHeight = editor.getLineHeight();
      int delta = (point.y - mousePosition.y) % lineHeight;
      if (delta < 0) delta += lineHeight;
      point.y = mousePosition.y + delta;
    }
    point.x -= popupPanel.getEditorTextOffset(); // align main editor with the one in popup

    int flags = HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING;
    HintManagerImpl.getInstanceImpl().showEditorHint(hint, editor, point, flags, -1, false, new HintHint(editor, point));

    if (!hint.isVisible()) {
      closeListener.hintHidden(null);
    }
  }

  @Nullable
  private static List<DiffFragment> computeWordDiff(@NotNull Range range, @NotNull LineStatusTracker tracker) {
    if (range.getType() != Range.MODIFIED) return null;

    final CharSequence vcsContent = tracker.getVcsContent(range);
    final CharSequence currentContent = tracker.getCurrentContent(range);

    return BackgroundTaskUtil.tryComputeFast(new Function<ProgressIndicator, List<DiffFragment>>() {
      @Override
      public List<DiffFragment> fun(ProgressIndicator indicator) {
        return ByWord.compare(vcsContent, currentContent, ComparisonPolicy.DEFAULT, indicator);
      }
    }, Registry.intValue("diff.status.tracker.byword.delay"));
  }

  @NotNull
  private static ActionToolbar buildToolbar(@NotNull Range range,
                                            @NotNull Editor editor,
                                            @NotNull LineStatusTracker tracker,
                                            @NotNull Disposable parentDisposable) {
    final DefaultActionGroup group = new DefaultActionGroup();

    final ShowPrevChangeMarkerAction localShowPrevAction = new ShowPrevChangeMarkerAction(tracker.getPrevRange(range), tracker, editor);
    final ShowNextChangeMarkerAction localShowNextAction = new ShowNextChangeMarkerAction(tracker.getNextRange(range), tracker, editor);
    final RollbackLineStatusRangeAction rollback = new RollbackLineStatusRangeAction(tracker, range, editor);
    final ShowLineStatusRangeDiffAction showDiff = new ShowLineStatusRangeDiffAction(tracker, range, editor);
    final CopyLineStatusRangeAction copyRange = new CopyLineStatusRangeAction(tracker, range);

    group.add(localShowPrevAction);
    group.add(localShowNextAction);
    group.add(rollback);
    group.add(showDiff);
    group.add(copyRange);

    JComponent editorComponent = editor.getComponent();
    EmptyAction.setupAction(localShowPrevAction, "VcsShowPrevChangeMarker", editorComponent);
    EmptyAction.setupAction(localShowNextAction, "VcsShowNextChangeMarker", editorComponent);
    EmptyAction.setupAction(rollback, IdeActions.SELECTED_CHANGES_ROLLBACK, editorComponent);
    EmptyAction.setupAction(showDiff, "ChangesView.Diff", editorComponent);
    EmptyAction.setupAction(copyRange, IdeActions.ACTION_COPY, editorComponent);

    final List<AnAction> actionList = ActionUtil.getActions(editorComponent);
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        actionList.remove(localShowPrevAction);
        actionList.remove(localShowNextAction);
        actionList.remove(rollback);
        actionList.remove(showDiff);
        actionList.remove(copyRange);
      }
    });

    return ActionManager.getInstance().createActionToolbar(ActionPlaces.FILEHISTORY_VIEW_TOOLBAR, group, true);
  }

  private static void installEditorHighlighters(@NotNull Range range,
                                                @NotNull Editor editor,
                                                @NotNull LineStatusTracker tracker,
                                                @Nullable List<DiffFragment> wordDiff,
                                                @NotNull Disposable parentDisposable) {
    if (wordDiff == null) return;
    final List<RangeHighlighter> highlighters = new ArrayList<RangeHighlighter>();

    int currentStartShift = tracker.getCurrentTextRange(range).getStartOffset();
    for (DiffFragment fragment : wordDiff) {
      int currentStart = currentStartShift + fragment.getStartOffset2();
      int currentEnd = currentStartShift + fragment.getEndOffset2();
      TextDiffType type = DiffUtil.getDiffType(fragment);

      highlighters.add(DiffDrawUtil.createInlineHighlighter(editor, currentStart, currentEnd, type));
    }

    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        for (RangeHighlighter highlighter : highlighters) {
          highlighter.dispose();
        }
      }
    });
  }

  @NotNull
  private static Pair<JComponent, Integer> createEditorComponent(@NotNull Range range,
                                                                 @NotNull Editor editor,
                                                                 @NotNull LineStatusTracker tracker,
                                                                 @Nullable List<DiffFragment> wordDiff) {
    if (range.getType() == Range.INSERTED) return Pair.create(null, 0);

    DocumentEx doc = (DocumentEx)tracker.getVcsDocument();
    EditorEx uEditor = (EditorEx)EditorFactory.getInstance().createViewer(doc, tracker.getProject());
    uEditor.setColorsScheme(editor.getColorsScheme());

    EditorHighlighterFactory highlighterFactory = EditorHighlighterFactory.getInstance();
    EditorHighlighter highlighter = highlighterFactory.createEditorHighlighter(tracker.getProject(), getFileName(tracker.getDocument()));
    uEditor.setHighlighter(highlighter);

    if (wordDiff != null) {
      int vcsStartShift = tracker.getVcsTextRange(range).getStartOffset();
      for (DiffFragment fragment : wordDiff) {
        int vcsStart = vcsStartShift + fragment.getStartOffset1();
        int vcsEnd = vcsStartShift + fragment.getEndOffset1();
        TextDiffType type = DiffUtil.getDiffType(fragment);

        DiffDrawUtil.createInlineHighlighter(uEditor, vcsStart, vcsEnd, type);
      }
    }

    JComponent fragmentComponent =
      EditorFragmentComponent.createEditorFragmentComponent(uEditor, range.getVcsLine1(), range.getVcsLine2(), false, false);
    int leftBorder = fragmentComponent.getBorder().getBorderInsets(fragmentComponent).left;

    EditorFactory.getInstance().releaseEditor(uEditor);

    return Pair.create(fragmentComponent, leftBorder);
  }

  private static String getFileName(final Document document) {
    final VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    if (file == null) return "";
    return file.getName();
  }

  public static void moveToRange(final Range range, final Editor editor, final LineStatusTracker tracker) {
    final Document document = tracker.getDocument();
    int line = Math.min(range.getType() == Range.DELETED ? range.getLine2() : range.getLine2() - 1, document.getLineCount() - 1);
    final int lastOffset = document.getLineStartOffset(line);
    editor.getCaretModel().moveToOffset(lastOffset);
    editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);

    showHint(range, editor, tracker);
  }

  public static void showHint(final Range range, final Editor editor, final LineStatusTracker tracker) {
    editor.getScrollingModel().runActionOnScrollingFinished(new Runnable() {
      public void run() {
        showActiveHint(range, editor, null, tracker);
      }
    });
  }

  @Nullable
  private static Color getDiffColor(@NotNull Range.InnerRange range) {
    // TODO: we should move color settings from Colors-General to Colors-Diff
    final EditorColorsScheme globalScheme = EditorColorsManager.getInstance().getGlobalScheme();
    switch (range.getType()) {
      case Range.INSERTED:
        return globalScheme.getColor(EditorColors.ADDED_LINES_COLOR);
      case Range.DELETED:
        return globalScheme.getColor(EditorColors.DELETED_LINES_COLOR);
      case Range.MODIFIED:
        return globalScheme.getColor(EditorColors.MODIFIED_LINES_COLOR);
      case Range.EQUAL:
        return globalScheme.getColor(EditorColors.WHITESPACES_MODIFIED_LINES_COLOR);
      default:
        assert false;
        return null;
    }
  }

  @Nullable
  private static Color getDiffColor(@NotNull Range range) {
    final EditorColorsScheme globalScheme = EditorColorsManager.getInstance().getGlobalScheme();
    switch (range.getType()) {
      case Range.INSERTED:
        return globalScheme.getAttributes(DiffColors.DIFF_INSERTED).getErrorStripeColor();
      case Range.DELETED:
        return globalScheme.getAttributes(DiffColors.DIFF_DELETED).getErrorStripeColor();
      case Range.MODIFIED:
        return globalScheme.getAttributes(DiffColors.DIFF_MODIFIED).getErrorStripeColor();
      default:
        assert false;
        return null;
    }
  }

  @Nullable
  private static Color getDiffGutterColor(@NotNull Range range) {
    final EditorColorsScheme globalScheme = EditorColorsManager.getInstance().getGlobalScheme();
    switch (range.getType()) {
      case Range.INSERTED:
        return globalScheme.getColor(EditorColors.ADDED_LINES_COLOR);
      case Range.DELETED:
        return globalScheme.getColor(EditorColors.DELETED_LINES_COLOR);
      case Range.MODIFIED:
        return globalScheme.getColor(EditorColors.MODIFIED_LINES_COLOR);
      default:
        assert false;
        return null;
    }
  }

  @Nullable
  private static Color getDiffGutterBorderColor() {
    final EditorColorsScheme globalScheme = EditorColorsManager.getInstance().getGlobalScheme();
    return globalScheme.getColor(EditorColors.BORDER_LINES_COLOR);
  }

  private static class PopupPanel extends JPanel {
    private final JComponent myEditorComponent;
    private final int myEditorLeftBorder;

    public PopupPanel(@NotNull final Editor editor,
                      @NotNull ActionToolbar toolbar,
                      @Nullable JComponent editorComponent,
                      int editorLeftBorder) {
      super(new BorderLayout());
      setOpaque(false);

      myEditorComponent = editorComponent;
      myEditorLeftBorder = editorLeftBorder;

      Color background = ((EditorEx)editor).getBackgroundColor();
      Color foreground = editor.getColorsScheme().getColor(EditorColors.CARET_COLOR);

      JComponent toolbarComponent = toolbar.getComponent();
      toolbarComponent.setBackground(background);
      boolean isEditorVisible = editorComponent != null;
      toolbarComponent.setBorder(new ColoredSideBorder(foreground, foreground, isEditorVisible ? null : foreground, foreground, 1));

      // 'empty space' to the right of toolbar
      JPanel emptyPanel = new JPanel();
      emptyPanel.setOpaque(false);

      JPanel toolbarPanel = new JPanel(new BorderLayout());
      toolbarPanel.setOpaque(false);
      toolbarPanel.add(toolbarComponent, BorderLayout.WEST);
      toolbarPanel.add(emptyPanel, BorderLayout.CENTER);

      add(toolbarPanel, BorderLayout.NORTH);
      if (editorComponent != null) add(editorComponent, BorderLayout.CENTER);

      // transfer clicks into editor
      MouseAdapter listener = new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
          transferEvent(e, editor);
        }

        @Override
        public void mouseClicked(MouseEvent e) {
          transferEvent(e, editor);
        }

        public void mouseReleased(MouseEvent e) {
          transferEvent(e, editor);
        }
      };
      emptyPanel.addMouseListener(listener);
    }

    private static void transferEvent(MouseEvent e, Editor editor) {
      editor.getContentComponent().dispatchEvent(SwingUtilities.convertMouseEvent(e.getComponent(), e, editor.getContentComponent()));
    }

    public int getEditorTextOffset() {
      return myEditorLeftBorder;
    }
  }
}
