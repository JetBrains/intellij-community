/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.diff.util.DiffDrawUtil;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.TextDiffType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.HintHint;
import com.intellij.ui.HintListener;
import com.intellij.ui.LightweightHint;
import com.intellij.util.Function;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;

import static com.intellij.diff.util.DiffUtil.getDiffType;
import static com.intellij.diff.util.DiffUtil.getLineCount;

public abstract class LineStatusMarkerPopup {
  @NotNull public final LineStatusTracker myTracker;
  @NotNull public final Editor myEditor;
  @NotNull public final Range myRange;

  public LineStatusMarkerPopup(@NotNull LineStatusTracker tracker, @NotNull Editor editor, @NotNull Range range) {
    myTracker = tracker;
    myEditor = editor;
    myRange = range;
  }

  @NotNull
  protected abstract ActionToolbar buildToolbar(@Nullable Point mousePosition, @NotNull Disposable parentDisposable);

  @NotNull
  protected FileType getFileType() {
    return PlainTextFileType.INSTANCE;
  }

  protected boolean isShowInnerDifferences() {
    return false;
  }


  public void scrollAndShow() {
    if (!myTracker.isValid()) return;
    final Document document = myTracker.getDocument();
    int line = Math.min(myRange.getType() == Range.DELETED ? myRange.getLine2() : myRange.getLine2() - 1, getLineCount(document) - 1);
    final int lastOffset = document.getLineStartOffset(line);
    myEditor.getCaretModel().moveToOffset(lastOffset);
    myEditor.getScrollingModel().scrollToCaret(ScrollType.CENTER);

    showAfterScroll();
  }

  public void showAfterScroll() {
    myEditor.getScrollingModel().runActionOnScrollingFinished(new Runnable() {
      public void run() {
        showHintAt(null);
      }
    });
  }

  public void showHint(@NotNull MouseEvent e) {
    final JComponent comp = (JComponent)e.getComponent(); // shall be EditorGutterComponent, cast is safe.
    final JLayeredPane layeredPane = comp.getRootPane().getLayeredPane();
    final Point point = SwingUtilities.convertPoint(comp, ((EditorEx)myEditor).getGutterComponentEx().getWidth(), e.getY(), layeredPane);
    showHintAt(point);
    e.consume();
  }

  public void showHintAt(@Nullable Point mousePosition) {
    if (!myTracker.isValid()) return;
    final Disposable disposable = Disposer.newDisposable();

    FileType fileType = getFileType();
    List<DiffFragment> wordDiff = computeWordDiff();

    installMasterEditorHighlighters(wordDiff, disposable);
    JComponent editorComponent = createEditorComponent(fileType, wordDiff);

    ActionToolbar toolbar = buildToolbar(mousePosition, disposable);
    toolbar.updateActionsImmediately(); // we need valid ActionToolbar.getPreferredSize() to calc size of popup
    toolbar.setReservePlaceAutoPopupIcon(false);

    PopupPanel popupPanel = new PopupPanel(myEditor, toolbar, editorComponent);

    LightweightHint hint = new LightweightHint(popupPanel);
    HintListener closeListener = new HintListener() {
      public void hintHidden(final EventObject event) {
        Disposer.dispose(disposable);
      }
    };
    hint.addHintListener(closeListener);

    int line = myEditor.getCaretModel().getLogicalPosition().line;
    Point point = HintManagerImpl.getHintPosition(hint, myEditor, new LogicalPosition(line, 0), HintManager.UNDER);
    if (mousePosition != null) { // show right after the nearest line
      int lineHeight = myEditor.getLineHeight();
      int delta = (point.y - mousePosition.y) % lineHeight;
      if (delta < 0) delta += lineHeight;
      point.y = mousePosition.y + delta;
    }
    point.x -= popupPanel.getEditorTextOffset(); // align main editor with the one in popup

    int flags = HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING;
    HintManagerImpl.getInstanceImpl().showEditorHint(hint, myEditor, point, flags, -1, false, new HintHint(myEditor, point));

    if (!hint.isVisible()) {
      closeListener.hintHidden(null);
    }
  }

  @Nullable
  private List<DiffFragment> computeWordDiff() {
    if (!isShowInnerDifferences()) return null;
    if (myRange.getType() != Range.MODIFIED) return null;

    final CharSequence vcsContent = myTracker.getVcsContent(myRange);
    final CharSequence currentContent = myTracker.getCurrentContent(myRange);

    return BackgroundTaskUtil.tryComputeFast(new Function<ProgressIndicator, List<DiffFragment>>() {
      @Override
      public List<DiffFragment> fun(ProgressIndicator indicator) {
        return ByWord.compare(vcsContent, currentContent, ComparisonPolicy.DEFAULT, indicator);
      }
    }, Registry.intValue("diff.status.tracker.byword.delay"));
  }

  private void installMasterEditorHighlighters(@Nullable List<DiffFragment> wordDiff, @NotNull Disposable parentDisposable) {
    if (wordDiff == null) return;
    final List<RangeHighlighter> highlighters = new ArrayList<>();

    int currentStartShift = myTracker.getCurrentTextRange(myRange).getStartOffset();
    for (DiffFragment fragment : wordDiff) {
      int currentStart = currentStartShift + fragment.getStartOffset2();
      int currentEnd = currentStartShift + fragment.getEndOffset2();
      TextDiffType type = getDiffType(fragment);

      highlighters.addAll(DiffDrawUtil.createInlineHighlighter(myEditor, currentStart, currentEnd, type));
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

  @Nullable
  private EditorFragmentComponent createEditorComponent(@Nullable FileType fileType, @Nullable List<DiffFragment> wordDiff) {
    if (myRange.getType() == Range.INSERTED) return null;

    EditorEx uEditor = (EditorEx)EditorFactory.getInstance().createViewer(myTracker.getVcsDocument(), myTracker.getProject());
    uEditor.setColorsScheme(myEditor.getColorsScheme());

    DiffUtil.setEditorCodeStyle(myTracker.getProject(), uEditor, fileType);

    EditorHighlighterFactory highlighterFactory = EditorHighlighterFactory.getInstance();
    uEditor.setHighlighter(highlighterFactory.createEditorHighlighter(myTracker.getProject(), getFileName(myTracker.getDocument())));

    if (wordDiff != null) {
      int vcsStartShift = myTracker.getVcsTextRange(myRange).getStartOffset();
      for (DiffFragment fragment : wordDiff) {
        int vcsStart = vcsStartShift + fragment.getStartOffset1();
        int vcsEnd = vcsStartShift + fragment.getEndOffset1();
        TextDiffType type = getDiffType(fragment);

        DiffDrawUtil.createInlineHighlighter(uEditor, vcsStart, vcsEnd, type);
      }
    }

    EditorFragmentComponent fragmentComponent =
      EditorFragmentComponent.createEditorFragmentComponent(uEditor, myRange.getVcsLine1(), myRange.getVcsLine2(), false, false);

    EditorFactory.getInstance().releaseEditor(uEditor);

    return fragmentComponent;
  }

  private static String getFileName(@NotNull Document document) {
    final VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    if (file == null) return "";
    return file.getName();
  }

  private static class PopupPanel extends JPanel {
    @Nullable private final JComponent myEditorComponent;

    public PopupPanel(@NotNull final Editor editor,
                      @NotNull ActionToolbar toolbar,
                      @Nullable JComponent editorComponent) {
      super(new BorderLayout());
      setOpaque(false);

      myEditorComponent = editorComponent;
      boolean isEditorVisible = myEditorComponent != null;

      Color background = ((EditorEx)editor).getBackgroundColor();
      Color borderColor = editor.getColorsScheme().getColor(EditorColors.SELECTED_TEARLINE_COLOR);

      JComponent toolbarComponent = toolbar.getComponent();
      toolbarComponent.setBackground(background);
      toolbarComponent.setBorder(null);

      JComponent toolbarPanel = JBUI.Panels.simplePanel(toolbarComponent);
      toolbarPanel.setBackground(background);
      Border outsideToolbarBorder = JBUI.Borders.customLine(borderColor, 1, 1, isEditorVisible ? 0 : 1, 1);
      Border insideToolbarBorder = JBUI.Borders.empty(1, 5, 1, 5);
      toolbarPanel.setBorder(BorderFactory.createCompoundBorder(outsideToolbarBorder, insideToolbarBorder));

      if (myEditorComponent != null) {
        // default border of EditorFragmentComponent is replaced here with our own.
        Border outsideEditorBorder = JBUI.Borders.customLine(borderColor, 1);
        Border insideEditorBorder = JBUI.Borders.empty(2);
        myEditorComponent.setBorder(BorderFactory.createCompoundBorder(outsideEditorBorder, insideEditorBorder));
      }

      // 'empty space' to the right of toolbar
      JPanel emptyPanel = new JPanel();
      emptyPanel.setOpaque(false);
      emptyPanel.setPreferredSize(new Dimension());

      JPanel topPanel = new JPanel(new BorderLayout());
      topPanel.setOpaque(false);
      topPanel.add(toolbarPanel, BorderLayout.WEST);
      topPanel.add(emptyPanel, BorderLayout.CENTER);

      add(topPanel, BorderLayout.NORTH);
      if (myEditorComponent != null) add(myEditorComponent, BorderLayout.CENTER);

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
      return 3; // myEditorComponent.getInsets().left
    }
  }
}
