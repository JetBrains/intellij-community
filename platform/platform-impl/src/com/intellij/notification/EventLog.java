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
import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.ContextHelpAction;
import com.intellij.notification.impl.NotificationsConfigurable;
import com.intellij.notification.impl.NotificationsConfigurationImpl;
import com.intellij.notification.impl.NotificationsManagerImpl;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.actions.ScrollToTheEndToolbarAction;
import com.intellij.openapi.editor.actions.ToggleUseSoftWrapsToolbarAction;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.LinkedHashMap;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author peter
 */
public class EventLog implements Notifications {
  public static final String LOG_REQUESTOR = "Internal log requestor";
  public static final String LOG_TOOL_WINDOW_ID = "Event Log";
  public static final String HELP_ID = "reference.toolwindows.event.log";
  private final LogModel myModel = new LogModel(null, ApplicationManager.getApplication());
  private static final String A_CLOSING = "</a>";
  private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]*>");
  private static final Pattern A_PATTERN = Pattern.compile("<a ([^>]* )?href=[\"\']([^>]*)[\"\'][^>]*>");
  private static final Set<String> NEW_LINES = CollectionFactory.hashSet("<br>", "</br>", "<br/>", "<p>", "</p>", "<p/>");

  public EventLog() {
    ApplicationManager.getApplication().getMessageBus().connect().subscribe(Notifications.TOPIC, this);
  }

  @Override
  public void notify(@NotNull Notification notification) {
    final Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    if (openProjects.length == 0) {
      myModel.addNotification(notification);
    }
    for (Project p : openProjects) {
      getProjectComponent(p).printNotification(notification);
    }
  }

  public static void expire(@NotNull Notification notification) {
    getApplicationComponent().myModel.removeNotification(notification);
    for (Project p : ProjectManager.getInstance().getOpenProjects()) {
      getProjectComponent(p).myProjectModel.removeNotification(notification);
    }
  }

  private static EventLog getApplicationComponent() {
    return ApplicationManager.getApplication().getComponent(EventLog.class);
  }

  @Override
  public void register(@NotNull String groupDisplayName, @NotNull NotificationDisplayType defaultDisplayType) {
  }

  @Override
  public void register(@NotNull String groupDisplayName,
                       @NotNull NotificationDisplayType defaultDisplayType,
                       boolean shouldLog) {
  }

  @NotNull
  public static LogModel getLogModel(@Nullable Project project) {
    return project != null ? getProjectComponent(project).myProjectModel : getApplicationComponent().myModel;
  }

  @Nullable
  public static Trinity<Notification, String, Long> getStatusMessage(@Nullable Project project) {
    return getLogModel(project).getStatusMessage();
  }

  public static LogEntry formatForLog(@NotNull final Notification notification, String indent) {
    DocumentImpl logDoc = new DocumentImpl("",true);
    AtomicBoolean showMore = new AtomicBoolean(false);
    Map<RangeMarker, HyperlinkInfo> links = new LinkedHashMap<RangeMarker, HyperlinkInfo>();
    List<RangeMarker> lineSeparators = new ArrayList<RangeMarker>();

    String title = notification.getTitle();
    String content = notification.getContent();
    RangeMarker afterTitle = null;
    boolean hasHtml = parseHtmlContent(title, notification, logDoc, showMore, links, lineSeparators);
    if (StringUtil.isNotEmpty(title)) {
      if (StringUtil.isNotEmpty(content)) {
        appendText(logDoc, ": ");
        afterTitle = logDoc.createRangeMarker(logDoc.getTextLength() - 2, logDoc.getTextLength());
      }
    }
    hasHtml |= parseHtmlContent(content, notification, logDoc, showMore, links, lineSeparators);

    String status = getStatusText(logDoc, showMore, lineSeparators, hasHtml);

    indentNewLines(logDoc, lineSeparators, afterTitle, hasHtml, indent);

    ArrayList<Pair<TextRange, HyperlinkInfo>> list = new ArrayList<Pair<TextRange, HyperlinkInfo>>();
    for (RangeMarker marker : links.keySet()) {
      if (!marker.isValid()) {
        showMore.set(true);
        continue;
      }
      list.add(Pair.create(new TextRange(marker.getStartOffset(), marker.getEndOffset()), links.get(marker)));
    }

    if (showMore.get()) {
      String sb = "show balloon";
      if (!logDoc.getText().endsWith(" ")) {
        appendText(logDoc, " ");
      }
      appendText(logDoc, "(" + sb + ")");
      list.add(new Pair<TextRange, HyperlinkInfo>(TextRange.from(logDoc.getTextLength() - 1 - sb.length(), sb.length()),
                                                  new ShowBalloon(notification)));
    }

    return new LogEntry(logDoc.getText(), status, list);
  }

  private static void indentNewLines(DocumentImpl logDoc, List<RangeMarker> lineSeparators, RangeMarker afterTitle, boolean hasHtml, String indent) {
    if (!hasHtml) {
      int i = -1;
      while (true) {
        i = StringUtil.indexOf(logDoc.getText(), '\n', i + 1);
        if (i < 0) {
          break;
        }
        lineSeparators.add(logDoc.createRangeMarker(i, i + 1));
      }
    }
    if (!lineSeparators.isEmpty() && afterTitle != null && afterTitle.isValid()) {
      lineSeparators.add(afterTitle);
    }
    int nextLineStart = -1;
    for (RangeMarker separator : lineSeparators) {
      if (separator.isValid()) {
        int start = separator.getStartOffset();
        if (start == nextLineStart) {
          continue;
        }

        logDoc.replaceString(start, separator.getEndOffset(), "\n" + indent);
        nextLineStart = start + 1 + indent.length();
        while (nextLineStart < logDoc.getTextLength() && Character.isWhitespace(logDoc.getCharsSequence().charAt(nextLineStart))) {
          logDoc.deleteString(nextLineStart, nextLineStart + 1);
        }
      }
    }
  }

  private static String getStatusText(DocumentImpl logDoc, AtomicBoolean showMore, List<RangeMarker> lineSeparators, boolean hasHtml) {
    DocumentImpl statusDoc = new DocumentImpl(logDoc.getText(),true);
    List<RangeMarker> statusSeparators = new ArrayList<RangeMarker>();
    for (RangeMarker separator : lineSeparators) {
      if (separator.isValid()) {
        statusSeparators.add(statusDoc.createRangeMarker(separator.getStartOffset(), separator.getEndOffset()));
      }
    }
    removeJavaNewLines(statusDoc, statusSeparators, hasHtml);
    insertNewLineSubstitutors(statusDoc, showMore, statusSeparators);

    return statusDoc.getText();
  }

  private static boolean parseHtmlContent(String text, Notification notification,
                                          Document document,
                                          AtomicBoolean showMore,
                                          Map<RangeMarker, HyperlinkInfo> links, List<RangeMarker> lineSeparators) {
    String content = StringUtil.convertLineSeparators(text);

    int initialLen = document.getTextLength();
    boolean hasHtml = false;
    while (true) {
      Matcher tagMatcher = TAG_PATTERN.matcher(content);
      if (!tagMatcher.find()) {
        appendText(document, content);
        break;
      }
      
      String tagStart = tagMatcher.group();
      appendText(document, content.substring(0, tagMatcher.start()));
      Matcher aMatcher = A_PATTERN.matcher(tagStart);
      if (aMatcher.matches()) {
        final String href = aMatcher.group(2);
        int linkEnd = content.indexOf(A_CLOSING, tagMatcher.end());
        if (linkEnd > 0) {
          String linkText = content.substring(tagMatcher.end(), linkEnd).replaceAll(TAG_PATTERN.pattern(), "");
          int linkStart = document.getTextLength();
          appendText(document, linkText);
          links.put(document.createRangeMarker(new TextRange(linkStart, document.getTextLength())),
                    new NotificationHyperlinkInfo(notification, href));
          content = content.substring(linkEnd + A_CLOSING.length());
          continue;
        }
      }

      hasHtml = true;
      if (NEW_LINES.contains(tagStart)) {
        if (initialLen != document.getTextLength()) {
          lineSeparators.add(document.createRangeMarker(TextRange.from(document.getTextLength(), 0)));
        }
      }
      else if (!"<html>".equals(tagStart) && !"</html>".equals(tagStart) && !"<body>".equals(tagStart) && !"</body>".equals(tagStart)) {
        showMore.set(true);
      }
      content = content.substring(tagMatcher.end());
    }
    for (Iterator<RangeMarker> iterator = lineSeparators.iterator(); iterator.hasNext(); ) {
      RangeMarker next = iterator.next();
      if (next.getEndOffset() == document.getTextLength()) {
        iterator.remove();
      }
    }
    return hasHtml;
  }

  private static void insertNewLineSubstitutors(Document document, AtomicBoolean showMore, List<RangeMarker> lineSeparators) {
    for (RangeMarker marker : lineSeparators) {
      if (!marker.isValid()) {
        showMore.set(true);
        continue;
      }
      
      int offset = marker.getStartOffset();
      if (offset == 0 || offset == document.getTextLength()) {
        continue;
      }
      boolean spaceBefore = offset > 0 && Character.isWhitespace(document.getCharsSequence().charAt(offset - 1));
      if (offset < document.getTextLength()) {
        boolean spaceAfter = Character.isWhitespace(document.getCharsSequence().charAt(offset));
        int next = CharArrayUtil.shiftForward(document.getCharsSequence(), offset, " \t");
        if (next < document.getTextLength() && !Character.isLowerCase(document.getCharsSequence().charAt(next))) {
          document.insertString(offset, (spaceBefore ? "" : " ") + "//" + (spaceAfter ? "" : " "));
          continue;
        }
        if (spaceAfter) {
          continue;
        }
      }
      if (spaceBefore) {
        continue;
      }

      document.insertString(offset, " ");
    }
  }

  private static void removeJavaNewLines(Document document, List<RangeMarker> lineSeparators, boolean hasHtml) {
    CharSequence text = document.getCharsSequence();
    int i = 0;
    while (true) {
      i = StringUtil.indexOf(text, '\n', i);
      if (i < 0) break;
      document.deleteString(i, i + 1);
      if (!hasHtml) {
        lineSeparators.add(document.createRangeMarker(TextRange.from(i, 0)));
      }
    }
  }

  private static void appendText(Document document, String text) {
    text = StringUtil.replace(text, "&nbsp;", " ");
    text = StringUtil.replace(text, "&raquo;", ">>");
    text = StringUtil.replace(text, "&laquo;", "<<");
    text = StringUtil.replace(text, "&hellip;", "...");
    document.insertString(document.getTextLength(), StringUtil.unescapeXml(text));
  }

  public static class LogEntry {
    public final String message;
    public final String status;
    public final List<Pair<TextRange, HyperlinkInfo>> links;

    public LogEntry(String message, String status, List<Pair<TextRange, HyperlinkInfo>> links) {
      this.message = message;
      this.status = status;
      this.links = links;
    }
  }

  @Nullable
  public static ToolWindow getEventLog(Project project) {
    return project == null ? null : ToolWindowManager.getInstance(project).getToolWindow(LOG_TOOL_WINDOW_ID);
  }

  public static void toggleLog(final Project project) {
    final ToolWindow eventLog = getEventLog(project);
    if (eventLog != null) {
      if (!eventLog.isVisible()) {
        eventLog.activate(null, true);
        getLogModel(project).logShown();
      } else {
        eventLog.hide(null);
      }
    }
  }

  public static class ProjectTracker extends AbstractProjectComponent {
    private volatile EventLogConsole myConsole;
    private final List<Notification> myInitial = ContainerUtil.createEmptyCOWList();
    private final LogModel myProjectModel;

    public ProjectTracker(@NotNull final Project project) {
      super(project);

      myProjectModel = new LogModel(project, project);

      for (Notification notification : getApplicationComponent().myModel.takeNotifications()) {
        printNotification(notification);
      }

      project.getMessageBus().connect(project).subscribe(Notifications.TOPIC, new Notifications() {
        @Override
        public void notify(@NotNull Notification notification) {
          printNotification(notification);
        }

        @Override
        public void register(@NotNull String groupDisplayName, @NotNull NotificationDisplayType defaultDisplayType) {
        }

        @Override
        public void register(@NotNull String groupDisplayName,
                             @NotNull NotificationDisplayType defaultDisplayType,
                             boolean shouldLog) {
        }
      });

    }

    @Override
    public void projectOpened() {
      myConsole = new EventLogConsole(myProjectModel);

      for (Notification notification : myInitial) {
        printNotification(notification);
      }
      myInitial.clear();
    }

    @Override
    public void projectClosed() {
      getApplicationComponent().myModel.setStatusMessage(null, 0);
      StatusBar.Info.set("", null, EventLog.LOG_REQUESTOR);
    }

    private void printNotification(final Notification notification) {
      final EventLogConsole console = myConsole;
      if (console == null) {
        myInitial.add(notification);
        return;
      }

      if (!NotificationsConfigurationImpl.getSettings(notification.getGroupId()).isShouldLog()) {
        return;
      }

      myProjectModel.addNotification(notification);

      StartupManager.getInstance(myProject).runWhenProjectIsInitialized(new Runnable() {
        @Override
        public void run() {
          if (!ShutDownTracker.isShutdownHookRunning() && !myProject.isDisposed()) {
            console.doPrintNotification(notification);
          }
        }
      });
    }

  }

  private static ProjectTracker getProjectComponent(Project project) {
    return project.getComponent(ProjectTracker.class);
  }
  public static class FactoryItself implements ToolWindowFactory, DumbAware {
    @Override
    public void createToolWindowContent(final Project project, ToolWindow toolWindow) {
      final Editor editor = getProjectComponent(project).myConsole.getConsoleEditor();

      SimpleToolWindowPanel panel = new SimpleToolWindowPanel(false, true) {
        @Override
        public Object getData(@NonNls String dataId) {
          return PlatformDataKeys.HELP_ID.is(dataId) ? HELP_ID : super.getData(dataId);
        }
      };
      panel.setContent(editor.getComponent());

      DefaultActionGroup group = new DefaultActionGroup();
      group.add(new DumbAwareAction("Settings", "Edit notification settings", AllIcons.Actions.ShowSettings) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          ShowSettingsUtil.getInstance().editConfigurable(project, new NotificationsConfigurable());
        }
      });
      group.add(new DisplayBalloons());
      group.add(new ToggleUseSoftWrapsToolbarAction(SoftWrapAppliancePlaces.CONSOLE) {
        @Override
        protected Editor getEditor(AnActionEvent e) {
          return editor;
        }
      });
      group.add(new ScrollToTheEndToolbarAction(editor));
      group.add(new DumbAwareAction("Mark all as read", "Mark all unread notifications as read", AllIcons.General.Reset) {
        @Override
        public void update(AnActionEvent e) {
          if (project.isDisposed()) return;
          e.getPresentation().setEnabled(!getProjectComponent(project).myProjectModel.getNotifications().isEmpty());
        }

        @Override
        public void actionPerformed(AnActionEvent e) {
          LogModel model = getProjectComponent(project).myProjectModel;
          for (Notification notification : model.getNotifications()) {
            model.removeNotification(notification);
            notification.expire();
          }
        }
      });
      group.add(new ContextHelpAction(HELP_ID));

      ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, false);
      toolbar.setTargetComponent(panel);
      panel.setToolbar(toolbar.getComponent());

      final Content content = ContentFactory.SERVICE.getInstance().createContent(panel, "", false);
      toolWindow.getContentManager().addContent(content);
    }

    private static class DisplayBalloons extends ToggleAction implements DumbAware {
      public DisplayBalloons() {
        super("Show balloons", "Enable or suppress notification balloons", AllIcons.General.Balloon);
      }

      @Override
      public boolean isSelected(AnActionEvent e) {
        return NotificationsConfigurationImpl.getNotificationsConfigurationImpl().SHOW_BALLOONS;
      }

      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        NotificationsConfigurationImpl.getNotificationsConfigurationImpl().SHOW_BALLOONS = state;
      }
    }
  }

  private static class NotificationHyperlinkInfo implements HyperlinkInfo {
    private final Notification myNotification;
    private final String myHref;

    public NotificationHyperlinkInfo(Notification notification, String href) {
      myNotification = notification;
      myHref = href;
    }

    @Override
    public void navigate(Project project) {
      NotificationListener listener = myNotification.getListener();
      if (listener != null) {
        EventLogConsole console = EventLog.getProjectComponent(project).myConsole;
        URL url = null;
        try {
          url = new URL(null, myHref);
        }
        catch (MalformedURLException ignored) {
        }
        listener.hyperlinkUpdate(myNotification, new HyperlinkEvent(console.getConsoleEditor().getContentComponent(), HyperlinkEvent.EventType.ACTIVATED, url, myHref));
      }
    }
  }

  private static class ShowBalloon implements HyperlinkInfo {
    private final Notification myNotification;

    public ShowBalloon(Notification notification) {
      myNotification = notification;
    }

    @Override
    public void navigate(Project project) {
      hideBalloon(myNotification);

      for (Notification notification : getLogModel(project).getNotifications()) {
        hideBalloon(notification);
      }

      RelativePoint target = EventLog.getProjectComponent(project).myConsole.getHyperlinkLocation(this);
      if (target != null) {
        IdeFrame frame = WindowManager.getInstance().getIdeFrame(project);
        assert frame != null;
        Balloon balloon = NotificationsManagerImpl.createBalloon(frame, myNotification, true, true);
        Disposer.register(project, balloon);
        balloon.show(target, Balloon.Position.above);
      }
    }

    private static void hideBalloon(Notification notification1) {
      Balloon balloon = notification1.getBalloon();
      if (balloon != null) {
        balloon.hide();
      }
    }
  }
}
