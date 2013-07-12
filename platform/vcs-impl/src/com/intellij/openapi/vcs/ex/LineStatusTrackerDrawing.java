/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diff.DiffColors;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.markup.ActiveGutterRenderer;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.LineMarkerRenderer;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vcs.actions.ShowNextChangeMarkerAction;
import com.intellij.openapi.vcs.actions.ShowPrevChangeMarkerAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredSideBorder;
import com.intellij.ui.HintHint;
import com.intellij.ui.HintListener;
import com.intellij.ui.LightweightHint;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.EventObject;
import java.util.List;

/**
 * @author irengrig
 */
public class LineStatusTrackerDrawing {
  private LineStatusTrackerDrawing() {
  }

  static TextAttributes getAttributesFor(final Range range) {
    final Color stripeColor = getDiffColor(range, false);
    final TextAttributes textAttributes = new TextAttributes(null, stripeColor, null, EffectType.BOXED, Font.PLAIN);
    textAttributes.setErrorStripeColor(stripeColor);
    return textAttributes;
  }

  private static void paintGutterFragment(final Editor editor, final Graphics g, final Rectangle r, final Color stripeColor) {
    final EditorGutterComponentEx gutter = ((EditorEx)editor).getGutterComponentEx();
    g.setColor(stripeColor);

    final int endX = gutter.getWhitespaceSeparatorOffset();
    final int x = r.x + r.width - 5;
    final int width = endX - x;
    if (r.height > 0) {
      g.fillRect(x, r.y, width, r.height);
      g.setColor(gutter.getOutlineColor(false));
      UIUtil.drawLine(g, x, r.y, x + width, r.y);
      UIUtil.drawLine(g, x, r.y, x, r.y + r.height - 1);
      UIUtil.drawLine(g, x, r.y + r.height - 1, x + width, r.y + r.height - 1);
    }
    else {
      final int[] xPoints = new int[]{x,
        x,
        x + width - 1};
      final int[] yPoints = new int[]{r.y - 4,
        r.y + 4,
        r.y};
      g.fillPolygon(xPoints, yPoints, 3);

      g.setColor(gutter.getOutlineColor(false));
      g.drawPolygon(xPoints, yPoints, 3);
    }
  }

  @Nullable
  private static Color brighter(final Color color) {
    if (color == null) {
      return null;
    }

    final float[] hsbStripeColor = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);

    if (hsbStripeColor[1] < 0.02f) {
      // color is grey
      hsbStripeColor[2] = Math.min(1.0f, hsbStripeColor[2] * 1.3f);
    } else {
      // let's decrease color saturation
      hsbStripeColor[1] *= 0.3;

      // max brightness
      hsbStripeColor[2] = 1.0f;
    }
    return Color.getHSBColor(hsbStripeColor[0], hsbStripeColor[1], hsbStripeColor[2]);
  }

  public static LineMarkerRenderer createRenderer(final Range range, final LineStatusTracker tracker) {
    return new ActiveGutterRenderer() {
      public void paint(final Editor editor, final Graphics g, final Rectangle r) {
        paintGutterFragment(editor, g, r, getDiffColor(range, true));
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
        return  e.getX() > gutter.getLineMarkerAreaOffset() + gutter.getIconsAreaWidth();
      }
    };
  }

  public static void showActiveHint(final Range range, final Editor editor, final Point point, final LineStatusTracker tracker) {

    final DefaultActionGroup group = new DefaultActionGroup();

    final AnAction globalShowNextAction = ActionManager.getInstance().getAction("VcsShowNextChangeMarker");
    final AnAction globalShowPrevAction = ActionManager.getInstance().getAction("VcsShowPrevChangeMarker");

    final ShowPrevChangeMarkerAction localShowPrevAction = new ShowPrevChangeMarkerAction(tracker.getPrevRange(range), tracker, editor);
    final ShowNextChangeMarkerAction localShowNextAction = new ShowNextChangeMarkerAction(tracker.getNextRange(range), tracker, editor);

    final JComponent editorComponent = editor.getComponent();

    localShowNextAction.registerCustomShortcutSet(localShowNextAction.getShortcutSet(), editorComponent);
    localShowPrevAction.registerCustomShortcutSet(localShowPrevAction.getShortcutSet(), editorComponent);

    group.add(localShowPrevAction);
    group.add(localShowNextAction);

    localShowNextAction.copyFrom(globalShowNextAction);
    localShowPrevAction.copyFrom(globalShowPrevAction);

    final RollbackLineStatusRangeAction rollback = new RollbackLineStatusRangeAction(tracker, range, editor);
    EmptyAction.setupAction(rollback, IdeActions.CHANGES_VIEW_ROLLBACK, editorComponent);
    group.add(rollback);

    group.add(new ShowLineStatusRangeDiffAction(tracker, range, editor));
    group.add(new CopyLineStatusRangeAction(tracker, range));

    @SuppressWarnings("unchecked")
    final List<AnAction> actionList = (List<AnAction>)editorComponent.getClientProperty(AnAction.ourClientProperty);

    final JComponent toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.FILEHISTORY_VIEW_TOOLBAR, group, true).getComponent();

    final Color background = ((EditorEx)editor).getBackgroundColor();
    final Color foreground = editor.getColorsScheme().getColor(EditorColors.CARET_COLOR);
    toolbar.setBackground(background);

    toolbar.setBorder(new ColoredSideBorder(foreground, foreground, (range.getType() != Range.INSERTED) ? null : foreground, foreground, 1));

    final JPanel component = new JPanel(new BorderLayout());
    component.setOpaque(false);

    final JPanel toolbarPanel = new JPanel(new BorderLayout());
    toolbarPanel.setOpaque(false);
    toolbarPanel.add(toolbar, BorderLayout.WEST);
    JPanel emptyPanel = new JPanel();
    emptyPanel.setOpaque(false);
    toolbarPanel.add(emptyPanel, BorderLayout.CENTER);
    MouseAdapter listener = new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        editor.getContentComponent().dispatchEvent(SwingUtilities.convertMouseEvent(e.getComponent(), e, editor.getContentComponent()));
      }

      @Override
      public void mouseClicked(MouseEvent e) {
        editor.getContentComponent().dispatchEvent(SwingUtilities.convertMouseEvent(e.getComponent(), e, editor.getContentComponent()));
      }

      public void mouseReleased(final MouseEvent e) {
        editor.getContentComponent().dispatchEvent(SwingUtilities.convertMouseEvent(e.getComponent(), e, editor.getContentComponent()));
      }
    };
    emptyPanel.addMouseListener(listener);

    component.add(toolbarPanel, BorderLayout.NORTH);

    if (range.getType() != Range.INSERTED) {
      final DocumentEx doc = (DocumentEx) tracker.getUpToDateDocument();
      final EditorEx uEditor = (EditorEx)EditorFactory.getInstance().createViewer(doc, tracker.getProject());
      final EditorHighlighter highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(tracker.getProject(), getFileName(tracker.getDocument()));
      uEditor.setHighlighter(highlighter);

      final EditorFragmentComponent editorFragmentComponent =
        EditorFragmentComponent.createEditorFragmentComponent(uEditor, range.getUOffset1(), range.getUOffset2(), false, false);

      component.add(editorFragmentComponent, BorderLayout.CENTER);

      EditorFactory.getInstance().releaseEditor(uEditor);
    }

    final LightweightHint lightweightHint = new LightweightHint(component);
    HintListener closeListener = new HintListener() {
      public void hintHidden(final EventObject event) {
        actionList.remove(rollback);
        actionList.remove(localShowPrevAction);
        actionList.remove(localShowNextAction);
      }
    };
    lightweightHint.addHintListener(closeListener);

    HintManagerImpl.getInstanceImpl().showEditorHint(lightweightHint, editor, point, HintManagerImpl.HIDE_BY_ANY_KEY | HintManagerImpl.HIDE_BY_TEXT_CHANGE |
                                                                             HintManagerImpl.HIDE_BY_SCROLLING,
                                                                             -1, false, new HintHint(editor, point));

    if (!lightweightHint.isVisible()) {
      closeListener.hintHidden(null);
    }
    
  }

  private static String getFileName(final Document document) {
    final VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    if (file == null) return "";
    return file.getName();
  }

  public static void moveToRange(final Range range, final Editor editor, final LineStatusTracker tracker) {
    final Document document = tracker.getDocument();
    final int lastOffset = document.getLineStartOffset(Math.min(range.getOffset2(), document.getLineCount() - 1));
    editor.getCaretModel().moveToOffset(lastOffset);
    editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);

    editor.getScrollingModel().runActionOnScrollingFinished(new Runnable() {
      public void run() {
        Point p = editor.visualPositionToXY(editor.offsetToVisualPosition(lastOffset));
        final JComponent editorComponent = editor.getContentComponent();
        final JLayeredPane layeredPane = editorComponent.getRootPane().getLayeredPane();
        p = SwingUtilities.convertPoint(editorComponent, 0, p.y, layeredPane);
        showActiveHint(range, editor, p, tracker);
      }
    });
  }

  private static Color getDiffColor(Range range, boolean gutter) {
    final EditorColorsScheme globalScheme = EditorColorsManager.getInstance().getGlobalScheme();
    switch (range.getType()) {
      case Range.INSERTED:
        return gutter ? globalScheme.getColor(EditorColors.ADDED_LINES_COLOR)
                      : globalScheme.getAttributes(DiffColors.DIFF_INSERTED).getErrorStripeColor();
      case Range.DELETED:
        return globalScheme.getAttributes(DiffColors.DIFF_DELETED).getErrorStripeColor();
      case Range.MODIFIED:
        return gutter ? globalScheme.getColor(EditorColors.MODIFIED_LINES_COLOR)
                      : globalScheme.getAttributes(DiffColors.DIFF_MODIFIED).getErrorStripeColor();
      default:
        assert false;
        return null;
    }
  }
}
