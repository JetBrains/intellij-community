// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification;

import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.impl.ConsoleViewUtil;
import com.intellij.execution.impl.EditorHyperlinkSupport;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.notification.impl.NotificationSettings;
import com.intellij.notification.impl.NotificationsConfigurationImpl;
import com.intellij.notification.impl.NotificationsManagerImpl;
import com.intellij.notification.impl.ui.NotificationsUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actions.ScrollToTheEndToolbarAction;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.colors.impl.DelegateColorScheme;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.ex.*;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.ContextMenuPopupHandler;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
final class EventLogConsole {
  private static final Key<String> GROUP_ID = Key.create("GROUP_ID");
  private static final Key<String> NOTIFICATION_ID = Key.create("NOTIFICATION_ID");

  private final EditorEx myLogEditor;

  private final NotNullLazyValue<EditorHyperlinkSupport> myHyperlinkSupport = new NotNullLazyValue<EditorHyperlinkSupport>() {
    @NotNull
    @Override
    protected EditorHyperlinkSupport compute() {
      return EditorHyperlinkSupport.get(getConsoleEditor());
    }
  };

  private final LogModel myProjectModel;

  private String myLastDate;

  private List<RangeHighlighter> myNMoreHighlighters;

  EventLogConsole(@NotNull LogModel model, @NotNull Disposable parentDisposable) {
    myProjectModel = model;

    Project project = myProjectModel.getProject();
    myLogEditor = ConsoleViewUtil.setupConsoleEditor(project, false, false);
    Disposer.register(parentDisposable, () -> {
      EditorFactory.getInstance().releaseEditor(myLogEditor);
    });

    myLogEditor.getSettings().setWhitespacesShown(false);
    installNotificationsFont(myLogEditor, parentDisposable);

    ((EditorMarkupModel)myLogEditor.getMarkupModel()).setErrorStripeVisible(true);

    ClearLogAction clearLog = new ClearLogAction(this);
    clearLog.registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.CONSOLE_CLEAR_ALL).getShortcutSet(),
                                       myLogEditor.getContentComponent());

    myLogEditor.installPopupHandler(new ContextMenuPopupHandler() {
      @Override
      public ActionGroup getActionGroup(@NotNull EditorMouseEvent event) {
        return createPopupActions(ActionManager.getInstance(), clearLog, myLogEditor, event);
      }
    });
  }

  private static void installNotificationsFont(@NotNull EditorEx editor, @NotNull Disposable parentDisposable) {
    DelegateColorScheme globalScheme = new DelegateColorScheme(EditorColorsManager.getInstance().getGlobalScheme()) {
      @Override
      public String getEditorFontName() {
        return getConsoleFontName();
      }

      @Override
      public int getEditorFontSize() {
        return getConsoleFontSize();
      }

      @Override
      public String getConsoleFontName() {
        String name = NotificationsUtil.getFontName();
        return name == null ? super.getConsoleFontName() : name;
      }

      @Override
      public int getConsoleFontSize() {
        Integer size = NotificationsUtil.getFontSize();
        return size == null ? super.getConsoleFontSize() : size;
      }

      @Override
      public void setEditorFontName(String fontName) {
      }

      @Override
      public void setConsoleFontName(String fontName) {
      }

      @Override
      public void setEditorFontSize(int fontSize) {
      }

      @Override
      public void setConsoleFontSize(int fontSize) {
      }
    };

    ApplicationManager.getApplication().getMessageBus().connect(parentDisposable).subscribe(EditorColorsManager.TOPIC, new EditorColorsListener() {
      @Override
      public void globalSchemeChange(EditorColorsScheme scheme) {
        globalScheme.setDelegate(EditorColorsManager.getInstance().getGlobalScheme());
        editor.reinitSettings();
      }
    });
    editor.setColorsScheme(ConsoleViewUtil.updateConsoleColorScheme(editor.createBoundColorSchemeDelegate(globalScheme)));
    if (editor instanceof EditorImpl) {
      ((EditorImpl)editor).setUseEditorAntialiasing(false);
    }
  }

  private static DefaultActionGroup createPopupActions(ActionManager actionManager,
                                                       ClearLogAction action,
                                                       EditorEx editor,
                                                       EditorMouseEvent event) {
    AnAction[] children = ((ActionGroup)actionManager.getAction(IdeActions.GROUP_CONSOLE_EDITOR_POPUP)).getChildren(null);
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new EventLogToolWindowFactory.ToggleSoftWraps(editor));
    group.add(new ScrollToTheEndToolbarAction(editor));
    group.addSeparator();
    addConfigureNotificationAction(editor, event, group);
    group.addAll(children);
    group.addSeparator();
    group.add(action);
    return group;
  }

  private static void addConfigureNotificationAction(@NotNull EditorEx editor,
                                                     @NotNull EditorMouseEvent event,
                                                     @NotNull DefaultActionGroup actions) {
    LogicalPosition position = editor.xyToLogicalPosition(event.getMouseEvent().getPoint());
    if (EditorUtil.inVirtualSpace(editor, position)) {
      return;
    }
    int offset = editor.logicalPositionToOffset(position);
    editor.getMarkupModel().processRangeHighlightersOverlappingWith(offset, offset, rangeHighlighter -> {
      String groupId = GROUP_ID.get(rangeHighlighter);
      if (groupId != null) {
        addConfigureNotificationAction(actions, groupId);
        return false;
      }
      return true;
    });
  }

  private static void addConfigureNotificationAction(@NotNull DefaultActionGroup actions, @NotNull String groupId) {
    DefaultActionGroup displayTypeGroup = DefaultActionGroup.createPopupGroup(() -> IdeBundle
      .message("popup.title.notification.display.type"));
    NotificationSettings settings = NotificationsConfigurationImpl.getSettings(groupId);
    NotificationDisplayType current = settings.getDisplayType();

    for (NotificationDisplayType type : NotificationDisplayType.values()) {
      if (type != NotificationDisplayType.TOOL_WINDOW ||
          NotificationsConfigurationImpl.getInstanceImpl().hasToolWindowCapability(groupId)) {
        displayTypeGroup.add(new DisplayTypeAction(settings, type, current));
      }
    }

    actions.add(displayTypeGroup);
    actions.addSeparator();
  }

  private static class DisplayTypeAction extends ToggleAction {
    private final NotificationSettings mySettings;
    private final NotificationDisplayType myType;
    private final NotificationDisplayType myCurrent;

    DisplayTypeAction(@NotNull NotificationSettings settings,
                             @NotNull NotificationDisplayType type,
                             @NotNull NotificationDisplayType current) {
      super(type.getTitle());
      mySettings = settings;
      myType = type;
      myCurrent = current;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myType == myCurrent;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      if (state) {
        NotificationsConfigurationImpl.getInstanceImpl().changeSettings(mySettings.withDisplayType(myType));
      }
    }
  }

  void doPrintNotification(@NotNull Notification notification) {
    Editor editor = getConsoleEditor();
    if (editor.isDisposed()) {
      return;
    }

    Document document = editor.getDocument();
    boolean scroll = document.getTextLength() == editor.getCaretModel().getOffset() || !editor.getContentComponent().hasFocus();

    if (document.getTextLength() > 0) {
      append(document, "\n");
    }

    String lastDate = DateFormatUtil.formatDate(notification.getTimestamp());
    if (document.getTextLength() == 0 || !lastDate.equals(myLastDate)) {
      myLastDate = lastDate;
      append(document, lastDate + "\n");
    }

    int startDateOffset = document.getTextLength();

    String date = DateFormatUtil.formatTime(notification.getTimestamp()) + "\t";
    append(document, date);

    int tabs = calculateTabs(editor, startDateOffset);

    int titleStartOffset = document.getTextLength();
    int startLine = document.getLineCount() - 1;

    EventLog.LogEntry pair = EventLog.formatForLog(notification, StringUtil.repeatSymbol('\t', tabs));

    final NotificationType type = notification.getType();
    TextAttributesKey key = type == NotificationType.ERROR
                            ? ConsoleViewContentType.LOG_ERROR_OUTPUT_KEY
                            : type == NotificationType.INFORMATION
                              ? ConsoleViewContentType.NORMAL_OUTPUT_KEY
                              : ConsoleViewContentType.LOG_WARNING_OUTPUT_KEY;

    int msgStart = document.getTextLength();
    append(document, pair.message);

    TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(key);
    int layer = HighlighterLayer.CARET_ROW + 1;
    RangeHighlighter highlighter = editor.getMarkupModel()
      .addRangeHighlighter(msgStart, document.getTextLength(), layer, attributes, HighlighterTargetArea.LINES_IN_RANGE);
    GROUP_ID.set(highlighter, notification.getGroupId());
    NOTIFICATION_ID.set(highlighter, notification.id);

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
      highlightNotification(notification, pair.status, startLine, document.getLineCount() - 1, titleStartOffset, pair.titleLength);
    }
  }

  private static int calculateTabs(@NotNull Editor editor, int startDateOffset) {
    Document document = editor.getDocument();
    int startOffset = document.getTextLength();
    Point dateStartPoint = editor.logicalPositionToXY(editor.offsetToLogicalPosition(startDateOffset));
    Point dateEndPoint = editor.logicalPositionToXY(editor.offsetToLogicalPosition(startOffset));
    int width = dateEndPoint.x - dateStartPoint.x;

    document.insertString(startOffset, "\n");

    Point startPoint = editor.logicalPositionToXY(editor.offsetToLogicalPosition(startOffset + 1));

    for (int count = 1; ; count++) {
      document.insertString(startOffset + count, "\t");

      Point endPoint = editor.logicalPositionToXY(editor.offsetToLogicalPosition(document.getTextLength()));
      int tabWidth = endPoint.x - startPoint.x;

      if (width <= tabWidth) {
        document.deleteString(startOffset, document.getTextLength());
        return count;
      }
    }
  }

  private void highlightNotification(final Notification notification,
                                     String message,
                                     final int startLine,
                                     final int endLine,
                                     int titleOffset,
                                     int titleLength) {

    final MarkupModel markupModel = getConsoleEditor().getMarkupModel();
    TextAttributes bold = new TextAttributes(null, null, null, null, Font.BOLD);
    final RangeHighlighter colorHighlighter = markupModel
      .addRangeHighlighter(titleOffset, titleOffset + titleLength, HighlighterLayer.CARET_ROW + 1, bold, HighlighterTargetArea.EXACT_RANGE);
    Color color = notification.getType() == NotificationType.ERROR
                  ? JBColor.RED
                  : notification.getType() == NotificationType.WARNING ? JBColor.YELLOW : JBColor.GREEN;
    colorHighlighter.setErrorStripeMarkColor(color);
    colorHighlighter.setErrorStripeTooltip(message);

    final Runnable removeHandler = () -> {
      if (colorHighlighter.isValid()) {
        markupModel.removeHighlighter(colorHighlighter);
      }

      TextAttributes italic = new TextAttributes(Gray.x80, null, null, null, Font.PLAIN);
      for (int line = startLine; line < endLine; line++) {
        for (RangeHighlighter highlighter : myHyperlinkSupport.getValue().findAllHyperlinksOnLine(line)) {
          markupModel
            .addRangeHighlighter(highlighter.getStartOffset(), highlighter.getEndOffset(), HighlighterLayer.CARET_ROW + 2, italic,
                                 HighlighterTargetArea.EXACT_RANGE);
          myHyperlinkSupport.getValue().removeHyperlink(highlighter);
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

  @NotNull
  public Editor getConsoleEditor() {
    return myLogEditor;
  }

  public void clearNMore() {
    if (myNMoreHighlighters != null) {
      MarkupModel model = getConsoleEditor().getMarkupModel();
      for (RangeHighlighter highlighter : myNMoreHighlighters) {
        model.removeHighlighter(highlighter);
      }
      myNMoreHighlighters = null;
    }
  }

  public void showNotification(@NotNull final List<String> ids) {
    clearNMore();
    myNMoreHighlighters = new ArrayList<>();

    EditorEx editor = (EditorEx)getConsoleEditor();
    List<RangeHighlighterEx> highlighters = ContainerUtil.mapNotNull(ids, this::findHighlighter);

    if (!highlighters.isEmpty()) {
      editor.getCaretModel().moveToOffset(highlighters.get(0).getStartOffset());
      editor.getScrollingModel().scrollToCaret(ScrollType.CENTER_UP);

      List<Point> ranges = new ArrayList<>();
      Point currentRange = null;
      DocumentEx document = editor.getDocument();

      for (RangeHighlighterEx highlighter : highlighters) {
        int startLine = document.getLineNumber(highlighter.getStartOffset());
        int endLine = document.getLineNumber(highlighter.getEndOffset()) + 1;
        if (currentRange != null && startLine - 1 == currentRange.y) {
          currentRange.y = endLine;
        }
        else {
          ranges.add(currentRange = new Point(startLine, endLine));
        }
      }

      //noinspection UseJBColor
      TextAttributes attributes =
        new TextAttributes(null, ColorUtil.mix(editor.getBackgroundColor(), new Color(0x808080), 0.1), null, EffectType.BOXED, Font.PLAIN);
      MarkupModelEx markupModel = editor.getMarkupModel();

      for (Point range : ranges) {
        int start = document.getLineStartOffset(range.x);
        int end = document.getLineStartOffset(range.y);
        myNMoreHighlighters
          .add(markupModel.addRangeHighlighter(start, end, HighlighterLayer.CARET_ROW + 2, attributes, HighlighterTargetArea.EXACT_RANGE));
      }
    }
  }

  @Nullable
  private RangeHighlighterEx findHighlighter(@NotNull final String id) {
    EditorEx editor = (EditorEx)getConsoleEditor();
    final Ref<RangeHighlighterEx> highlighter = new Ref<>();

    editor.getMarkupModel()
      .processRangeHighlightersOverlappingWith(0, editor.getDocument().getTextLength(), rangeHighlighter -> {
        if (id.equals(NOTIFICATION_ID.get(rangeHighlighter))) {
          highlighter.set(rangeHighlighter);
          return false;
        }
        return true;
      });

    return highlighter.get();
  }

  @Nullable
  public RelativePoint getRangeHighlighterLocation(RangeHighlighter range) {
    Editor editor = getConsoleEditor();
    Project project = editor.getProject();
    Window window = NotificationsManagerImpl.findWindowForBalloon(project);
    if (range != null && window != null) {
      Point point = editor.visualPositionToXY(editor.offsetToVisualPosition(range.getStartOffset()));
      return new RelativePoint(window, SwingUtilities.convertPoint(editor.getContentComponent(), point, window));
    }
    return null;
  }

  private static void append(@NotNull Document document, @NotNull String s) {
    document.insertString(document.getTextLength(), s);
  }

  public static final class ClearLogAction extends DumbAwareAction {
    private final EventLogConsole myConsole;

    public ClearLogAction(EventLogConsole console) {
      super(IdeBundle.message("action.text.clear.all"), IdeBundle.message("action.description.clear.the.contents.of.the.event.log"), AllIcons.Actions.GC);
      myConsole = console;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Editor editor = e.getData(CommonDataKeys.EDITOR);
      e.getPresentation().setEnabled(editor != null && editor.getDocument().getTextLength() > 0);
    }

    @Override
    public void actionPerformed(@NotNull final AnActionEvent e) {
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
