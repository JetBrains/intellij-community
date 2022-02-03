// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification;

import com.intellij.CommonBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.notification.impl.NotificationsConfigurable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actions.ToggleUseSoftWrapsToolbarAction;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.AncestorListenerAdapter;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.AbstractLayoutManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.AncestorEvent;
import java.awt.*;

/**
* @author peter
*/
public final class EventLogToolWindowFactory implements ToolWindowFactory, DumbAware {
  @Override
  public boolean isApplicable(@NotNull Project project) {
    return !ActionCenter.isEnabled();
  }

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    EventLog.getProjectService(project).initDefaultContent();
    toolWindow.setHelpId(EventLog.HELP_ID);
  }

  static void createContent(@NotNull Project project, @NotNull ToolWindow toolWindow, @NotNull EventLogConsole console, @NotNull @NlsContexts.TabTitle String title) {
    // update default Event Log tab title
    ContentManager contentManager = toolWindow.getContentManager();
    Content generalContent = contentManager.getContent(0);
    if (generalContent != null && contentManager.getContentCount() == 1) {
      generalContent.setDisplayName(CommonBundle.message("tab.title.general"));
    }

    Editor editor = console.getConsoleEditor();
    JPanel editorPanel = new JPanel(new AbstractLayoutManager() {
      private int getOffset() {
        return JBUIScale.scale(4);
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
      public Object getData(@NotNull @NonNls String dataId) {
        return PlatformCoreDataKeys.HELP_ID.is(dataId) ? EventLog.HELP_ID : super.getData(dataId);
      }
    };
    panel.setContent(editorPanel);
    panel.addAncestorListener(new LogShownTracker(project));

    ActionToolbar toolbar = createToolbar(project, console);
    toolbar.setTargetComponent(editor.getContentComponent());
    panel.setToolbar(toolbar.getComponent());

    Content content = ContentFactory.SERVICE.getInstance().createContent(panel, title, false);
    contentManager.addContent(content);
    contentManager.setSelectedContent(content);
  }

  private static ActionToolbar createToolbar(Project project, EventLogConsole console) {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(ActionManager.getInstance().getAction(IdeActions.ACTION_MARK_ALL_NOTIFICATIONS_AS_READ));
    group.add(new EventLogConsole.ClearLogAction(console));
    group.addSeparator();
    group.add(new EditNotificationSettings(project));

    return ActionManager.getInstance().createActionToolbar("EventLog", group, false);
  }

  private static class EditNotificationSettings extends DumbAwareAction {
    private final Project myProject;

    EditNotificationSettings(Project project) {
      super(IdeBundle.message("action.text.settings"), IdeBundle.message("action.description.edit.notification.settings"), AllIcons.General.Settings);
      myProject = project;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      ShowSettingsUtil.getInstance().editConfigurable(myProject, new NotificationsConfigurable());
    }
  }

  protected static class ToggleSoftWraps extends ToggleUseSoftWrapsToolbarAction {
    private final Editor myEditor;

    public ToggleSoftWraps(Editor editor) {
      super(SoftWrapAppliancePlaces.CONSOLE);
      myEditor = editor;
    }

    @Override
    protected Editor getEditor(@NotNull AnActionEvent e) {
      return myEditor;
    }
  }

  private static final class LogShownTracker extends AncestorListenerAdapter {
    private final Project myProject;

    LogShownTracker(Project project) {
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