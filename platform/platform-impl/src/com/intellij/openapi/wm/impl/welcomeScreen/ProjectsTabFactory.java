// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.RecentProjectListActionProvider;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.wm.WelcomeScreenTab;
import com.intellij.openapi.wm.WelcomeTabFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

import static com.intellij.openapi.actionSystem.impl.ActionButton.HIDE_DROPDOWN_ICON;
import static com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenComponentFactory.ToolbarTextButtonWrapper.wrapAsTextButton;
import static com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenComponentFactory.createErrorsLink;
import static com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager.getProjectsBackground;

public class ProjectsTabFactory implements WelcomeTabFactory {

  public static final int PRIMARY_BUTTONS_NUM = 3;

  @Override
  public @NotNull WelcomeScreenTab createWelcomeTab(@NotNull Disposable parentDisposable) {
    return new TabbedWelcomeScreen.DefaultWelcomeScreenTab("Projects") {

      @Override
      protected JComponent buildComponent() {
        if (RecentProjectListActionProvider.getInstance().getActions(false, true).isEmpty()) {
          return new EmptyStateProjectsPanel();
        }
        JPanel mainPanel = JBUI.Panels.simplePanel().withBorder(JBUI.Borders.empty(13, 12)).withBackground(getProjectsBackground());
        final SearchTextField projectSearch = createSearchProjectsField();
        NewRecentProjectPanel projectsPanel = new NewRecentProjectPanel(parentDisposable);
        projectsPanel.setBorder(JBUI.Borders.emptyTop(10));

        JPanel northPanel =
          JBUI.Panels.simplePanel().andTransparent().withBorder(new CustomLineBorder(JBColor.border(), JBUI.insetsBottom(1)) {
            @Override
            public Insets getBorderInsets(Component c) {
              return JBUI.insetsBottom(12);
            }
          });

        JComponent projectActionsPanel = createActionsToolbar().getComponent();

        northPanel.add(projectSearch, BorderLayout.CENTER);
        northPanel.add(projectActionsPanel, BorderLayout.EAST);
        mainPanel.add(northPanel, BorderLayout.NORTH);
        mainPanel.add(projectsPanel, BorderLayout.CENTER);
        mainPanel.add(createNotificationsPanel(parentDisposable), BorderLayout.SOUTH);

        return mainPanel;
      }

      @NotNull
      private SearchTextField createSearchProjectsField() {
        SearchTextField projectSearch = new SearchTextField(false);
        projectSearch.setOpaque(false);
        projectSearch.setBorder(JBUI.Borders.empty());
        projectSearch.getTextEditor().setOpaque(false);
        projectSearch.getTextEditor().setBorder(JBUI.Borders.empty());
        projectSearch.getTextEditor().getEmptyText().setText(IdeBundle.message("welcome.screen.search.projects.empty.text"));
        return projectSearch;
      }

      @NotNull
      private ActionToolbar createActionsToolbar() {
        ActionGroup quickStartActionGroup =
          (ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_WELCOME_SCREEN_QUICKSTART);
        DefaultActionGroup group = new DefaultActionGroup();
        WelcomeScreenComponentFactory.collectAllActions(group, quickStartActionGroup);

        DefaultActionGroup moreActionGroup = new DefaultActionGroup(IdeBundle.message("welcome.screen.projects.action.more.text"), true);
        Presentation moreActionPresentation = moreActionGroup.getTemplatePresentation();
        moreActionPresentation.setIcon(AllIcons.Actions.More);
        moreActionPresentation.putClientProperty(HIDE_DROPDOWN_ICON, true);

        DefaultActionGroup toolbarActionGroup = new DefaultActionGroup();
        for (AnAction child : group.getChildren(null)) {
          boolean isPrimary = toolbarActionGroup.getChildrenCount() < PRIMARY_BUTTONS_NUM;
          if (isPrimary && child.getTemplatePresentation().isVisible()) {
            toolbarActionGroup.addAction(wrapAsTextButton(child));
          }
          else {
            moreActionGroup.addAction(child);
          }
        }
        toolbarActionGroup.addAction(moreActionGroup);
        ActionToolbarImpl toolbar = new ActionToolbarImpl(ActionPlaces.WELCOME_SCREEN, toolbarActionGroup, true);
        toolbar.setOpaque(false);
        return toolbar;
      }

      private JPanel createNotificationsPanel(@NotNull Disposable parentDisposable) {
        JPanel notificationsPanel = new NonOpaquePanel(new FlowLayout(FlowLayout.RIGHT));
        notificationsPanel.setBorder(JBUI.Borders.emptyTop(10));
        notificationsPanel.add(createErrorsLink(parentDisposable));
        return notificationsPanel;
      }
    };
  }
}