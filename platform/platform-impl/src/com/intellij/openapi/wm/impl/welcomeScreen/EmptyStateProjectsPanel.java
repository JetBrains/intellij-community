// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

import static com.intellij.openapi.wm.impl.welcomeScreen.ProjectsTabFactory.PRIMARY_BUTTONS_NUM;
import static com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenComponentFactory.LargeIconWithTextWrapper.wrapAsBigIconWithText;
import static com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenComponentFactory.getApplicationTitle;

public class EmptyStateProjectsPanel extends JPanel {

  public EmptyStateProjectsPanel() {
    JPanel mainPanel = new NonOpaquePanel(new VerticalFlowLayout());
    mainPanel.setBorder(JBUI.Borders.emptyTop(130));
    mainPanel.setBackground(WelcomeScreenUIManager.getProjectsBackground());

    mainPanel.add(createTitle());
    mainPanel.add(createCommentLabel(IdeBundle.message("welcome.screen.empty.projects.create.comment")));
    mainPanel.add(createCommentLabel(IdeBundle.message("welcome.screen.empty.projects.open.comment")));

    ActionGroup quickStartActionGroup =
      (ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_WELCOME_SCREEN_QUICKSTART);
    DefaultActionGroup group = new DefaultActionGroup();
    WelcomeScreenComponentFactory.collectAllActions(group, quickStartActionGroup);
    AnAction[] actions = group.getChildren(null);

    ActionToolbarImpl actionsToolbar = createActionsToolbar(Arrays.copyOfRange(actions, 0, PRIMARY_BUTTONS_NUM));
    mainPanel.add(new Wrapper(new FlowLayout(), actionsToolbar.getComponent()));

    if (PRIMARY_BUTTONS_NUM < actions.length) {
      ActionGroup moreActionGroup = new DefaultActionGroup(IdeBundle.message("welcome.screen.empty.projects.more.text"),
                                                           ContainerUtil.newArrayList(actions, PRIMARY_BUTTONS_NUM, actions.length));
      LinkLabel<String> moreLink = createLinkWithPopup(moreActionGroup);
      JPanel moreLinkPanel = new Wrapper(new FlowLayout(), moreLink);
      mainPanel.add(moreLinkPanel);
    }

    add(mainPanel);
  }

  @NotNull
  static ActionToolbarImpl createActionsToolbar(AnAction... actions) {
    DefaultActionGroup mainActionGroup = new DefaultActionGroup();
    for (AnAction action : actions) {
      mainActionGroup.addAction(wrapAsBigIconWithText(action));
    }
    ActionToolbarImpl actionToolbar = new ActionToolbarImpl(ActionPlaces.WELCOME_SCREEN, mainActionGroup, true);
    actionToolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
    actionToolbar.setBorder(JBUI.Borders.emptyTop(50));
    return actionToolbar;
  }

  @NotNull
  static LinkLabel<String> createLinkWithPopup(@NotNull ActionGroup actionGroup) {
    LinkLabel<String> moreLink =
      new LinkLabel<>(actionGroup.getTemplateText(), AllIcons.General.LinkDropTriangle, (s, __) ->
      {
        JBPopupFactory.getInstance().createActionGroupPopup(null, actionGroup,
                                                            DataManager
                                                              .getInstance()
                                                              .getDataContext(s),
                                                            JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                                                            true).showUnderneathOf(s);
      }, null);
    moreLink.setHorizontalTextPosition(SwingConstants.LEADING);
    moreLink.setBorder(JBUI.Borders.emptyTop(30));
    return moreLink;
  }

  @NotNull
  static JBLabel createTitle() {
    JBLabel titleLabel = new JBLabel(getApplicationTitle(), SwingConstants.CENTER);
    titleLabel.setOpaque(false);
    Font componentFont = titleLabel.getFont();
    titleLabel.setFont(componentFont.deriveFont(componentFont.getSize() + (float)JBUIScale.scale(13)).deriveFont(Font.BOLD));
    titleLabel.setBorder(JBUI.Borders.emptyBottom(20));
    return titleLabel;
  }

  @NotNull
  static JBLabel createCommentLabel(@NotNull String text) {
    JBLabel commentFirstLabel = new JBLabel(text, SwingConstants.CENTER);
    commentFirstLabel.setOpaque(false);
    commentFirstLabel.setForeground(UIUtil.getContextHelpForeground());
    return commentFirstLabel;
  }
}
