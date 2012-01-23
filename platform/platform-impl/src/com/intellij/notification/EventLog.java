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
import com.intellij.ide.actions.ContextHelpAction;
import com.intellij.notification.impl.NotificationsConfigurable;
import com.intellij.notification.impl.NotificationsConfigurationImpl;
import com.intellij.notification.impl.NotificationsManagerImpl;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actions.ScrollToTheEndToolbarAction;
import com.intellij.openapi.editor.actions.ToggleUseSoftWrapsToolbarAction;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
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
  public static Pair<Notification, Long> getStatusMessage(@Nullable Project project) {
    return getLogModel(project).getStatusMessage();
  }

  public static LogEntry formatForLog(@NotNull final Notification notification) {
    String content = notification.getContent();
    String mainText = notification.getTitle();
    boolean showMore = false;
    if (StringUtil.isNotEmpty(content)) {
      if (content.startsWith("<p>")) {
        content = content.substring("<p>".length());
      }

      if (content.startsWith("<") && !content.startsWith("<a ")) {
        showMore = true;
      }
      if (StringUtil.isNotEmpty(mainText)) {
        mainText += ": ";
      }
      mainText += content;
    }

    int nlIndex = eolIndex(mainText);
    if (nlIndex >= 0) {
      mainText = mainText.substring(0, nlIndex);
      showMore = true;
    }

    List<Pair<TextRange, HyperlinkInfo>> links = new ArrayList<Pair<TextRange, HyperlinkInfo>>();

    String message = "";
    while (true) {
      Matcher tagMatcher = TAG_PATTERN.matcher(mainText);
      if (!tagMatcher.find()) {
        message += mainText;
        break;
      }
      message += mainText.substring(0, tagMatcher.start());
      Matcher aMatcher = A_PATTERN.matcher(tagMatcher.group());
      if (aMatcher.matches()) {
        final String href = aMatcher.group(2);
        int linkEnd = mainText.indexOf(A_CLOSING, tagMatcher.end());
        if (linkEnd > 0) {
          String linkText = mainText.substring(tagMatcher.end(), linkEnd).replaceAll(TAG_PATTERN.pattern(), "");

          links.add(new Pair<TextRange, HyperlinkInfo>(TextRange.from(message.length(), linkText.length()), new NotificationHyperlinkInfo(notification, href)));

          message += linkText;
          mainText = mainText.substring(linkEnd + A_CLOSING.length());
          continue;
        }
      }
      mainText = mainText.substring(tagMatcher.end());
    }

    message = StringUtil.unescapeXml(StringUtil.convertLineSeparators(message));

    String status = message;

    if (showMore) {
      message += " more ";
      links.add(new Pair<TextRange, HyperlinkInfo>(TextRange.from(message.length() - 5, 4), new ShowBalloon(notification)));
    }

    return new LogEntry(message, status, links);
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

  private static int eolIndex(String mainText) {
    TreeSet<Integer> indices = new TreeSet<Integer>();
    indices.add(mainText.indexOf("<br>", 1));
    indices.add(mainText.indexOf("<br/>", 1));
    indices.add(mainText.indexOf("<p/>", 1));
    indices.add(mainText.indexOf("<p>", 1));
    indices.add(mainText.indexOf("\n"));
    indices.remove(-1);
    return indices.isEmpty() ? -1 : indices.iterator().next();
  }

  public static boolean isEventLogVisible(Project project) {
    final ToolWindow window = getEventLog(project);
    return window != null && window.isVisible();
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
    private final List<Notification> myInitial = new CopyOnWriteArrayList<Notification>();
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

      ApplicationManager.getApplication().invokeLater(new Runnable() {
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
      group.add(new DumbAwareAction("Settings", "Edit notification settings", IconLoader.getIcon("/actions/showSettings.png")) {
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
      group.add(new DumbAwareAction("Mark all as read", "Mark all unread notifications as read", IconLoader.getIcon("/general/reset.png")) {
        @Override
        public void update(AnActionEvent e) {
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
        super("Show balloons", "Enable or suppress notification balloons", IconLoader.getIcon("/general/balloon.png"));
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
        Balloon balloon = NotificationsManagerImpl.createBalloon(myNotification, true, true);
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
