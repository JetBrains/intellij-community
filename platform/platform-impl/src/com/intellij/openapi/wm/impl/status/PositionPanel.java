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
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.ui.UIBundle;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.MouseEvent;

public class PositionPanel extends EditorBasedWidget implements StatusBarWidget.Multiframe, StatusBarWidget.TextPresentation, CaretListener, SelectionListener {
  private String myText;

  public PositionPanel(@NotNull final Project project) {
    super(project);
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
    return new Consumer<MouseEvent>() {
      public void consume(MouseEvent mouseEvent) {
        final Project project = getProject();
        if (project == null) return;
        final Editor editor = getEditor();
        if (editor == null) return;
        final CommandProcessor processor = CommandProcessor.getInstance();
        processor.executeCommand(
          project, new Runnable() {
            public void run() {
              final GotoLineNumberDialog dialog = new GotoLineNumberDialog(project, editor);
              dialog.show();
              IdeDocumentHistory.getInstance(project).includeCurrentCommandAsNavigation();
            }
          },
          UIBundle.message("go.to.line.command.name"),
          null
        );
      }
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
    updatePosition(e.getEditor());
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

  private String getPositionText(@NotNull Editor editor) {
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
            message.append(UIBundle.message("position.panel.selected.chars.count", selectionEnd - selectionStart));
            int selectionStartLine = editor.getDocument().getLineNumber(selectionStart);
            int selectionEndLine = editor.getDocument().getLineNumber(selectionEnd);
            if (selectionEndLine > selectionStartLine) {
              message.append(", ");
              message.append(UIBundle.message("position.panel.selected.lines.count", selectionEndLine - selectionStartLine + 1));
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
}
