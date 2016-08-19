/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.notification.impl.actions;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.internal.statistic.connect.StatisticsNotification;
import com.intellij.internal.statistic.updater.StatisticsNotificationManager;
import com.intellij.notification.*;
import com.intellij.notification.impl.NotificationsManagerImpl;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author spleaner
 * @author Sergey.Malenkov
 */
public class NotificationTestAction extends AnAction implements DumbAware {
  public static final String TEST_GROUP_ID = "Test Notification";
  private static final NotificationGroup TEST_STICKY_GROUP =
    new NotificationGroup("Test Sticky Notification", NotificationDisplayType.STICKY_BALLOON, true);
  private static final NotificationGroup TEST_TOOLWINDOW_GROUP =
    NotificationGroup.toolWindowGroup("Test ToolWindow Notification", ToolWindowId.TODO_VIEW, true);
  private static final String MESSAGE_KEY = "NotificationTestAction_Message";

  public void actionPerformed(@NotNull AnActionEvent event) {
    new NotificationDialog(event.getProject(), NotificationsManagerImpl.newEnabled()).show();
  }

  private static final class NotificationDialog extends DialogWrapper {
    private final JTextField myTitle = new JTextField(50);
    private final JTextArea myMessage = new JTextArea(10, 50);
    private final JComboBox myType = new JComboBox(NotificationType.values());
    private final MessageBus myMessageBus;
    private final boolean myIsNew;

    private NotificationDialog(@Nullable Project project, boolean isNew) {
      super(project, true, IdeModalityType.MODELESS);
      myIsNew = isNew;
      myMessageBus = project != null ? project.getMessageBus() : ApplicationManager.getApplication().getMessageBus();
      init();
      setOKButtonText("Notify");
      setTitle("Test Notification");
      if (isNew) {
        myMessage.setText(
          PropertiesComponent.getInstance().getValue(MESSAGE_KEY, "GroupID:\nTitle:\nSubtitle:\nContent:\nContent:\nActions:\nSticky:\n"));
      }
      else {
        myMessage.setText("You can close<br>\n" +
                          "this very very very very long notification\n" +
                          "by clicking <a href=\"close\">this link</a>.\n" +
                          "<p>Long long long long. It should be long. Very long. Too long. And even longer.");
      }
    }

    @Nullable
    @Override
    protected String getDimensionServiceKey() {
      return myIsNew ? "NotificationTestAction" : null;
    }

    @Override
    protected JComponent createCenterPanel() {
      JPanel panel = new JPanel(new BorderLayout(10, 10));
      if (!myIsNew) {
        panel.add(BorderLayout.NORTH, myTitle);
        panel.add(BorderLayout.SOUTH, myType);
      }
      panel.add(BorderLayout.CENTER, new JScrollPane(myMessage));
      return panel;
    }

    @NotNull
    @Override
    protected Action[] createActions() {
      return new Action[]{getOKAction(), getCancelAction()};
    }

    @Override
    public void doCancelAction() {
      if (myIsNew) {
        PropertiesComponent.getInstance().setValue(MESSAGE_KEY, myMessage.getText());
      }
      super.doCancelAction();
    }

    @Override
    protected void doOKAction() {
      String message = myMessage.getText();
      if (myIsNew) {
        newNotification(message);
        return;
      }
      String title = myTitle.getText();
      Object value = myType.getSelectedItem();
      NotificationType type = value instanceof NotificationType ? (NotificationType)value : NotificationType.ERROR;
      final Notification notification = new Notification(TEST_GROUP_ID, title, message, type, new NotificationListener() {
        public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
          notification.expire();
        }
      });
      ApplicationManager.getApplication().executeOnPooledThread(() -> myMessageBus.syncPublisher(Notifications.TOPIC).notify(notification));
    }

    private void newNotification(String text) {
      final List<NotificationInfo> notifications = new ArrayList<>();
      NotificationInfo notification = null;

      for (String line : StringUtil.splitByLines(text, false)) {
        if (line.length() == 0) {
          if (notification != null) {
            notification = null;
            continue;
          }
        }
        if (line.startsWith("//")) {
          continue;
        }
        if (line.startsWith("--")) {
          break;
        }
        if (notification == null) {
          notification = new NotificationInfo();
          notifications.add(notification);
        }
        if (line.startsWith("GroupID:")) {
          notification.setGroupId(StringUtil.substringAfter(line, ":"));
        }
        else if (line.startsWith("Title:")) {
          notification.setTitle(StringUtil.substringAfter(line, ":"));
        }
        else if (line.startsWith("Content:")) {
          String value = StringUtil.substringAfter(line, ":");
          if (value != null) {
            notification.addContent(value);
          }
        }
        else if (line.startsWith("Subtitle:")) {
          notification.setSubtitle(StringUtil.substringAfter(line, ":"));
        }
        else if (line.startsWith("Actions:")) {
          String value = StringUtil.substringAfter(line, ":");
          if (value != null) {
            notification.setActions(StringUtil.split(value, ","));
          }
        }
        else if (line.startsWith("Type:")) {
          notification.setType(StringUtil.substringAfter(line, ":"));
        }
        else if (line.startsWith("Sticky:")) {
          notification.setSticky("true".equals(StringUtil.substringAfter(line, ":")));
        }
        else if (line.startsWith("Listener:")) {
          notification.setAddListener("true".equals(StringUtil.substringAfter(line, ":")));
        }
        else if (line.startsWith("Toolwindow:")) {
          notification.setToolwindow("true".equals(StringUtil.substringAfter(line, ":")));
        }
      }

      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        for (NotificationInfo info : notifications) {
          myMessageBus.syncPublisher(Notifications.TOPIC).notify(info.getNotification());
        }
      });
    }
  }

  private static class NotificationInfo implements NotificationListener {
    private String myGroupId;
    private String myTitle;
    private String mySubtitle;
    private List<String> myContent;
    private List<String> myActions;
    private NotificationType myType = NotificationType.INFORMATION;
    private boolean mySticky;
    private boolean myAddListener;
    private boolean myToolwindow;

    private Notification myNotification;

    public Notification getNotification() {
      if (myNotification == null) {
        Icon icon = null;
        if (!StringUtil.isEmpty(myGroupId)) {
          icon = IconLoader.findIcon(myGroupId);
        }
        if ("!!!St!!!".equals(myTitle)) {
          return myNotification = new StatisticsNotification(StatisticsNotificationManager.GROUP_DISPLAY_ID, getListener()).setIcon(icon);
        }
        String displayId = mySticky ? TEST_STICKY_GROUP.getDisplayId() : TEST_GROUP_ID;
        if (myToolwindow) {
          displayId = TEST_TOOLWINDOW_GROUP.getDisplayId();
        }
        String content = myContent == null ? "" : StringUtil.join(myContent, "\n");
        if (icon == null) {
          myNotification =
            new Notification(displayId, StringUtil.notNullize(myTitle), content, myType, getListener());
        }
        else {
          myNotification = new Notification(displayId, icon, myTitle, mySubtitle, content, myType, getListener());
          if (myActions != null) {
            for (String action : myActions) {
              myNotification.addAction(new MyAnAction(action));
            }
          }
        }
      }
      return myNotification;
    }

    @Nullable
    private NotificationListener getListener() {
      return myAddListener ? this : null;
    }

    public void setGroupId(@Nullable String groupId) {
      myGroupId = groupId;
    }

    public void setTitle(@Nullable String title) {
      myTitle = title;
    }

    public void setSubtitle(@Nullable String subtitle) {
      mySubtitle = subtitle;
    }

    public void setAddListener(boolean addListener) {
      myAddListener = addListener;
    }

    public void addContent(@NotNull String content) {
      if (myContent == null) {
        myContent = new ArrayList<>();
      }
      myContent.add(content);
    }

    public void setActions(@NotNull List<String> actions) {
      myActions = actions;
    }

    public void setSticky(boolean sticky) {
      mySticky = sticky;
    }

    public void setToolwindow(boolean toolwindow) {
      myToolwindow = toolwindow;
    }

    public void setType(@Nullable String type) {
      if ("info".equals(type)) {
        myType = NotificationType.INFORMATION;
      }
      else if ("error".equals(type)) {
        myType = NotificationType.ERROR;
      }
      else if ("warn".equals(type)) {
        myType = NotificationType.WARNING;
      }
    }

    @Override
    public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
      if (MessageDialogBuilder.yesNo("Notification Listener", event.getDescription() + "      Expire?").is()) {
        myNotification.expire();
        myNotification = null;
      }
    }

    private class MyAnAction extends AnAction {
      private MyAnAction(@Nullable String text) {
        if (text != null) {
          if (text.endsWith(".png")) {
            Icon icon = IconLoader.findIcon(text);
            if (icon != null) {
              getTemplatePresentation().setIcon(icon);
              return;
            }
          }
          getTemplatePresentation().setText(text);
        }
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        if (MessageDialogBuilder.yesNo("AnAction", getTemplatePresentation().getText() + "      Expire?").is()) {
          myNotification.expire();
          myNotification = null;
        }
      }
    }
  }
}
