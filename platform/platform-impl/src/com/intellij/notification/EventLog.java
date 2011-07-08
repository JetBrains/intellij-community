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

import com.intellij.notification.impl.NotificationsConfigurable;
import com.intellij.notification.impl.NotificationsConfiguration;
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
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author peter
 */
public class EventLog implements Notifications {
  public static final String LOG_REQUESTOR = "Internal log requestor";
  public static final String LOG_TOOL_WINDOW_ID = "Event Log";
  private final LogModel myModel = new LogModel(null);

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
  public void register(@NotNull String groupDisplayType, @NotNull NotificationDisplayType defaultDisplayType) {
  }

  @NotNull
  public static LogModel getLogModel(@Nullable Project project) {
    return project != null ? getProjectComponent(project).myProjectModel : getApplicationComponent().myModel;
  }

  @Nullable
  public static Notification getStatusMessage(@Nullable Project project) {
    return getLogModel(project).getStatusMessage();
  }

  public static Pair<String, Boolean> formatForLog(@NotNull final Notification notification) {
    boolean showLink = notification.getListener() != null;
    String content = notification.getContent();
    String mainText = notification.getTitle();
    if (StringUtil.isNotEmpty(content) && !content.startsWith("<")) {
      if (StringUtil.isNotEmpty(mainText)) {
        mainText += ": ";
      }
      mainText += content;
    }

    int nlIndex = eolIndex(mainText);
    if (nlIndex >= 0) {
      mainText = mainText.substring(0, nlIndex);
      showLink = true;
    }

    mainText = mainText.replaceAll("<[^>]*>", "");
    return Pair.create(mainText, showLink);
  }

  private static int eolIndex(String mainText) {
    int nlIndex = mainText.indexOf("<br>");
    if (nlIndex < 0) nlIndex = mainText.indexOf("<br/>");
    if (nlIndex < 0) nlIndex = mainText.indexOf("\n");
    return nlIndex;
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
    private List<Notification> myInitial = new CopyOnWriteArrayList<Notification>();
    private final LogModel myProjectModel;

    public ProjectTracker(final Project project) {
      super(project);

      myProjectModel = new LogModel(project);

      for (Notification notification : getApplicationComponent().myModel.takeNotifications()) {
        printNotification(notification);
      }

      project.getMessageBus().connect(project).subscribe(Notifications.TOPIC, new Notifications() {
        @Override
        public void notify(@NotNull Notification notification) {
          printNotification(notification);
        }

        @Override
        public void register(@NotNull String groupDisplayType, @NotNull NotificationDisplayType defaultDisplayType) {
        }
      });

    }

    @Override
    public void projectOpened() {
      myConsole = new EventLogConsole(myProject, myProjectModel);

      for (Notification notification : myInitial) {
        printNotification(notification);
      }
      myInitial.clear();
    }

    @Override
    public void projectClosed() {
      myConsole.releaseEditor();
      getApplicationComponent().myModel.setStatusMessage(null);
    }

    private void printNotification(final Notification notification) {
      final EventLogConsole console = myConsole;
      if (console == null) {
        myInitial.add(notification);
        return;
      }

      if (!NotificationsConfiguration.getSettings(notification.getGroupId()).isShouldLog()) {
        return;
      }

      myProjectModel.addNotification(notification);

      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          console.doPrintNotification(notification);
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

      SimpleToolWindowPanel panel = new SimpleToolWindowPanel(false, true);
      panel.setContent(editor.getComponent());

      DefaultActionGroup group = new DefaultActionGroup();
      group.add(new DumbAwareAction("Settings", "Edit notification settings", IconLoader.getIcon("/general/secondaryGroup.png")) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          ShowSettingsUtil.getInstance().editConfigurable(project, new NotificationsConfigurable());
        }
      });
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
          }
        }
      });

      ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, false);
      toolbar.setTargetComponent(panel);
      panel.setToolbar(toolbar.getComponent());

      final Content content = ContentFactory.SERVICE.getInstance().createContent(panel, "", false);
      toolWindow.getContentManager().addContent(content);
    }

  }
}
