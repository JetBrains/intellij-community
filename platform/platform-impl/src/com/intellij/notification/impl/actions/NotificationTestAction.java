// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification.impl.actions;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.*;
import com.intellij.notification.Notification.CollapseActionsDirection;
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
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("HardCodedStringLiteral")
public final class NotificationTestAction extends AnAction implements DumbAware {
  public static final String TEST_GROUP_ID = "Test Notification";
  private static class Holder {
    private static final NotificationGroup TEST_STICKY_GROUP =
      NotificationGroupManager.getInstance().getNotificationGroup("Test Sticky Notification");
    private static final NotificationGroup TEST_TOOLWINDOW_GROUP =
      NotificationGroupManager.getInstance().getNotificationGroup("Test ToolWindow Notification");
  }
  private static final String MESSAGE_KEY = "NotificationTestAction_Message";

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    new NotificationDialog(event.getProject()).show();
  }

  private static final class NotificationDialog extends DialogWrapper {
    private final JTextArea myMessage = new JTextArea(10, 50);
    private final MessageBus myMessageBus;

    private NotificationDialog(@Nullable Project project) {
      super(project, true, IdeModalityType.MODELESS);
      myMessageBus = project != null ? project.getMessageBus() : ApplicationManager.getApplication().getMessageBus();
      init();
      setOKButtonText("Notify");
      setTitle("Test Notification");
      myMessage.setText(PropertiesComponent.getInstance().getValue(MESSAGE_KEY));
    }

    @Override
    protected String getDimensionServiceKey() {
      return "NotificationTestAction";
    }

    @Override
    protected JComponent createCenterPanel() {
      JPanel panel = new JPanel(new BorderLayout(10, 10));
      panel.add(BorderLayout.CENTER, new JScrollPane(myMessage));
      return panel;
    }

    @Override
    protected Action @NotNull [] createActions() {
      return new Action[]{getOKAction(), getCancelAction()};
    }

    @Override
    protected Action @NotNull [] createLeftSideActions() {
      Action balloon = new AbstractAction("Balloon Examples") {
        @Override
        public void actionPerformed(ActionEvent e) {
          setExamples("// Example 1\nIcon:/toolwindows/toolWindowChanges.png\nTitle:Deleted Branch\nContent:Unmerged commits discarded\n" +
                      "Actions:Restore,View Commits,Delete Tracked Branch\n\n" +
                      "// Example 2\nType:warn\nTitle:Title\nSubtitle:Subtitle\nContent:Foo<br>Bar\nSticky\n--\n" +
                      "// Description\nType:info/error/warn\nIcon:\nTitle:\nSubtitle:\n" +
                      "Content:\nContent:\nActions:\nSticky\n--\n");
        }
      };
      Action toolwindow = new AbstractAction("Toolwindow Examples") {
        @Override
        public void actionPerformed(ActionEvent e) {
          setExamples("// Example\nToolwindow\nContent:Build completed successfully in 7 s 851 ms\n--\n" +
                      "// Description: Notifications shows for toolwindow TODO\n" +
                      "Toolwindow\nType:info/error/warn\nIcon:\nTitle:\n" +
                      "Content:\nContent:\n--\n");
        }
      };
      return new Action[]{balloon, toolwindow};
    }

    @Override
    public void doCancelAction() {
      PropertiesComponent.getInstance().setValue(MESSAGE_KEY, StringUtil.nullize(myMessage.getText(), true));
      super.doCancelAction();
    }

    @Override
    protected void doOKAction() {
      newNotification(myMessage.getText());
    }

    private void setExamples(@NotNull String text) {
      try {
        myMessage.getDocument().insertString(0, text, null);
      }
      catch (BadLocationException ignore) {
      }
    }

    private void newNotification(@NotNull String text) {
      List<NotificationInfo> notifications = new ArrayList<>();
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
        if (line.startsWith("Icon:")) {
          notification.setIcon(StringUtil.substringAfter(line, ":"));
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
        else if (line.startsWith("LaterId:")) {
          notification.setRemindLaterHandlerId(StringUtil.substringAfter(line, ":"));
        }
        else if (line.equals("Suggestion")) {
          notification.setSuggestionType(true);
        }
        else if (line.equals("ImportantSuggestion")) {
          notification.setImportantSuggestion(true);
        }
        else if (line.equals("Sticky")) {
          notification.setSticky(true);
        }
        else if (line.equals("Listener")) {
          notification.setAddListener(true);
        }
        else if (line.equals("Toolwindow")) {
          notification.setToolwindow(true);
        }
        else if (line.equals("LeftCollapseActions")) {
          notification.myRightActionsDirection = false;
        }
      }

      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        for (NotificationInfo info : notifications) {
          myMessageBus.syncPublisher(Notifications.TOPIC).notify(info.getNotification());
        }
      });
    }
  }

  private static final class NotificationInfo implements NotificationListener {
    private String myIcon;
    private String myTitle;
    private String mySubtitle;
    private List<String> myContent;
    private List<String> myActions;
    private NotificationType myType = NotificationType.INFORMATION;
    private boolean mySticky;
    private boolean myAddListener;
    private boolean myToolwindow;
    private boolean myRightActionsDirection = true;
    private boolean mySuggestionType;
    private boolean myImportantSuggestion;

    private Notification myNotification;
    private String myRemindLaterHandlerId;

    public Notification getNotification() {
      if (myNotification == null) {
        Icon icon = StringUtil.isEmpty(myIcon) ? null : IconLoader.findIcon(myIcon);

        String displayId = mySticky ? Holder.TEST_STICKY_GROUP.getDisplayId() : TEST_GROUP_ID;
        if (myToolwindow) {
          displayId = Holder.TEST_TOOLWINDOW_GROUP.getDisplayId();
        }

        String content = myContent == null ? "" : String.join("\n", myContent);

        myNotification = new Notification(displayId, content, myType).setIcon(icon).setTitle(myTitle, mySubtitle);

        NotificationListener listener = getListener();
        if (listener != null) {
          myNotification.setListener(listener);
        }

        if (myRemindLaterHandlerId != null) {
          myNotification.setRemindLaterHandlerId(myRemindLaterHandlerId);
        }

        myNotification.setSuggestionType(mySuggestionType);
        myNotification.setImportantSuggestion(myImportantSuggestion);

        if (myActions != null && !myToolwindow) {
          for (String action : myActions) {
            myNotification.addAction(new MyAnAction(action));
          }
        }
      }
      myNotification.setCollapseDirection(myRightActionsDirection ? CollapseActionsDirection.KEEP_RIGHTMOST : CollapseActionsDirection.KEEP_LEFTMOST);
      return myNotification;
    }

    @Nullable
    private NotificationListener getListener() {
      return myAddListener ? this : null;
    }

    public void setIcon(@Nullable String icon) {
      myIcon = icon;
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

    public void setRemindLaterHandlerId(String remindLaterHandlerId) {
      myRemindLaterHandlerId = remindLaterHandlerId;
    }

    private void setSuggestionType(boolean suggestionType) {
      mySuggestionType = suggestionType;
    }

    private void setImportantSuggestion(boolean importantSuggestion) {
      myImportantSuggestion = importantSuggestion;
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
      if (myNotification != null && MessageDialogBuilder.yesNo("Notification Listener", event.getDescription() + "      Expire?").guessWindowAndAsk()) {
        myNotification.expire();
        myNotification = null;
      }
    }

    private final class MyAnAction extends AnAction {
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
      public void actionPerformed(@NotNull AnActionEvent e) {
        if (myNotification == null) {
          return;
        }
        Notification.get(e);
        if (MessageDialogBuilder.yesNo("AnAction", getTemplatePresentation().getText() + "      Expire?").guessWindowAndAsk()) {
          myNotification.expire();
          myNotification = null;
        }
      }
    }
  }
}
