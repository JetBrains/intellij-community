// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.i18n.folding;

import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.editor.impl.FoldingPopupManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.HintHint;
import com.intellij.ui.LightweightHint;
import com.intellij.util.Alarm;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.util.Arrays;

import static com.intellij.codeInsight.hint.HintManager.*;
import static com.intellij.openapi.actionSystem.IdeActions.ACTION_EDIT_PROPERTY_VALUE;
import static com.intellij.openapi.actionSystem.IdeActions.ACTION_EXPAND_REGION;

public class EditPropertyValueTooltipManager implements EditorMouseListener, CaretListener {
  private static final Key<Boolean> INITIALIZED = Key.create("PropertyEditTooltipManager");
  private static final long TOOLTIP_DELAY_MS = 500;

  private final Alarm myAlarm = new Alarm();

  public static void initializeForDocument(@NotNull Document document) {
    for (Editor editor : EditorFactory.getInstance().getEditors(document)) {
      initializeForEditor(editor);
    }
  }

  private static void initializeForEditor(@NotNull Editor editor) {
    if (editor.getUserData(INITIALIZED) != null) return;
    editor.putUserData(INITIALIZED, Boolean.TRUE);
    new EditPropertyValueTooltipManager(editor);
  }

  private EditPropertyValueTooltipManager(@NotNull Editor editor) {
    editor.getCaretModel().addCaretListener(this);
    editor.addEditorMouseListener(this);
  }

  @Override
  public void mouseReleased(@NotNull EditorMouseEvent event) {
    Editor editor = event.getEditor();
    if (shouldShowTooltip(editor)) {
      event.consume();
      myAlarm.cancelAllRequests();
      showTooltip(editor);
    }
  }

  @Override
  public void caretPositionChanged(@NotNull CaretEvent event) {
    myAlarm.cancelAllRequests();
    Editor editor = event.getEditor();
    if (shouldShowTooltip(editor)) myAlarm.addRequest(() -> showTooltip(editor), TOOLTIP_DELAY_MS);
  }

  private static boolean shouldShowTooltip(@NotNull Editor editor) {
    return EditPropertyValueAction.isEnabled(editor);
  }

  private static void showTooltip(@NotNull Editor editor) {
    String hintText = StringUtil.join(Arrays.asList(createActionText(ACTION_EXPAND_REGION, "expand"),
                                                    createActionText(ACTION_EDIT_PROPERTY_VALUE, "edit")),
                                      "&nbsp;&nbsp;&nbsp;&nbsp;");
    if (hintText.isEmpty()) return;
    JComponent component = HintUtil.createInformationLabel("<html>" + hintText, new HyperlinkListener() {
      @Override
      public void hyperlinkUpdate(HyperlinkEvent e) {
        if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED) return;
        String actionId = null;
        switch (e.getDescription()) {
          case "expand": actionId = ACTION_EXPAND_REGION; break;
          case "edit": actionId = ACTION_EDIT_PROPERTY_VALUE; break;
        }
        if (actionId != null) {
          AnAction action = ActionManager.getInstance().getAction(actionId);
          if (action != null) {
            ActionUtil.invokeAction(action, editor.getContentComponent(), ActionPlaces.UNKNOWN, e.getInputEvent(), null);
          }
        }
      }
    }, null, null);
    showTooltip(editor, component, false);
  }

  @Nullable
  private static String createActionText(@NotNull String actionId, @NotNull String href) {
    AnAction action = ActionManager.getInstance().getAction(actionId);
    if (action == null) return null;
    String text = action.getTemplateText();
    if (text == null) return null;
    StringBuilder b = new StringBuilder().append("<a href='").append(href).append("'>").append(text).append("</a> <span style='color:#");
    UIUtil.appendColor(UIUtil.getContextHelpForeground(), b);
    return b.append("'>").append(KeymapUtil.getFirstKeyboardShortcutText(action)).append("</span>").toString();
  }

  public static LightweightHint showTooltip(@NotNull Editor editor, @NotNull JComponent component, boolean tenacious) {
    if (editor.isDisposed()) return null;
    FoldRegion foldRegion = editor.getFoldingModel().getCollapsedRegionAtOffset(editor.getCaretModel().getOffset());
    if (foldRegion == null) return null;
    JComponent editorComponent = editor.getContentComponent();
    JRootPane rootPane = editorComponent.getRootPane();
    if (rootPane == null) return null;
    Point start = editor.offsetToXY(foldRegion.getStartOffset(), true, false);
    Point end = editor.offsetToXY(foldRegion.getEndOffset(), false, true);
    Point relativePoint = new Point((start.x + end.x) / 2, start.y);
    Point point = SwingUtilities.convertPoint(editorComponent, relativePoint, rootPane.getLayeredPane());
    LightweightHint hint = new LightweightHint(component);
    HintHint hintHint = HintManagerImpl.createHintHint(editor, point, hint, ABOVE).setShowImmediately(true);
    int flags = HIDE_BY_TEXT_CHANGE | HIDE_BY_SCROLLING;
    if (tenacious) {
      hintHint.setExplicitClose(true);
    }
    else {
      flags |= HIDE_BY_ANY_KEY;
    }
    FoldingPopupManager.disableForEditor(editor);
    hint.addHintListener(e -> FoldingPopupManager.enableForEditor(editor));
    HintManagerImpl.getInstanceImpl().showEditorHint(hint, editor, point, flags, 0, false, hintHint);
    return hint;
  }
}
