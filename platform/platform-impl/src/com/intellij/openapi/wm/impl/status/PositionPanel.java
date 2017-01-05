/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl.status;

import com.intellij.ide.util.GotoLineNumberDialog;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.ui.UIBundle;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

public class PositionPanel extends EditorBasedWidget implements StatusBarWidget.Multiframe, StatusBarWidget.TextPresentation, CaretListener, SelectionListener {
  private static final int CHAR_COUNT_SYNC_LIMIT = 500_000;
  private static final String CHAR_COUNT_UNKNOWN = "...";

  private final Alarm myAlarm;
  private CodePointCountTask myCountTask;

  private String myText;

  public PositionPanel(@NotNull final Project project) {
    super(project);
    myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, project);
  }

  @Override
  public void selectionChanged(@NotNull FileEditorManagerEvent event) {
    updatePosition(getEditor());
  }

  @NotNull
  public String ID() {
    return "Position";
  }

  @Override
  public StatusBarWidget copy() {
    return new PositionPanel(getProject());
  }

  public WidgetPresentation getPresentation(@NotNull final PlatformType type) {
    return this;
  }

  @NotNull
  public String getText() {
    return myText == null ? "" : myText;
  }

  @NotNull
  public String getMaxPossibleText() {
    return "0000000000000";
  }

  @Override
  public float getAlignment() {
    return Component.CENTER_ALIGNMENT;
  }

  public String getTooltipText() {
    return UIBundle.message("go.to.line.command.double.click");
  }

  public Consumer<MouseEvent> getClickConsumer() {
    return mouseEvent -> {
      final Project project = getProject();
      if (project == null) return;
      final Editor editor = getEditor();
      if (editor == null) return;
      final CommandProcessor processor = CommandProcessor.getInstance();
      processor.executeCommand(
        project, () -> {
          final GotoLineNumberDialog dialog = new GotoLineNumberDialog(project, editor);
          dialog.show();
          IdeDocumentHistory.getInstance(project).includeCurrentCommandAsNavigation();
        },
        UIBundle.message("go.to.line.command.name"),
        null
      );
    };
  }

  public void install(@NotNull StatusBar statusBar) {
    super.install(statusBar);
    final EditorEventMulticaster multicaster = EditorFactory.getInstance().getEventMulticaster();
    multicaster.addCaretListener(this, this);
    multicaster.addSelectionListener(this, this);
  }

  @Override
  public void selectionChanged(final SelectionEvent e) {
    updatePosition(e.getEditor());
  }

  public void caretPositionChanged(final CaretEvent e) {
    Editor editor = e.getEditor();
    // When multiple carets exist in editor, we don't show information about caret positions
    if (editor.getCaretModel().getCaretCount() == 1) updatePosition(editor);
  }

  @Override
  public void caretAdded(CaretEvent e) {
    updatePosition(e.getEditor());
  }

  @Override
  public void caretRemoved(CaretEvent e) {
    updatePosition(e.getEditor());
  }

  private void updatePosition(final Editor editor) {
    if (editor == null) {
      myText = "";
    }
    else {
      if (!isOurEditor(editor)) return;
      myText = getPositionText(editor);
    }
    if (myStatusBar != null) {
      myStatusBar.updateWidget(ID());
    }
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
    if (!editor.isDisposed() && myStatusBar != null) {
      StringBuilder message = new StringBuilder();

      SelectionModel selectionModel = editor.getSelectionModel();
      int caretCount = editor.getCaretModel().getCaretCount();
      if (caretCount > 1) {
        message.append(UIBundle.message("position.panel.caret.count", caretCount));
      }
      else {
        if (selectionModel.hasSelection()) {
          int selectionStart = selectionModel.getSelectionStart();
          int selectionEnd = selectionModel.getSelectionEnd();
          if (selectionEnd > selectionStart) {
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
            message.append("     ");
          }
        }
        LogicalPosition caret = editor.getCaretModel().getLogicalPosition();
        message.append(caret.line + 1).append(":").append(caret.column + 1);
      }

      return message.toString();
    }
    else {
      return "";
    }
  }

  private class CodePointCountTask implements Runnable {
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
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(() -> {
        if (this == myCountTask) {
          updateTextWithCodePointCount(count);
          myCountTask = null;
        }
      });
    }
  }
}
