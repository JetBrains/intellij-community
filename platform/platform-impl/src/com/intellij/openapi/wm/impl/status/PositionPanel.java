/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.ide.*;
import com.intellij.ide.util.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.*;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.ex.*;
import com.intellij.openapi.project.*;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.wm.*;
import com.intellij.ui.*;
import com.intellij.util.*;
import org.jetbrains.annotations.*;

import java.awt.*;
import java.awt.event.*;

public class PositionPanel implements StatusBarWidget, StatusBarWidget.TextPresentation, CaretListener {
  private String myText;
  private StatusBar myStatusBar;

  public PositionPanel(@NotNull final Project project) {
    project.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerAdapter() {
      @Override
      public void fileOpened(@NotNull final FileEditorManager source, @NotNull final VirtualFile file) {
        final Editor editor = source.getSelectedTextEditor();
        if (editor != null) updatePosition(editor);
      }
    });
  }

  @NotNull
  public String ID() {
    return "Position";
  }

  public WidgetPresentation getPresentation(@NotNull final Type type) {
    return this;
  }

  @NotNull
  public String getText() {
    return myText == null ? "" : myText;
  }

  @NotNull
  public String getMaxPossibleText() {
    return "#############";
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

  @Nullable
  private Editor getEditor() {
    final Project project = getProject();
    if (project != null) {
      final FileEditorManager manager = FileEditorManager.getInstance(project);
      return manager.getSelectedTextEditor();
    }

    return null;
  }

  @Nullable
  private Project getProject() {
    return PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext((Component)myStatusBar));
  }

  public void dispose() {
    EditorFactory.getInstance().getEventMulticaster().removeCaretListener(this);
    myStatusBar = null;
  }

  public void install(@NotNull StatusBar statusBar) {
    EditorFactory.getInstance().getEventMulticaster().addCaretListener(this);
    myStatusBar = statusBar;
  }

  private static void appendLogicalPosition(LogicalPosition caret, StringBuilder message) {
    message.append(caret.line + 1);
    message.append(":");
    message.append(caret.column + 1);
  }

  public void caretPositionChanged(final CaretEvent e) {
    updatePosition(e.getEditor());
  }

  private void updatePosition(final Editor editor) {
    if (editor == null) return;
    myText = getPositionText(editor);
    myStatusBar.updateWidget(ID());
  }

  private String getPositionText(Editor editor) {
    if (!editor.isDisposed() && myStatusBar != null) {
      StringBuilder message = new StringBuilder();

      SelectionModel selectionModel = editor.getSelectionModel();
      if (selectionModel.hasBlockSelection()) {
        LogicalPosition start = selectionModel.getBlockStart();
        LogicalPosition end = selectionModel.getBlockEnd();
        appendLogicalPosition(start, message);
        message.append("-");
        appendLogicalPosition(new LogicalPosition(Math.abs(end.line - start.line), Math.abs(end.column - start.column) - 1), message);
      }
      else {
        LogicalPosition caret = editor.getCaretModel().getLogicalPosition();

        appendLogicalPosition(caret, message);
        if (selectionModel.hasSelection()) {
          int len = Math.abs(selectionModel.getSelectionStart() - selectionModel.getSelectionEnd());
          message.append("/");
          message.append(len);
        }
      }

      return message.toString();
    }
    else {
      return "";
    }
  }
}
