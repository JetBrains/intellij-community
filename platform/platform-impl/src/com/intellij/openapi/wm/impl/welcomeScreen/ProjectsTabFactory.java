// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeCoreBundle;
import com.intellij.ide.ProjectGroupActionGroup;
import com.intellij.ide.RecentProjectListActionProvider;
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
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.WelcomeScreenTab;
import com.intellij.openapi.wm.WelcomeTabFactory;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.speedSearch.NameFilteringListModel;
import com.intellij.ui.speedSearch.SpeedSearch;
import com.intellij.util.BooleanFunction;
import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
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
    return new TabbedWelcomeScreen.DefaultWelcomeScreenTab(IdeBundle.message("welcome.screen.projects.title"),
                                                           WelcomeScreenEventCollector.TabType.TabNavProject) {

      @Override
      protected JComponent buildComponent() {
        JPanel mainPanel;
        if (RecentProjectListActionProvider.getInstance().getActions(false, true).isEmpty()) {
          mainPanel = new EmptyStateProjectsPanel(parentDisposable);
        }
        else {
          mainPanel = JBUI.Panels.simplePanel().withBorder(JBUI.Borders.empty(13, 12)).withBackground(getProjectsBackground());
          final SearchTextField projectSearch = createSearchProjectsField();
          NewRecentProjectPanel projectsPanel = createProjectsPanelWithExternalSearch(projectSearch);
          projectsPanel.setBorder(JBUI.Borders.emptyTop(10));

          JPanel northPanel =
            JBUI.Panels.simplePanel().andTransparent().withBorder(new CustomLineBorder(WelcomeScreenUIManager.getSeparatorColor(), JBUI.insetsBottom(1)) {
              @Override
              public Insets getBorderInsets(Component c) {
                return JBUI.insetsBottom(12);
              }
            });

          ActionToolbar actionsToolbar = createActionsToolbar();
          actionsToolbar.setTargetComponent(projectsPanel.myList);
          JComponent projectActionsPanel = actionsToolbar.getComponent();
          northPanel.add(projectSearch, BorderLayout.CENTER);
          northPanel.add(projectActionsPanel, BorderLayout.EAST);
          mainPanel.add(northPanel, BorderLayout.NORTH);
          mainPanel.add(projectsPanel, BorderLayout.CENTER);
          mainPanel.add(createNotificationPanel(parentDisposable), BorderLayout.SOUTH);
        }
        DnDNativeTarget target = createDropFileTarget();
        DnDSupport.createBuilder(mainPanel)
          .enableAsNativeTarget()
          .setTargetChecker(target)
          .setDropHandler(target)
          .setDisposableParent(parentDisposable)
          .install();
        return mainPanel;
      }

      @NotNull
      private NewRecentProjectPanel createProjectsPanelWithExternalSearch(@NotNull SearchTextField projectSearch) {
        return new NewRecentProjectPanel(parentDisposable, false) {
          @Override
          protected JBList<AnAction> createList(AnAction[] recentProjectActions, Dimension size) {
            JBList<AnAction> projectsList = super.createList(recentProjectActions, size);
            projectsList.setEmptyText(IdeCoreBundle.message("message.nothingToShow"));
            SpeedSearch speedSearch = new SpeedSearch();

            NameFilteringListModel<AnAction> model = new NameFilteringListModel<>(
              projectsList.getModel(), createProjectNameFunction(), speedSearch::shouldBeShowing,
              () -> StringUtil.notNullize(speedSearch.getFilter())) {
              @Override
              protected @NotNull Collection<AnAction> getElementsToFilter() {
                if (projectSearch.getText().isEmpty()) {
                  return super.getElementsToFilter();
                }
                ArrayList<AnAction> result = new ArrayList<>();
                ListModel<AnAction> originalModel = getOriginalModel();
                for (int i = 0; i < originalModel.getSize(); i++) {
                  AnAction element = originalModel.getElementAt(i);
                  result.add(element);
                  if (element instanceof ProjectGroupActionGroup) {
                    ProjectGroupActionGroup group = (ProjectGroupActionGroup)element;
                    if (!group.getGroup().isExpanded()) {
                      ContainerUtil.addAll(result, group.getChildActionsOrStubs());
                    }
                  }
                }
                return result;
              }
            };
            projectsList.setModel(model);

            projectSearch.addDocumentListener(new DocumentAdapter() {
              @Override
              public void insertUpdate(@NotNull DocumentEvent e) {
                if (StringUtil.length(projectSearch.getText()) == 1) {
                  WelcomeScreenEventCollector.logProjectSearchUsed();
                }
                super.insertUpdate(e);
              }

              @Override
              protected void textChanged(@NotNull DocumentEvent e) {
                speedSearch.updatePattern(projectSearch.getText());
                model.refilter();
                projectsList.setSelectedIndex(0);
              }
            });
            ScrollingUtil.installActions(projectsList, projectSearch);
            DumbAwareAction.create(event -> {
              AnAction selectedProject = myList.getSelectedValue();
              if (selectedProject != null) {
                selectedProject.actionPerformed(event);
              }
            }).registerCustomShortcutSet(CommonShortcuts.ENTER, projectSearch, parentDisposable);
            return projectsList;
          }
        };
      }

      @NotNull
      private SearchTextField createSearchProjectsField() {
        SearchTextField projectSearch = new SearchTextField(false);
        projectSearch.setOpaque(false);
        projectSearch.setBorder(JBUI.Borders.empty());
        JBTextField textEditor = projectSearch.getTextEditor();
        textEditor.setOpaque(false);
        textEditor.setBorder(JBUI.Borders.empty());
        textEditor.getEmptyText().setText(IdeBundle.message("welcome.screen.search.projects.empty.text"));
        textEditor.getAccessibleContext().setAccessibleName(IdeBundle.message("welcome.screen.search.projects.empty.text"));
        projectSearch.getTextEditor()
          .putClientProperty("StatusVisibleFunction", (BooleanFunction<JBTextField>)editor -> editor.getText().isEmpty());
        return projectSearch;
      }

      @NotNull
      private ActionToolbar createActionsToolbar() {
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

      @NotNull
      private ToolbarTextButtonWrapper createButtonWrapper(@NotNull AnAction action) {
        if (action instanceof ActionGroup) {
          List<AnAction> actions =
            ContainerUtil.map(((ActionGroup)action).getChildren(null), a -> ActionGroupPanelWrapper.wrapGroups(a, parentDisposable));
          return ToolbarTextButtonWrapper.wrapAsOptionButton(actions);
        }
        return ToolbarTextButtonWrapper.wrapAsTextButton(action);
      }

    };
  }

  @Override
  public boolean isApplicable() {
    return !PlatformUtils.isDataSpell();
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
}
