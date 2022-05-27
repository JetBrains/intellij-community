// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.RecentProjectListActionProvider;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.dnd.DnDEvent;
import com.intellij.ide.dnd.DnDNativeTarget;
import com.intellij.ide.dnd.DnDSupport;
import com.intellij.ide.dnd.FileCopyPasteUtil;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.wm.WelcomeScreenTab;
import com.intellij.openapi.wm.WelcomeTabFactory;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService;
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.*;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;

import static com.intellij.openapi.actionSystem.impl.ActionButton.HIDE_DROPDOWN_ICON;
import static com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenActionsUtil.ToolbarTextButtonWrapper;
import static com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenActionsUtil.splitAndWrapActions;
import static com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenComponentFactory.createNotificationPanel;
import static com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager.getProjectsBackground;

final class ProjectsTabFactory implements WelcomeTabFactory {
  static final int PRIMARY_BUTTONS_NUM = 3;

  @Override
  public @NotNull WelcomeScreenTab createWelcomeTab(@NotNull Disposable parentDisposable) {
    return new ProjectsTab(parentDisposable);
  }

  @Override
  public boolean isApplicable() {
    return !PlatformUtils.isDataSpell();
  }

  private static class ProjectsTab extends TabbedWelcomeScreen.DefaultWelcomeScreenTab {
    private final @NotNull Wrapper wrapper = new Wrapper();
    private final @NotNull Disposable parentDisposable;
    private final @NotNull JComponent recentProjectsPanel;
    private final @NotNull JComponent emptyStatePanel;
    private @NotNull PanelState panelState;

    private ProjectsTab(@NotNull Disposable parentDisposable) {
      super(IdeBundle.message("welcome.screen.projects.title"), WelcomeScreenEventCollector.TabType.TabNavProject);
      this.parentDisposable = parentDisposable;
      this.panelState = getCurrentState();
      this.recentProjectsPanel = createRecentProjectsPanel();
      this.emptyStatePanel = createEmptyStatePanel();

      updateState(panelState);

      MessageBusConnection connect = ApplicationManager.getApplication().getMessageBus().connect(parentDisposable);
      connect.subscribe(CloneableProjectsService.TOPIC, new CloneableProjectsService.CloneProjectListener() {
        @Override
        public void onCloneAdded(@NotNull ProgressIndicatorEx progressIndicator, @NotNull TaskInfo taskInfo) {
          checkState();
        }

        @Override
        public void onCloneRemoved() {
          checkState();
        }
      });
      connect.subscribe(RecentProjectsManager.RECENT_PROJECTS_CHANGE_TOPIC, new RecentProjectsManager.RecentProjectsChange() {
        @Override
        public void change() {
          checkState();
        }
      });
    }

    @Override
    protected @NotNull JComponent buildComponent() {
      return wrapper;
    }

    private void checkState() {
      PanelState currentState = getCurrentState();
      if (currentState == panelState) {
        return;
      }

      updateState(currentState);
    }

    private void updateState(@NotNull PanelState currentPanelState) {
      if (currentPanelState == PanelState.EMPTY) {
        wrapper.setContent(emptyStatePanel);
      }
      else {
        wrapper.setContent(recentProjectsPanel);
      }

      panelState = currentPanelState;
      wrapper.repaint();
    }

    private static @NotNull PanelState getCurrentState() {
      List<RecentProjectTreeItem> recentProjects = RecentProjectListActionProvider.getInstance().collectProjects();
      List<CloneableProjectItem> cloneableProjects = CloneableProjectsService.getInstance().collectCloneableProjects();

      return !recentProjects.isEmpty() || !cloneableProjects.isEmpty() ? PanelState.NOT_EMPTY : PanelState.EMPTY;
    }

    private @NotNull JComponent createRecentProjectsPanel() {
      JPanel recentProjectsPanel = JBUI.Panels.simplePanel()
        .withBorder(JBUI.Borders.empty(13, 12))
        .withBackground(getProjectsBackground());
      RecentProjectFilteringTree recentProjectTree = RecentProjectPanelComponentFactory.createComponent(
        parentDisposable, ProjectCollectors.all
      );
      JComponent treeComponent = recentProjectTree.getComponent();
      JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(treeComponent, true);
      scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
      scrollPane.setOpaque(false);
      SearchTextField projectSearch = recentProjectTree.installSearchField();

      JPanel northPanel = JBUI.Panels.simplePanel()
        .andTransparent()
        .withBorder(new CustomLineBorder(WelcomeScreenUIManager.getSeparatorColor(), JBUI.insetsBottom(1)) {
          @Override
          public Insets getBorderInsets(Component c) {
            return JBUI.insetsBottom(12);
          }
        });

      ActionToolbar actionsToolbar = createActionsToolbar();
      actionsToolbar.setTargetComponent(scrollPane);
      JComponent projectActionsPanel = actionsToolbar.getComponent();
      northPanel.add(projectSearch, BorderLayout.CENTER);
      northPanel.add(projectActionsPanel, BorderLayout.EAST);
      recentProjectsPanel.add(northPanel, BorderLayout.NORTH);
      recentProjectsPanel.add(scrollPane, BorderLayout.CENTER);
      recentProjectsPanel.add(createNotificationPanel(parentDisposable), BorderLayout.SOUTH);

      initDnD(treeComponent);

      return recentProjectsPanel;
    }

    private @NotNull JComponent createEmptyStatePanel() {
      EmptyStateProjectsPanel emptyStateProjectsPanel = new EmptyStateProjectsPanel(parentDisposable);
      initDnD(emptyStateProjectsPanel);

      return emptyStateProjectsPanel;
    }

    private void initDnD(@NotNull JComponent component) {
      DnDNativeTarget target = createDropFileTarget();
      DnDSupport.createBuilder(component)
        .enableAsNativeTarget()
        .setTargetChecker(target)
        .setDropHandler(target)
        .setDisposableParent(parentDisposable)
        .install();
    }

    private @NotNull ActionToolbar createActionsToolbar() {
      Couple<DefaultActionGroup> mainAndMore =
        splitAndWrapActions((ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_WELCOME_SCREEN_QUICKSTART_PROJECTS_STATE),
                            action -> ActionGroupPanelWrapper.wrapGroups(action, parentDisposable),
                            PRIMARY_BUTTONS_NUM);
      DefaultActionGroup toolbarActionGroup = new DefaultActionGroup(
        ContainerUtil.map2List(mainAndMore.getFirst().getChildren(null), action -> createButtonWrapper(action)));
      ActionGroup moreActionGroup = mainAndMore.getSecond();

      Presentation moreActionPresentation = moreActionGroup.getTemplatePresentation();
      moreActionPresentation.setIcon(AllIcons.Actions.More);
      moreActionPresentation.putClientProperty(HIDE_DROPDOWN_ICON, true);

      toolbarActionGroup.addAction(moreActionGroup);
      ActionToolbarImpl toolbar = new ActionToolbarImpl(ActionPlaces.WELCOME_SCREEN, toolbarActionGroup, true) {
        @Override
        protected @NotNull ActionButton createToolbarButton(@NotNull AnAction action,
                                                            ActionButtonLook look,
                                                            @NotNull String place,
                                                            @NotNull Presentation presentation,
                                                            @NotNull Dimension minimumSize) {
          ActionButton toolbarButton = super.createToolbarButton(action, look, place, presentation, minimumSize);
          toolbarButton.setFocusable(true);
          return toolbarButton;
        }
      };
      toolbar.setOpaque(false);
      toolbar.setReservePlaceAutoPopupIcon(false);
      return toolbar;
    }

    private @NotNull ToolbarTextButtonWrapper createButtonWrapper(@NotNull AnAction action) {
      if (action instanceof ActionGroup) {
        List<AnAction> actions =
          ContainerUtil.map(((ActionGroup)action).getChildren(null), a -> ActionGroupPanelWrapper.wrapGroups(a, parentDisposable));
        return ToolbarTextButtonWrapper.wrapAsOptionButton(actions);
      }
      return ToolbarTextButtonWrapper.wrapAsTextButton(action);
    }

    private static @NotNull DnDNativeTarget createDropFileTarget() {
      return new DnDNativeTarget() {
        @Override
        public boolean update(@NotNull DnDEvent event) {
          if (!FileCopyPasteUtil.isFileListFlavorAvailable(event)) {
            return false;
          }
          event.setDropPossible(true);
          return false;
        }

        @Override
        public void drop(@NotNull DnDEvent event) {
          List<File> files = FileCopyPasteUtil.getFileListFromAttachedObject(event.getAttachedObject());
          if (!files.isEmpty()) {
            ProjectUtil.tryOpenFiles(null, ContainerUtil.map((List<? extends File>)files, file -> file.toPath()), "WelcomeFrame");
          }
        }
      };
    }

    private enum PanelState {
      EMPTY,
      NOT_EMPTY
    }
  }
}
