// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.status;

import com.intellij.ide.util.EditorGotoLineNumberDialog;
import com.intellij.ide.util.GotoLineNumberDialog;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.ui.UIBundle;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class PositionPanel extends EditorBasedWidget
  implements StatusBarWidget.Multiframe, StatusBarWidget.TextPresentation,
             CaretListener, SelectionListener, BulkAwareDocumentListener.Simple, PropertyChangeListener {

  public static final Key<Object> DISABLE_FOR_EDITOR = new Key<>("positionPanel.disableForEditor");

  public static final String SPACE = "     ";
  public static final String SEPARATOR = ":";

  private static final int CHAR_COUNT_SYNC_LIMIT = 500_000;
  private static final String CHAR_COUNT_UNKNOWN = "...";

  private Alarm myAlarm;
  private MergingUpdateQueue myQueue;
  private CodePointCountTask myCountTask;

  private String myText;

  public PositionPanel(@NotNull Project project) {
    super(project);
  }

  @Override
  public void selectionChanged(@NotNull FileEditorManagerEvent event) {
    updatePosition(getEditor());
  }

  @Override
  @NotNull
  public String ID() {
    return StatusBar.StandardWidgets.POSITION_PANEL;
  }

  @Override
  public StatusBarWidget copy() {
    return new PositionPanel(getProject());
  }

  @Override
  public WidgetPresentation getPresentation() {
    return this;
  }

  @Override
  @NotNull
  public String getText() {
    return myText == null ? "" : myText;
  }

  @Override
  public float getAlignment() {
    return Component.CENTER_ALIGNMENT;
  }

  @Override
  public String getTooltipText() {
    String toolTip = UIBundle.message("go.to.line.command.name");
    String shortcut = getShortcutText();

    if (!Registry.is("ide.helptooltip.enabled") && StringUtil.isNotEmpty(shortcut)) {
      return toolTip + " (" + shortcut + ")";
    }
    return toolTip;
  }

  @Override
  public String getShortcutText() {
    return KeymapUtil.getFirstKeyboardShortcutText("GotoLine");
  }

  @Override
  public Consumer<MouseEvent> getClickConsumer() {
    return mouseEvent -> {
      Project project = getProject();
      Editor editor = getFocusedEditor();
      if (editor == null) return;

      CommandProcessor.getInstance().executeCommand(
        project,
        () -> {
          GotoLineNumberDialog dialog = new EditorGotoLineNumberDialog(project, editor);
          dialog.show();
          IdeDocumentHistory.getInstance(project).includeCurrentCommandAsNavigation();
        },
        UIBundle.message("go.to.line.command.name"),
        null
      );
    };
  }

  @Override
  public void install(@NotNull StatusBar statusBar) {
    super.install(statusBar);
    myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
    myQueue = new MergingUpdateQueue("PositionPanel", 100, true, null, this);
    EditorEventMulticaster multicaster = EditorFactory.getInstance().getEventMulticaster();
    multicaster.addCaretListener(this, this);
    multicaster.addSelectionListener(this, this);
    multicaster.addDocumentListener(this, this);
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener(SWING_FOCUS_OWNER_PROPERTY, this);
    Disposer.register(this,
                      () -> KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener(SWING_FOCUS_OWNER_PROPERTY,
                                                                                                               this));
  }

  @Override
  public void selectionChanged(@NotNull final SelectionEvent e) {
    Editor editor = e.getEditor();
    if (isFocusedEditor(editor)) updatePosition(editor);
  }

  @Override
  public void caretPositionChanged(@NotNull final CaretEvent e) {
    Editor editor = e.getEditor();
    // When multiple carets exist in editor, we don't show information about caret positions
    if (editor.getCaretModel().getCaretCount() == 1 && isFocusedEditor(editor)) updatePosition(editor);
  }

  @Override
  public void caretAdded(@NotNull CaretEvent e) {
    updatePosition(e.getEditor());
  }

  @Override
  public void caretRemoved(@NotNull CaretEvent e) {
    updatePosition(e.getEditor());
  }

  @Override
  public void afterDocumentChange(@NotNull Document document) {
    EditorFactory.getInstance().editors(document)
      .filter(this::isFocusedEditor)
      .findFirst()
      .ifPresent(this::updatePosition);
  }

  private boolean isFocusedEditor(Editor editor) {
    Component focusOwner = getFocusedComponent();
    return focusOwner == editor.getContentComponent();
  }

  private void updatePosition(final Editor editor) {
    myQueue.queue(Update.create(this, () -> {
      boolean empty = editor == null || DISABLE_FOR_EDITOR.isIn(editor);
      if (!empty && !isOurEditor(editor)) return;

      String newText = empty ? "" : getPositionText(editor);
      if (newText.equals(myText)) return;

      myText = newText;
      if (myStatusBar != null) {
        myStatusBar.updateWidget(ID());
      }
    }));
  }

  private void updateTextWithCodePointCount(int codePointCount) {
    if (myText != null) {
      myText = myText.replace(CHAR_COUNT_UNKNOWN, Integer.toString(codePointCount));
      if (myStatusBar != null) {
        myStatusBar.updateWidget(ID());
      }
    }
  }

  private String getPositionText(@NotNull Editor editor) {
    myCountTask = null;
    if (!editor.isDisposed() && !myAlarm.isDisposed()) {
      StringBuilder message = new StringBuilder();

      SelectionModel selectionModel = editor.getSelectionModel();
      int caretCount = editor.getCaretModel().getCaretCount();
      if (caretCount > 1) {
        message.append(UIBundle.message("position.panel.caret.count", caretCount));
      }
      else {
        LogicalPosition caret = editor.getCaretModel().getLogicalPosition();
        message.append(caret.line + 1).append(SEPARATOR).append(caret.column + 1);
        if (selectionModel.hasSelection()) {
          int selectionStart = selectionModel.getSelectionStart();
          int selectionEnd = selectionModel.getSelectionEnd();
          if (selectionEnd > selectionStart) {
            message.append(" (");
            CodePointCountTask countTask = new CodePointCountTask(editor.getDocument().getImmutableCharSequence(),
                                                                  selectionStart, selectionEnd);
            if (countTask.isQuick()) {
              int charCount = countTask.calculate();
              message.append(charCount).append(' ').append(UIBundle.message("position.panel.selected.chars.count", charCount));
            }
            else {
              message.append(CHAR_COUNT_UNKNOWN).append(' ').append(UIBundle.message("position.panel.selected.chars.count", 2));
              myCountTask = countTask;
              myAlarm.cancelAllRequests();
              myAlarm.addRequest(countTask, 0);
            }
            int selectionStartLine = editor.getDocument().getLineNumber(selectionStart);
            int selectionEndLine = editor.getDocument().getLineNumber(selectionEnd);
            if (selectionEndLine > selectionStartLine) {
              message.append(", ");
              message.append(UIBundle.message("position.panel.selected.line.breaks.count", selectionEndLine - selectionStartLine));
            }
            message.append(")");
          }
        }
      }

      return message.toString();
    }
    else {
      return "";
    }
  }

  @Override
  public void propertyChange(PropertyChangeEvent e) {
    updatePosition(getFocusedEditor());
  }

  private final class CodePointCountTask implements Runnable {
    private final CharSequence text;
    private final int startOffset;
    private final int endOffset;

    private CodePointCountTask(CharSequence text, int startOffset, int endOffset) {
      this.text = text;
      this.startOffset = startOffset;
      this.endOffset = endOffset;
    }

    private boolean isQuick() {
      return endOffset - startOffset < CHAR_COUNT_SYNC_LIMIT;
    }

    private int calculate() {
      return Character.codePointCount(text, startOffset, endOffset);
    }

    @Override
    public void run() {
      int count = calculate();
      SwingUtilities.invokeLater(() -> {
        if (this == myCountTask) {
          updateTextWithCodePointCount(count);
          myCountTask = null;
        }
      });
    }
  }
}
