/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.ide.util.EditorGotoLineNumberDialog;
import com.intellij.ide.util.GotoLineNumberDialog;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.DocumentBulkUpdateListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.ui.UIBundle;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class PositionPanel extends EditorBasedWidget
  implements StatusBarWidget.Multiframe, StatusBarWidget.TextPresentation,
             CaretListener, SelectionListener, DocumentListener, DocumentBulkUpdateListener, PropertyChangeListener {

  public static final String SPACE = "     ";
  public static final String SEPARATOR = ":";
  public static final String MAX_POSSIBLE_TEXT = "0000000000000";
  public static final String ID = "Position";

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

  @Override
  @NotNull
  public String ID() {
    return ID;
  }

  @Override
  public StatusBarWidget copy() {
    return new PositionPanel(getProject());
  }

  @Override
  public WidgetPresentation getPresentation(@NotNull final PlatformType type) {
    return this;
  }

  @Override
  @NotNull
  public String getText() {
    return myText == null ? "" : myText;
  }

  @Override
  @NotNull
  public String getMaxPossibleText() {
    return MAX_POSSIBLE_TEXT;
  }

  @Override
  public float getAlignment() {
    return Component.CENTER_ALIGNMENT;
  }

  @Override
  public String getTooltipText() {
    return UIBundle.message("go.to.line.command.double.click");
  }

  @Override
  public Consumer<MouseEvent> getClickConsumer() {
    return mouseEvent -> {
      Project project = getProject();
      Editor editor = getFocusedEditor();
      if (project == null || editor == null) return;

      CommandProcessor processor = CommandProcessor.getInstance();
      processor.executeCommand(
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
    EditorEventMulticaster multicaster = EditorFactory.getInstance().getEventMulticaster();
    multicaster.addCaretListener(this, this);
    multicaster.addSelectionListener(this, this);
    multicaster.addDocumentListener(this, this);
    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect(this);
    connection.subscribe(DocumentBulkUpdateListener.TOPIC, this);
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener(SWING_FOCUS_OWNER_PROPERTY, this);
    Disposer.register(this, 
                      () -> KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener(SWING_FOCUS_OWNER_PROPERTY, 
                                                                                                               this));
  }

  @Override
  public void selectionChanged(final SelectionEvent e) {
    Editor editor = e.getEditor();
    if (isFocusedEditor(editor)) updatePosition(editor);
  }

  @Override
  public void caretPositionChanged(final CaretEvent e) {
    Editor editor = e.getEditor();
    // When multiple carets exist in editor, we don't show information about caret positions
    if (editor.getCaretModel().getCaretCount() == 1 && isFocusedEditor(editor)) updatePosition(editor);
  }

  @Override
  public void caretAdded(CaretEvent e) {
    updatePosition(e.getEditor());
  }

  @Override
  public void caretRemoved(CaretEvent e) {
    updatePosition(e.getEditor());
  }

  @Override
  public void documentChanged(DocumentEvent event) {
    Document document = event.getDocument();
    if (document instanceof DocumentEx && ((DocumentEx)document).isInBulkUpdate()) return;
    onDocumentUpdate(document);
  }

  @Override
  public void updateStarted(@NotNull Document doc) {}

  @Override
  public void updateFinished(@NotNull Document doc) {
    onDocumentUpdate(doc);
  }

  private void onDocumentUpdate(Document document) {
    Editor[] editors = EditorFactory.getInstance().getEditors(document);
    for (Editor editor : editors) {
      if (isFocusedEditor(editor)) {
        updatePosition(editor);
        break;
      }
    }
  }

  private boolean isFocusedEditor(Editor editor) {
    Component focusOwner = getFocusedComponent();
    return focusOwner == editor.getContentComponent();
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
            message.append(SPACE);
          }
        }
        LogicalPosition caret = editor.getCaretModel().getLogicalPosition();
        message.append(caret.line + 1).append(SEPARATOR).append(caret.column + 1);
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
