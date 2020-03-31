// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.codeInsight.hint.DocumentFragmentTooltipRenderer;
import com.intellij.codeInsight.hint.TooltipController;
import com.intellij.codeInsight.hint.TooltipGroup;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.util.Key;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * This class implements showing a preview of text in a collapsed fold region on mouse hover.
 */
public class FoldingPopupManager implements EditorMouseListener, EditorMouseMotionListener {
  private static final Key<Boolean> DISABLED = Key.create("FoldingPopupManager.disabled");
  private static final TooltipGroup FOLDING_TOOLTIP_GROUP = new TooltipGroup("FOLDING_TOOLTIP_GROUP", 10);
  private static final int TOOLTIP_DELAY_MS = 300;

  private final Alarm myAlarm;

  public static void disableForEditor(@NotNull Editor editor) {
    editor.putUserData(DISABLED, Boolean.TRUE);
    TooltipController.getInstance().cancelTooltip(FOLDING_TOOLTIP_GROUP, null, true);
  }

  public static void enableForEditor(@NotNull Editor editor) {
    editor.putUserData(DISABLED, null);
  }

  FoldingPopupManager(EditorImpl editor) {
    myAlarm = new Alarm(editor.getDisposable());
    editor.addEditorMouseListener(this);
    editor.addEditorMouseMotionListener(this);
  }

  @Override
  public void mouseMoved(@NotNull EditorMouseEvent e) {
    myAlarm.cancelAllRequests();
    Editor editor = e.getEditor();
    if (editor.getUserData(DISABLED) != null) return;
    if (e.getArea() == EditorMouseEventArea.EDITING_AREA) {
      MouseEvent mouseEvent = e.getMouseEvent();
      Point point = mouseEvent.getPoint();
      FoldRegion fold = ((EditorEx)editor).getFoldingModel().getFoldingPlaceholderAt(point);
      TooltipController controller = TooltipController.getInstance();
      if (fold != null && !fold.shouldNeverExpand()) {
        myAlarm.addRequest(() -> {
          if (editor.getUserData(DISABLED) != null || !editor.getComponent().isShowing() || !fold.isValid() || fold.isExpanded()) return;
          DocumentFragment range = createDocumentFragment(fold);
          Point p = SwingUtilities.convertPoint((Component)mouseEvent.getSource(), point,
                                                editor.getComponent().getRootPane().getLayeredPane());
          controller.showTooltip(editor, p, new DocumentFragmentTooltipRenderer(range), false, FOLDING_TOOLTIP_GROUP);
        }, TOOLTIP_DELAY_MS); }
      else {
        controller.cancelTooltip(FOLDING_TOOLTIP_GROUP, mouseEvent, true);
      }
    }
  }

  @NotNull
  private static DocumentFragment createDocumentFragment(@NotNull FoldRegion fold) {
    EditorImpl editor = (EditorImpl)fold.getEditor();
    Document document = editor.getDocument();
    FoldingGroup group = fold.getGroup();
    int startOffset = fold.getStartOffset();
    int endOffset = fold.getEndOffset();
    if (group != null) {
      int groupEndOffset = editor.getFoldingModel().getEndOffset(group);
      if (editor.offsetToVisualLine(groupEndOffset) == editor.offsetToVisualLine(startOffset)) {
        endOffset = groupEndOffset;
      }
    }
    return new DocumentFragment(document, startOffset, endOffset);
  }

  @Override
  public void mouseExited(@NotNull EditorMouseEvent e) {
    myAlarm.cancelAllRequests();
    if (e.getEditor().getUserData(DISABLED) != null) return;
    TooltipController.getInstance().cancelTooltip(FOLDING_TOOLTIP_GROUP, e.getMouseEvent(), true);
  }

  @Override
  public void mouseDragged(@NotNull EditorMouseEvent e) {}

  @Override
  public void mousePressed(@NotNull EditorMouseEvent e) {}

  @Override
  public void mouseClicked(@NotNull EditorMouseEvent e) {}

  @Override
  public void mouseReleased(@NotNull EditorMouseEvent e) {}

  @Override
  public void mouseEntered(@NotNull EditorMouseEvent e) {}
}
