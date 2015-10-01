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
package com.intellij.notification;

import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.impl.ConsoleViewUtil;
import com.intellij.execution.impl.EditorHyperlinkSupport;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.icons.AllIcons;
import com.intellij.notification.impl.NotificationsManagerImpl;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.EditorPopupHandler;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
class EventLogConsole {
  private final NotNullLazyValue<Editor> myLogEditor = new NotNullLazyValue<Editor>() {
    @NotNull
    @Override
    protected Editor compute() {
      return createLogEditor();
    }
  };

  private final NotNullLazyValue<EditorHyperlinkSupport> myHyperlinkSupport = new NotNullLazyValue<EditorHyperlinkSupport>() {
    @NotNull
    @Override
    protected EditorHyperlinkSupport compute() {
      return new EditorHyperlinkSupport(myLogEditor.getValue(), myProjectModel.getProject());
    }
  };
  private final LogModel myProjectModel;

  EventLogConsole(LogModel model) {
    myProjectModel = model;
  }

  private Editor createLogEditor() {
    Project project = myProjectModel.getProject();
    final EditorEx editor = ConsoleViewUtil.setupConsoleEditor(project, false, false);
    myProjectModel.getProject().getMessageBus().connect().subscribe(ProjectManager.TOPIC, new ProjectManagerAdapter() {
      @Override
      public void projectClosed(Project project) {
        if (project == myProjectModel.getProject()) {
          EditorFactory.getInstance().releaseEditor(editor);
        }
      }
    });

    ((EditorMarkupModel)editor.getMarkupModel()).setErrorStripeVisible(true);

    final ClearLogAction clearLog = new ClearLogAction(this);
    clearLog.registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.CONSOLE_CLEAR_ALL).getShortcutSet(), editor.getContentComponent());

    editor.setContextMenuGroupId(null); // disabling default context menu
    editor.addEditorMouseListener(new EditorPopupHandler() {
      public void invokePopup(final EditorMouseEvent event) {
        final ActionManager actionManager = ActionManager.getInstance();
        final ActionPopupMenu menu = actionManager.createActionPopupMenu(ActionPlaces.EDITOR_POPUP, createPopupActions(actionManager, clearLog));
        final MouseEvent mouseEvent = event.getMouseEvent();
        menu.getComponent().show(mouseEvent.getComponent(), mouseEvent.getX(), mouseEvent.getY());
      }
    });
    return editor;
  }

  private DefaultActionGroup createPopupActions(ActionManager actionManager, ClearLogAction action) {
    AnAction[] children = ((ActionGroup)actionManager.getAction(IdeActions.GROUP_CONSOLE_EDITOR_POPUP)).getChildren(null);
    DefaultActionGroup group = new DefaultActionGroup(children);
    group.addSeparator();
    group.add(action);
    return group;
  }

  void doPrintNotification(final Notification notification) {
    Editor editor = myLogEditor.getValue();
    if (editor.isDisposed()) {
      return;
    }

    Document document = editor.getDocument();
    boolean scroll = document.getTextLength() == editor.getCaretModel().getOffset() || !editor.getContentComponent().hasFocus();

    String date = DateFormatUtil.formatTimeWithSeconds(notification.getTimestamp()) + " ";
    append(document, date);

    int startLine = document.getLineCount() - 1;

    EventLog.LogEntry pair = EventLog.formatForLog(notification, StringUtil.repeatSymbol(' ', date.length()));

    final NotificationType type = notification.getType();
    TextAttributesKey key = type == NotificationType.ERROR
                                         ? ConsoleViewContentType.LOG_ERROR_OUTPUT_KEY
                                         : type == NotificationType.INFORMATION
                                           ? ConsoleViewContentType.NORMAL_OUTPUT_KEY
                                           : ConsoleViewContentType.LOG_WARNING_OUTPUT_KEY;

    int msgStart = document.getTextLength();
    String message = pair.message;
    append(document, message);

    TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(key);
    int layer = HighlighterLayer.CARET_ROW + 1;
    editor.getMarkupModel().addRangeHighlighter(msgStart, document.getTextLength(), layer, attributes, HighlighterTargetArea.EXACT_RANGE);

    for (Pair<TextRange, HyperlinkInfo> link : pair.links) {
      final RangeHighlighter rangeHighlighter = myHyperlinkSupport.getValue()
        .createHyperlink(link.first.getStartOffset() + msgStart, link.first.getEndOffset() + msgStart, null, link.second);
      if (link.second instanceof EventLog.ShowBalloon) {
        ((EventLog.ShowBalloon)link.second).setRangeHighlighter(rangeHighlighter);
      }
    }

    append(document, "\n");

    if (scroll) {
      editor.getCaretModel().moveToOffset(document.getTextLength());
      editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    }

    if (notification.isImportant()) {
      highlightNotification(notification, pair.status, startLine, document.getLineCount() - 1);
    }
  }

  private void highlightNotification(final Notification notification,
                                     String message, final int line1, final int line2) {

    final MarkupModel markupModel = myLogEditor.getValue().getMarkupModel();
    TextAttributes bold = new TextAttributes(null, null, null, null, Font.BOLD);
    final List<RangeHighlighter> lineColors = new ArrayList<RangeHighlighter>();
    for (int line = line1; line < line2; line++) {
      final RangeHighlighter lineHighlighter = markupModel.addLineHighlighter(line, HighlighterLayer.CARET_ROW + 1, bold);
      Color color = notification.getType() == NotificationType.ERROR
                    ? JBColor.RED
                    : notification.getType() == NotificationType.WARNING ? JBColor.YELLOW : JBColor.GREEN;
      lineHighlighter.setErrorStripeMarkColor(color);
      lineHighlighter.setErrorStripeTooltip(message);
      lineColors.add(lineHighlighter);

    }

    final Document document = myLogEditor.getValue().getDocument();

    final Runnable removeHandler = new Runnable() {
      @Override
      public void run() {
        TextAttributes expired = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(ConsoleViewContentType.LOG_EXPIRED_ENTRY);
        TextAttributes italic = new TextAttributes(null, null, null, null, Font.ITALIC);
        for (RangeHighlighter colorHighlighter : lineColors) {
          if (colorHighlighter.isValid()) {
            int line = document.getLineNumber(colorHighlighter.getStartOffset());
            
            markupModel.addLineHighlighter(line, HighlighterLayer.CARET_ROW + 1, expired);

            for (RangeHighlighter highlighter : myHyperlinkSupport.getValue().findAllHyperlinksOnLine(line)) {
              markupModel.addRangeHighlighter(highlighter.getStartOffset(), highlighter.getEndOffset(), HighlighterLayer.CARET_ROW + 2, italic, HighlighterTargetArea.EXACT_RANGE);
              myHyperlinkSupport.getValue().removeHyperlink(highlighter);
            }
          }
          markupModel.removeHighlighter(colorHighlighter);
        }
      }
    };
    if (!notification.isExpired()) {
      myProjectModel.removeHandlers.put(notification, removeHandler);
    }
    else {
      removeHandler.run();
    }
  }

  public Editor getConsoleEditor() {
    return myLogEditor.getValue();
  }

  @Nullable
  public RelativePoint getRangeHighlighterLocation(RangeHighlighter range) {
    Editor editor = myLogEditor.getValue();
    Project project = editor.getProject();
    Window window = NotificationsManagerImpl.findWindowForBalloon(project);
    if (range != null && window != null) {
      Point point = editor.visualPositionToXY(editor.offsetToVisualPosition(range.getStartOffset()));
      return new RelativePoint(window, SwingUtilities.convertPoint(editor.getContentComponent(), point, window));
    }
    return null;
  }

  private static void append(Document document, String s) {
    document.insertString(document.getTextLength(), s);
  }

  public static class ClearLogAction extends DumbAwareAction {
    private EventLogConsole myConsole;

    public ClearLogAction(EventLogConsole console) {
      super("Clear All", "Clear the contents of the Event Log", AllIcons.Actions.GC);
      myConsole = console;
    }

    @Override
    public void update(AnActionEvent e) {
      Editor editor = e.getData(CommonDataKeys.EDITOR);
      e.getPresentation().setEnabled(editor != null && editor.getDocument().getTextLength() > 0);
    }

    public void actionPerformed(final AnActionEvent e) {
      LogModel model = myConsole.myProjectModel;
      for (Notification notification : model.getNotifications()) {
        notification.expire();
        model.removeNotification(notification);
      }
      model.setStatusMessage(null, 0);
      final Editor editor = e.getData(CommonDataKeys.EDITOR);
      if (editor != null) {
        editor.getDocument().deleteString(0, editor.getDocument().getTextLength());
      }
    }
  }
}
