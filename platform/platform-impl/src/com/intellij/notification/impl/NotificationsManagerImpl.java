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
package com.intellij.notification.impl;

import com.intellij.ide.FrameStateManager;
import com.intellij.notification.*;
import com.intellij.notification.impl.ui.NotificationsUtil;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.DialogWrapperDialog;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import com.intellij.ui.*;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author spleaner
 */
public class NotificationsManagerImpl extends NotificationsManager {
  public NotificationsManagerImpl() {
    ApplicationManager.getApplication().getMessageBus().connect().subscribe(Notifications.TOPIC, new MyNotificationListener(null));
  }

  @Override
  public void expire(@NotNull final Notification notification) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        EventLog.expireNotification(notification);
      }
    });
  }

  @Override
  public <T extends Notification> T[] getNotificationsOfType(Class<T> klass, @Nullable final Project project) {
    final List<T> result = new ArrayList<T>();
    if (project == null || !project.isDefault() && !project.isDisposed()) {
      for (Notification notification : EventLog.getLogModel(project).getNotifications()) {
        if (klass.isInstance(notification)) {
          //noinspection unchecked
          result.add((T) notification);
        }
      }
    }
    return ArrayUtil.toObjectArray(result, klass);
  }

  private static void doNotify(@NotNull final Notification notification,
                              @Nullable NotificationDisplayType displayType,
                              @Nullable final Project project) {
    final NotificationsConfigurationImpl configuration = NotificationsConfigurationImpl.getInstanceImpl();
    if (!configuration.isRegistered(notification.getGroupId())) {
      configuration.register(notification.getGroupId(), displayType == null ? NotificationDisplayType.BALLOON : displayType);
    }

    final NotificationSettings settings = NotificationsConfigurationImpl.getSettings(notification.getGroupId());
    boolean shouldLog = settings.isShouldLog();
    boolean displayable = settings.getDisplayType() != NotificationDisplayType.NONE;

    boolean willBeShown = displayable && NotificationsConfigurationImpl.getInstanceImpl().SHOW_BALLOONS;
    if (!shouldLog && !willBeShown) {
      notification.expire();
    }

    if (NotificationsConfigurationImpl.getInstanceImpl().SHOW_BALLOONS) {
      final Runnable runnable = new DumbAwareRunnable() {
        @Override
        public void run() {
          showNotification(notification, project);
        }
      };
      if (project == null) {
        UIUtil.invokeLaterIfNeeded(runnable);
      }
      else if (!project.isDisposed()) {
        StartupManager.getInstance(project).runWhenProjectIsInitialized(runnable);
      }
    }
  }

  private static void showNotification(final Notification notification, @Nullable final Project project) {
    Application application = ApplicationManager.getApplication();
    if (application instanceof ApplicationEx && !((ApplicationEx)application).isLoaded()) {
      application.invokeLater(new Runnable() {
        @Override
        public void run() {
          showNotification(notification, project);
        }
      }, ModalityState.current());
      return;
    }


    String groupId = notification.getGroupId();
    final NotificationSettings settings = NotificationsConfigurationImpl.getSettings(groupId);

    NotificationDisplayType type = settings.getDisplayType();
    String toolWindowId = NotificationsConfigurationImpl.getInstanceImpl().getToolWindowId(groupId);
    if (type == NotificationDisplayType.TOOL_WINDOW &&
        (toolWindowId == null || project == null || !ToolWindowManager.getInstance(project).canShowNotification(toolWindowId))) {
      type = NotificationDisplayType.BALLOON;
    }

    switch (type) {
      case NONE:
        return;
      //case EXTERNAL:
      //  notifyByExternal(notification);
      //  break;
      case STICKY_BALLOON:
      case BALLOON:
      default:
        Balloon balloon = notifyByBalloon(notification, type, project);
        if (!settings.isShouldLog() || type == NotificationDisplayType.STICKY_BALLOON) {
          if (balloon == null) {
            notification.expire();
          } else {
            balloon.addListener(new JBPopupAdapter() {
              @Override
              public void onClosed(LightweightWindowEvent event) {
                if (!event.isOk()) {
                  notification.expire();
                }
              }
            });
          }
        }
        break;
      case TOOL_WINDOW:
        MessageType messageType = notification.getType() == NotificationType.ERROR
                            ? MessageType.ERROR
                            : notification.getType() == NotificationType.WARNING ? MessageType.WARNING : MessageType.INFO;
        final NotificationListener notificationListener = notification.getListener();
        HyperlinkListener listener = notificationListener == null ? null : new HyperlinkListener() {
          @Override
          public void hyperlinkUpdate(HyperlinkEvent e) {
            notificationListener.hyperlinkUpdate(notification, e);
          }
        };
        assert toolWindowId != null;
        String msg = notification.getTitle();
        if (StringUtil.isNotEmpty(notification.getContent())) {
          if (StringUtil.isNotEmpty(msg)) {
            msg += "<br>";
          }
          msg += notification.getContent();
        }

        //noinspection SSBasedInspection
        ToolWindowManager.getInstance(project).notifyByBalloon(toolWindowId, messageType, msg, notification.getIcon(), listener);
    }
  }

  @Nullable
  private static Balloon notifyByBalloon(final Notification notification,
                                      final NotificationDisplayType displayType,
                                      @Nullable final Project project) {
    if (isDummyEnvironment()) return null;

    Window window = findWindowForBalloon(project);
    if (window instanceof IdeFrame) {
      final ProjectManager projectManager = ProjectManager.getInstance();
      final boolean noProjects = projectManager.getOpenProjects().length == 0;
      final boolean sticky = NotificationDisplayType.STICKY_BALLOON == displayType || noProjects;
      final Balloon balloon = createBalloon((IdeFrame)window, notification, false, false);
      Disposer.register(project != null ? project : ApplicationManager.getApplication(), balloon);

      if (notification.isExpired()) {
        return null;
      }

      BalloonLayout layout = ((IdeFrame)window).getBalloonLayout();

      if (layout == null) return null;
      layout.add(balloon);
      ((BalloonImpl)balloon).startFadeoutTimer(0);
      if (NotificationDisplayType.BALLOON == displayType) {
        FrameStateManager.getInstance().getApplicationActive().doWhenDone(new Runnable() {
          @Override
          public void run() {
            if (balloon.isDisposed()) {
              return;
            }

            if (!sticky) {
              ((BalloonImpl)balloon).startFadeoutTimer(0);
              ((BalloonImpl)balloon).setHideOnClickOutside(true);
            }
            else //noinspection ConstantConditions
              if (noProjects) {
              projectManager.addProjectManagerListener(new ProjectManagerAdapter() {
                @Override
                public void projectOpened(Project project) {
                  projectManager.removeProjectManagerListener(this);
                  if (!balloon.isDisposed()) {
                    ((BalloonImpl)balloon).startFadeoutTimer(300);
                  }
                }
              });
            }
          }
        });
      }
      return balloon;
    }
    return null;
  }

  @Nullable
  public static Window findWindowForBalloon(Project project) {
    Window frame = WindowManager.getInstance().getFrame(project);
    if (frame == null && project == null) {
      frame = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
      while (frame instanceof DialogWrapperDialog && ((DialogWrapperDialog)frame).getDialogWrapper().isModalProgress()) {
        frame = frame.getOwner();
      }
    }
    if (frame == null && project == null) {
      frame = (Window)WelcomeFrame.getInstance();
    }
    return frame;
  }

  public static Balloon createBalloon(@NotNull final IdeFrame window, final Notification notification, final boolean showCallout, final boolean hideOnClickOutside) {
    final JEditorPane text = new JEditorPane();
    text.setEditorKit(UIUtil.getHTMLEditorKit());

    final HyperlinkListener listener = NotificationsUtil.wrapListener(notification);
    if (listener != null) {
      text.addHyperlinkListener(listener);
    }

    final JLabel label = new JLabel(NotificationsUtil.buildHtml(notification, null));
    text.setText(NotificationsUtil.buildHtml(notification, "width:" + Math.min(JBUI.scale(350), label.getPreferredSize().width) + "px;"));
    text.setEditable(false);
    text.setOpaque(false);

    if (UIUtil.isUnderNimbusLookAndFeel()) {
      text.setBackground(UIUtil.TRANSPARENT_COLOR);
    }

    text.setBorder(null);

    final JPanel content = new NonOpaquePanel(new BorderLayout((int)(label.getIconTextGap() * 1.5), (int)(label.getIconTextGap() * 1.5)));

    if (text.getCaret() != null) {
      text.setCaretPosition(0);
    }
    JScrollPane pane = ScrollPaneFactory.createScrollPane(text,
                                                          ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                          ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    pane.setBorder(null);
    pane.setOpaque(false);
    pane.getViewport().setOpaque(false);
    content.add(pane, BorderLayout.CENTER);

    final NonOpaquePanel north = new NonOpaquePanel(new BorderLayout());
    north.add(new JLabel(NotificationsUtil.getIcon(notification)), BorderLayout.NORTH);
    content.add(north, BorderLayout.WEST);

    content.setBorder(new EmptyBorder(2, 4, 2, 4));

    Dimension preferredSize = text.getPreferredSize();
    text.setSize(preferredSize);
    
    Dimension paneSize = new Dimension(text.getPreferredSize());
    int maxHeight = Math.min(JBUI.scale(400), window.getComponent().getHeight() - 20);
    int maxWidth = Math.min(JBUI.scale(600), window.getComponent().getWidth() - 20);
    if (paneSize.height > maxHeight) {
      pane.setPreferredSize(new Dimension(Math.min(maxWidth, paneSize.width + UIUtil.getScrollBarWidth()), maxHeight));
    } else if (paneSize.width > maxWidth) {
      pane.setPreferredSize(new Dimension(maxWidth, paneSize.height + UIUtil.getScrollBarWidth()));
    }

    final BalloonBuilder builder = JBPopupFactory.getInstance().createBalloonBuilder(content);
    builder.setFillColor(new JBColor(Gray._234, Gray._92))
      .setCloseButtonEnabled(true)
      .setShowCallout(showCallout)
      .setShadow(false)
      .setHideOnClickOutside(hideOnClickOutside)
      .setHideOnAction(hideOnClickOutside)
      .setHideOnKeyOutside(hideOnClickOutside)
      .setHideOnFrameResize(false)
      .setBorderColor(new JBColor(Gray._180, Gray._110));

    final Balloon balloon = builder.createBalloon();
    balloon.setAnimationEnabled(false);
    notification.setBalloon(balloon);
    return balloon;
  }

  private static boolean isDummyEnvironment() {
    final Application application = ApplicationManager.getApplication();
    return application.isUnitTestMode() || application.isCommandLine();
  }

  public static class ProjectNotificationsComponent {

    public ProjectNotificationsComponent(final Project project) {
      if (isDummyEnvironment()) {
        return;
      }

      project.getMessageBus().connect().subscribe(Notifications.TOPIC, new MyNotificationListener(project));
    }

  }

  private static class MyNotificationListener extends NotificationsAdapter {
    private final Project myProject;

    public MyNotificationListener(@Nullable Project project) {
      myProject = project;
    }

    @Override
    public void notify(@NotNull Notification notification) {
      doNotify(notification, null, myProject);
    }
  }

}
