/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;

/**
 * @author spleaner
 * @author Sergey.Malenkov
 */
public class NotificationTestAction extends AnAction implements DumbAware {
  public static final String TEST_GROUP_ID = "Test Notification";

  public void actionPerformed(@NotNull AnActionEvent event) {
    new NotificationDialog(event.getProject()).show();
  }

  private static final class NotificationDialog extends DialogWrapper {
    private final JTextField myTitle = new JTextField(50);
    private final JTextArea myMessage = new JTextArea(10, 50);
    private final JComboBox myType = new JComboBox(NotificationType.values());
    private final MessageBus myMessageBus;

    private NotificationDialog(Project project) {
      super(project, true, IdeModalityType.MODELESS);
      myMessageBus = project != null ? project.getMessageBus() : ApplicationManager.getApplication().getMessageBus();
      init();
      setOKButtonText("Notify");
      setTitle("Test Notification");
      myMessage.setText("You can close<br>\n" +
                        "this very very very very long notification\n" +
                        "by clicking <a href=\"close\">this link</a>.\n" +
                        "<p>Long long long long. It should be long. Very long. Too long. And even longer.");
    }

    @Override
    protected JComponent createCenterPanel() {
      JPanel panel = new JPanel(new BorderLayout(10, 10));
      panel.add(BorderLayout.NORTH, myTitle);
      panel.add(BorderLayout.CENTER, new JScrollPane(myMessage));
      panel.add(BorderLayout.SOUTH, myType);
      return panel;
    }

    @NotNull
    @Override
    protected Action[] createActions() {
      return new Action[]{getOKAction(), getCancelAction()};
    }

    @Override
    protected void doOKAction() {
      String title = myTitle.getText();
      String message = myMessage.getText();
      Object value = myType.getSelectedItem();
      NotificationType type = value instanceof NotificationType ? (NotificationType)value : NotificationType.ERROR;
      final Notification notification = new Notification(TEST_GROUP_ID, title, message, type, new NotificationListener() {
        public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
          notification.expire();
        }
      });
      ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        @Override
        public void run() {
          myMessageBus.syncPublisher(Notifications.TOPIC).notify(notification);
        }
      });
    }
  }
}
