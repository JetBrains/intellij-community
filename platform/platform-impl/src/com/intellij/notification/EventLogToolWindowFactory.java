// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification;

import com.intellij.icons.AllIcons;
import com.intellij.notification.impl.NotificationsConfigurable;
import com.intellij.notification.impl.NotificationsConfigurationImpl;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actions.ScrollToTheEndToolbarAction;
import com.intellij.openapi.editor.actions.ToggleUseSoftWrapsToolbarAction;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.AncestorListenerAdapter;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ui.AbstractLayoutManager;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.AncestorEvent;
import java.awt.*;

/**
* @author peter
*/
public class EventLogToolWindowFactory implements ToolWindowFactory, DumbAware {
  @Override
  public void createToolWindowContent(@NotNull final Project project, @NotNull ToolWindow toolWindow) {
    EventLog.getProjectComponent(project).initDefaultContent();
    toolWindow.setHelpId(EventLog.HELP_ID);
  }

  static void createContent(Project project, ToolWindow toolWindow, EventLogConsole console, String title) {
    // update default Event Log tab title
    ContentManager contentManager = toolWindow.getContentManager();
    Content generalContent = contentManager.getContent(0);
    if (generalContent != null && contentManager.getContentCount() == 1) {
      generalContent.setDisplayName("General");
    }

    final Editor editor = console.getConsoleEditor();
    JPanel editorPanel = new JPanel(new AbstractLayoutManager() {
      private int getOffset() {
        return JBUI.scale(4);
      }

      @Override
      public Dimension preferredLayoutSize(Container parent) {
        Dimension size = parent.getComponent(0).getPreferredSize();
        return new Dimension(size.width + getOffset(), size.height);
      }

      @Override
      public void layoutContainer(Container parent) {
        int offset = getOffset();
        parent.getComponent(0).setBounds(offset, 0, parent.getWidth() - offset, parent.getHeight());
      }
    }) {
      @Override
      public Color getBackground() {
        return ((EditorEx)editor).getBackgroundColor();
      }
    };
    editorPanel.add(editor.getComponent());

    SimpleToolWindowPanel panel = new SimpleToolWindowPanel(false, true) {
      @Override
      public Object getData(@NonNls String dataId) {
        return PlatformDataKeys.HELP_ID.is(dataId) ? EventLog.HELP_ID : super.getData(dataId);
      }
    };
    panel.setContent(editorPanel);
    panel.addAncestorListener(new LogShownTracker(project));

    ActionToolbar toolbar = createToolbar(project, editor, console);
    toolbar.setTargetComponent(editor.getContentComponent());
    panel.setToolbar(toolbar.getComponent());

    Content content = ContentFactory.SERVICE.getInstance().createContent(panel, title, false);
    contentManager.addContent(content);
    contentManager.setSelectedContent(content);
  }

  private static ActionToolbar createToolbar(Project project, Editor editor, EventLogConsole console) {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new EditNotificationSettings(project));
    group.add(new DisplayBalloons());
    group.add(new ToggleSoftWraps(editor));
    group.add(new ScrollToTheEndToolbarAction(editor));
    group.add(ActionManager.getInstance().getAction(IdeActions.ACTION_MARK_ALL_NOTIFICATIONS_AS_READ));
    group.add(new EventLogConsole.ClearLogAction(console));

    return ActionManager.getInstance().createActionToolbar("EventLog", group, false);
  }
  
  private static class DisplayBalloons extends ToggleAction implements DumbAware {
    public DisplayBalloons() {
      super("Show balloons", "Enable or suppress notification balloons", AllIcons.General.Balloon);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return NotificationsConfigurationImpl.getInstanceImpl().SHOW_BALLOONS;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      NotificationsConfigurationImpl.getInstanceImpl().SHOW_BALLOONS = state;
    }
  }

  private static class EditNotificationSettings extends DumbAwareAction {
    private final Project myProject;

    public EditNotificationSettings(Project project) {
      super("Settings", "Edit notification settings", AllIcons.General.Settings);
      myProject = project;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      ShowSettingsUtil.getInstance().editConfigurable(myProject, new NotificationsConfigurable());
    }
  }

  private static class ToggleSoftWraps extends ToggleUseSoftWrapsToolbarAction {
    private final Editor myEditor;

    public ToggleSoftWraps(Editor editor) {
      super(SoftWrapAppliancePlaces.CONSOLE);
      myEditor = editor;
    }

    @Override
    protected Editor getEditor(AnActionEvent e) {
      return myEditor;
    }
  }

  private static class LogShownTracker extends AncestorListenerAdapter {
    private final Project myProject;

    public LogShownTracker(Project project) {
      myProject = project;
    }

    @Override
    public void ancestorAdded(AncestorEvent event) {
      ToolWindow log = EventLog.getEventLog(myProject);
      if (log != null && log.isVisible()) {
        EventLog.getLogModel(myProject).logShown();
      }
    }
  }
}
