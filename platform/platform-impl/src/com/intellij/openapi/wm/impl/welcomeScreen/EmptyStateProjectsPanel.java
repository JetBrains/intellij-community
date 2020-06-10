// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Couple;
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

import static com.intellij.openapi.wm.impl.welcomeScreen.ProjectsTabFactory.PRIMARY_BUTTONS_NUM;
import static com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenComponentFactory.*;

public class EmptyStateProjectsPanel extends JPanel {

  public EmptyStateProjectsPanel() {
    setBackground(WelcomeScreenUIManager.getMainAssociatedComponentBackground());
    JPanel mainPanel = new NonOpaquePanel(new VerticalFlowLayout());
    mainPanel.setBorder(JBUI.Borders.emptyTop(130));

    mainPanel.add(createTitle());
    mainPanel.add(createCommentLabel(IdeBundle.message("welcome.screen.empty.projects.create.comment")));
    mainPanel.add(createCommentLabel(IdeBundle.message("welcome.screen.empty.projects.open.comment")));

    Couple<DefaultActionGroup> mainAndMore =
      splitActionGroupToMainAndMore((ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_WELCOME_SCREEN_QUICKSTART),
                                    PRIMARY_BUTTONS_NUM);

    ActionGroup main = new DefaultActionGroup(
      ContainerUtil.map2List(mainAndMore.getFirst().getChildren(null), LargeIconWithTextWrapper::wrapAsBigIconWithText));

    ActionToolbarImpl actionsToolbar = createActionsToolbar(main);
    mainPanel.add(new Wrapper(new FlowLayout(), actionsToolbar.getComponent()));

    DefaultActionGroup moreActionGroup = mainAndMore.getSecond();
    if (moreActionGroup.getChildrenCount() > 0) {
      LinkLabel<String> moreLink = createLinkWithPopup(moreActionGroup);
      JPanel moreLinkPanel = new Wrapper(new FlowLayout(), moreLink);
      mainPanel.add(moreLinkPanel);
    }

    add(mainPanel);
  }

  @NotNull
  static ActionToolbarImpl createActionsToolbar(ActionGroup actionGroup) {
    ActionToolbarImpl actionToolbar = new ActionToolbarImpl(ActionPlaces.WELCOME_SCREEN, actionGroup, true);
    actionToolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
    actionToolbar.setBorder(JBUI.Borders.emptyTop(50));
    actionToolbar.setOpaque(false);
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
