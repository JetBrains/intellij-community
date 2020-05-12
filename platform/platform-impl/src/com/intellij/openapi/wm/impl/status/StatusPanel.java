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
package com.intellij.openapi.wm.impl.status;

import com.intellij.ide.ClipboardSynchronizer;
import com.intellij.notification.EventLog;
import com.intellij.notification.Notification;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.JBMenuItem;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.ClickListener;
import com.intellij.util.Alarm;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author peter
 */
class StatusPanel extends JPanel {
  private Notification myCurrentNotification;
  @Nullable private String myTimeText;
  private boolean myDirty;
  private boolean myAfterClick;
  private Alarm myLogAlarm;
  private Action myCopyAction;
  private final TextPanel myTextPanel = new TextPanel() {
    @Override
    protected String getTextForPreferredSize() {
      return getText();
    }

    @Override
    public void setBounds(int x, int y, int w, int h) {
      super.setBounds(x, y, Math.min(w, StatusPanel.this.getWidth()), h);
    }

    @Override
    protected String truncateText(String text, Rectangle bounds, FontMetrics fm, Rectangle textR, Rectangle iconR, int maxWidth) {
      if (myTimeText != null && text.endsWith(myTimeText)) {
        int withoutTime = maxWidth - fm.stringWidth(myTimeText);
        Rectangle boundsForTrim = new Rectangle(withoutTime, bounds.height);
        return super.truncateText(text, boundsForTrim, fm, textR, iconR, withoutTime) + myTimeText;
      }
      return super.truncateText(text, bounds, fm, textR, iconR, maxWidth);
    }
  };

  StatusPanel() {
    super(new BorderLayout());

    setOpaque(false);

    myTextPanel.setBorder(JBUI.Borders.emptyLeft(5));
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        if (myCurrentNotification != null || myAfterClick) {
          EventLog.toggleLog(getActiveProject(), myCurrentNotification);
          myAfterClick = true;
          myTextPanel.setExplicitSize(myTextPanel.getSize());
          UIUtil.setCursor(myTextPanel, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        return true;
      }
    }.installOn(myTextPanel);

    myTextPanel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseExited(MouseEvent e) {
        myTextPanel.setExplicitSize(null);
        myTextPanel.revalidate();
        myAfterClick = false;
        if (myCurrentNotification == null) {
          UIUtil.setCursor(myTextPanel, Cursor.getDefaultCursor());
        }
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        if (SwingUtilities.isRightMouseButton(e)) {
          if (myCopyAction == null) myCopyAction = createCopyAction();

          JBPopupMenu menu = new JBPopupMenu();
          menu.add(new JBMenuItem(myCopyAction));
          menu.show(myTextPanel, e.getX(), e.getY());
        }
      }
    });

    add(myTextPanel, BorderLayout.WEST);
  }

  private Action createCopyAction() {
    ActionManager actionManager = ActionManager.getInstance();
    if (actionManager == null) return null;
    AnAction action = actionManager.getAction(IdeActions.ACTION_COPY);
    if (action == null) return null;
    return new AbstractAction(action.getTemplatePresentation().getText(), action.getTemplatePresentation().getIcon()) {
      @Override
      public void actionPerformed(ActionEvent e) {
        StringSelection content = new StringSelection(getText());
        ClipboardSynchronizer.getInstance().setContent(content, content);
      }

      @Override
      public boolean isEnabled() {
        return !getText().isEmpty();
      }
    };
  }


  @Nullable
  private Project getActiveProject() {
    // a better way of finding a project would be great
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      IdeFrame ideFrame = WindowManager.getInstance().getIdeFrame(project);
      if (ideFrame != null) {
        final JComponent frame = ideFrame.getComponent();
        if (SwingUtilities.isDescendingFrom(myTextPanel, frame)) {
          return project;
        }
      }
    }
    return null;
  }

  // Returns the alarm used for displaying status messages in the status bar, or null if the status bar is attached to a floating
  // editor window.
  @Nullable
  private Alarm getAlarm() {
    if (myLogAlarm == null || myLogAlarm.isDisposed()) {
      myLogAlarm = null; //Welcome screen
      Project project = getActiveProject();
      if (project != null && !project.isDisposed() && !Disposer.isDisposing(project)) {
        myLogAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, project);
      }
    }
    return myLogAlarm;
  }

  public boolean updateText(@Nullable String nonLogText) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    final Project project = getActiveProject();
    final Trinity<Notification, String, Long> statusMessage = EventLog.getStatusMessage(project);
    final Alarm alarm = getAlarm();
    myCurrentNotification = StringUtil.isEmpty(nonLogText) && statusMessage != null && alarm != null ? statusMessage.first : null;

    if (alarm != null) {
      alarm.cancelAllRequests();
    }

    if (myCurrentNotification != null) {
      UIUtil.setCursor(myTextPanel, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      new Runnable() {
        @Override
        public void run() {
          assert statusMessage != null;
          String text = statusMessage.second;
          if (myDirty || System.currentTimeMillis() - statusMessage.third >= DateFormatUtil.MINUTE) {
            myTimeText = " (" + StringUtil.decapitalize(DateFormatUtil.formatPrettyDateTime(statusMessage.third)) + ")";
            text += myTimeText;
          }
          else {
            myTimeText = null;
          }
          setStatusText(text);
          alarm.addRequest(this, 30000);
        }
      }.run();
    }
    else {
      myTimeText = null;
      UIUtil.setCursor(myTextPanel, Cursor.getDefaultCursor());
      myDirty = true;
      setStatusText(nonLogText);
    }

    return myCurrentNotification != null;
  }

  private void setStatusText(String text) {
    myTextPanel.setText(text);
  }

  public String getText() {
    return myTextPanel.getText();
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessibleStatusPanel();
    }
    return accessibleContext;
  }

  protected class AccessibleStatusPanel extends AccessibleJPanel {
    @Override
    public AccessibleRole getAccessibleRole() {
      return AccessibleRole.STATUS_BAR;
    }
  }
}
