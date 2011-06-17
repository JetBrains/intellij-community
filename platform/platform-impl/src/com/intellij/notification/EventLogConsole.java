/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.notification;

import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.impl.ConsoleViewUtil;
import com.intellij.execution.impl.EditorCopyAction;
import com.intellij.execution.impl.EditorHyperlinkSupport;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.notification.impl.NotificationsManagerImpl;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.EditorPopupHandler;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.text.DateFormat;

/**
 * @author peter
 */
class EventLogConsole {
  private final Editor myLogEditor;
  private final EditorHyperlinkSupport myHyperlinkSupport;
  private final LogModel myProjectModel;

  EventLogConsole(@NotNull Project project, LogModel model) {
    myProjectModel = model;
    myLogEditor = ConsoleViewUtil.setupConsoleEditor(project, false, true);

    ((EditorMarkupModel) myLogEditor.getMarkupModel()).setErrorStripeVisible(true);


    myLogEditor.addEditorMouseListener(new EditorPopupHandler() {
      public void invokePopup(final EditorMouseEvent event) {
        final ActionManager actionManager = ActionManager.getInstance();
        final ActionPopupMenu menu = actionManager.createActionPopupMenu(ActionPlaces.UNKNOWN, createPopupActions(actionManager));
        final MouseEvent mouseEvent = event.getMouseEvent();
        menu.getComponent().show(mouseEvent.getComponent(), mouseEvent.getX(), mouseEvent.getY());
      }
    });
    myHyperlinkSupport = new EditorHyperlinkSupport(myLogEditor, project);
  }

  void releaseEditor() {
    EditorFactory.getInstance().releaseEditor(myLogEditor);
  }

  private DefaultActionGroup createPopupActions(ActionManager actionManager) {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new DumbAwareAction("Clear All") {
      @Override
      public void update(AnActionEvent e) {
        final boolean enabled = e.getData(PlatformDataKeys.EDITOR) != null;
        e.getPresentation().setEnabled(enabled);
        e.getPresentation().setVisible(enabled);
      }

      public void actionPerformed(final AnActionEvent e) {
        final Editor editor = e.getData(PlatformDataKeys.EDITOR);
        if (editor != null) {
          editor.getDocument().deleteString(0, editor.getDocument().getTextLength());
        }
        for (Notification notification : myProjectModel.getNotifications()) {
          myProjectModel.removeNotification(notification);
        }
        myProjectModel.setStatusMessage(null);
      }
    });
    group.add(new EditorCopyAction());
    group.addSeparator();
    group.add(actionManager.getAction(IdeActions.ACTION_COMPARE_CLIPBOARD_WITH_SELECTION));
    return group;
  }

  void doPrintNotification(final Notification notification) {
    if (myLogEditor.isDisposed()) {
      return;
    }

    Document document = myLogEditor.getDocument();
    boolean scroll = document.getTextLength() == myLogEditor.getCaretModel().getOffset();

    append(document, DateFormat.getTimeInstance(DateFormat.MEDIUM).format(notification.getCreationTime()) + " ");

    Pair<String, Boolean> pair = EventLog.formatForLog(notification);

    final NotificationType type = notification.getType();
    ConsoleViewContentType contentType = type == NotificationType.ERROR
                                         ? ConsoleViewContentType.ERROR_OUTPUT
                                         : type == NotificationType.INFORMATION
                                           ? ConsoleViewContentType.NORMAL_OUTPUT
                                           : ConsoleViewContentType.WARNING_OUTPUT;

    int msgStart = document.getTextLength();
    String message = pair.first;
    append(document, message);
    myLogEditor.getMarkupModel()
      .addRangeHighlighter(msgStart, document.getTextLength(), HighlighterLayer.CARET_ROW + 1, contentType.getAttributes(),
                           HighlighterTargetArea.EXACT_RANGE);

    if (pair.second) {
      String s = " ";
      append(document, s);

      final int linkStart = document.getTextLength();
      append(document, "more");
      myHyperlinkSupport.addHyperlink(linkStart, document.getTextLength(), null, new HyperlinkInfo() {
        @Override
        public void navigate(Project project) {
          Balloon balloon = notification.getBalloon();
          if (balloon != null) {
            balloon.hide();
          }

          Window window = NotificationsManagerImpl.findWindowForBalloon(project);
          if (window != null) {
            Point point = EventLogConsole.this.myLogEditor
              .visualPositionToXY(EventLogConsole.this.myLogEditor.offsetToVisualPosition(linkStart));
            Point target = SwingUtilities.convertPoint(EventLogConsole.this.myLogEditor.getContentComponent(), point, window);
            balloon = NotificationsManagerImpl.createBalloon(notification, true, true, false);
            balloon.show(new RelativePoint(window, target), Balloon.Position.above);
          }
        }
      });

      append(document, " ");
    }
    append(document, "\n");

    if (scroll) {
      myLogEditor.getCaretModel().moveToOffset(document.getTextLength());
      myLogEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    }

    if (notification.isImportant()) {
      highlightNotification(notification, message, document.getLineCount() - 2);
    }
  }

  private void highlightNotification(final Notification notification,
                                     String message, final int line) {
    TextAttributes attr = new TextAttributes(null, null, null, null, Font.BOLD);
    final RangeHighlighter lineHighlighter = myLogEditor.getMarkupModel().addLineHighlighter(line, HighlighterLayer.CARET_ROW + 1, attr);
    Color color = notification.getType() == NotificationType.ERROR
                  ? Color.red
                  : notification.getType() == NotificationType.WARNING ? Color.yellow : Color.green;
    lineHighlighter.setErrorStripeMarkColor(color);
    lineHighlighter.setErrorStripeTooltip(message);
    lineHighlighter.setGutterIconRenderer(new GutterIconRenderer() {
      @NotNull
      @Override
      public Icon getIcon() {
        return IconLoader.getIcon("/general/reset.png");
      }

      @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
      @Override
      public boolean equals(Object obj) {
        return this == obj;
      }

      @Override
      public int hashCode() {
        return 0;
      }

      @Override
      public String getTooltipText() {
        return "Mark as read";
      }

      @Override
      public boolean isNavigateAction() {
        return true;
      }

      @Override
      public AnAction getClickAction() {
        return new AnAction() {
          @Override
          public void actionPerformed(AnActionEvent e) {
            myProjectModel.removeNotification(notification);
          }
        };
      }
    });

    myProjectModel.removeHandlers.put(notification, new Runnable() {
      @Override
      public void run() {
        myLogEditor.getMarkupModel().removeHighlighter(lineHighlighter);
      }
    });
  }

  public Editor getConsoleEditor() {
    return myLogEditor;
  }

  private static void append(Document document, String s) {
    document.insertString(document.getTextLength(), s);
  }

}