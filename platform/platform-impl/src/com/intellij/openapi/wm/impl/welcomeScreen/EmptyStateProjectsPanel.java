// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.components.AnActionLink;
import com.intellij.ui.components.DropDownLink;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.FocusUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenActionsUtil.LargeIconWithTextWrapper;
import static com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenActionsUtil.splitAndWrapActions;
import static com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenComponentFactory.getApplicationTitle;

final class EmptyStateProjectsPanel extends BorderLayoutPanel {
  EmptyStateProjectsPanel(@NotNull Disposable parentDisposable) {
    setBackground(WelcomeScreenUIManager.getMainAssociatedComponentBackground());
    JPanel mainPanel = new NonOpaquePanel(new VerticalFlowLayout());
    mainPanel.setBorder(JBUI.Borders.emptyTop(103));

    mainPanel.add(createTitle());
    mainPanel.add(createCommentLabel(IdeBundle.message("welcome.screen.empty.projects.create.comment")));
    mainPanel.add(createCommentLabel(IdeBundle.message("welcome.screen.empty.projects.open.comment")));

    Couple<DefaultActionGroup> mainAndMore =
      splitAndWrapActions((ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_WELCOME_SCREEN_QUICKSTART_EMPTY_STATE),
                          action -> ActionGroupPanelWrapper.wrapGroups(action, parentDisposable),
                          ProjectsTabFactory.PRIMARY_BUTTONS_NUM);
    ActionGroup main = new DefaultActionGroup(
      ContainerUtil.map2List(mainAndMore.getFirst().getChildren(null), LargeIconWithTextWrapper::wrapAsBigIconWithText));

    ActionToolbarImpl actionsToolbar = createActionsToolbar(main);
    mainPanel.add(new Wrapper(new FlowLayout(), actionsToolbar.getComponent()));

    DefaultActionGroup moreActionGroup = mainAndMore.getSecond();
    if (moreActionGroup.getChildrenCount() > 0) {
      JComponent actionLink;
      if (moreActionGroup.getChildrenCount() == 1) {
        AnAction action = moreActionGroup.getChildren(null)[0];
        actionLink = new AnActionLink(action, ActionPlaces.WELCOME_SCREEN);
        if (action instanceof OpenAlienProjectAction) {
          ((OpenAlienProjectAction)action).scheduleUpdate(actionLink);
        }
      }
      else {
        actionLink = createLinkWithPopup(moreActionGroup);
      }

      JPanel moreLinkPanel = new Wrapper(new FlowLayout(), actionLink);
      moreLinkPanel.setBorder(JBUI.Borders.emptyTop(5));
      mainPanel.add(moreLinkPanel);
    }

    addToCenter(mainPanel);
  }

  @NotNull
  private static ActionToolbarImpl createActionsToolbar(ActionGroup actionGroup) {
    ActionToolbarImpl actionToolbar = new ActionToolbarImpl(ActionPlaces.WELCOME_SCREEN, actionGroup, true) {
      private boolean wasFocusRequested = false;
      @Override
      protected void actionsUpdated(boolean forced,
                                    @NotNull List<? extends AnAction> newVisibleActions) {
        super.actionsUpdated(forced, newVisibleActions);
        if (forced && !newVisibleActions.isEmpty() && getComponents().length > 0 && !wasFocusRequested) {
          ObjectUtils.doIfNotNull(FocusUtil.findFocusableComponentIn(getComponents()[0], null),
                                  component -> {
                                    wasFocusRequested =true;
                                    return IdeFocusManager.getGlobalInstance().requestFocus(component, true);
                                  });
        }
      }
    };
    actionToolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
    actionToolbar.setTargetComponent(actionToolbar.getComponent());
    actionToolbar.setBorder(JBUI.Borders.emptyTop(27));
    actionToolbar.setTargetComponent(actionToolbar.getComponent());
    actionToolbar.setOpaque(false);
    return actionToolbar;
  }

  @NotNull
  private static DropDownLink<String> createLinkWithPopup(@NotNull ActionGroup group) {
    return new DropDownLink<>(group.getTemplateText(), link
      -> JBPopupFactory.getInstance().createActionGroupPopup(
      null, group, DataManager.getInstance().getDataContext(link),
      JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true));
  }

  @NotNull
  private static JBLabel createTitle() {
    JBLabel titleLabel = new JBLabel(getApplicationTitle(), SwingConstants.CENTER);
    titleLabel.setOpaque(false);
    Font componentFont = titleLabel.getFont();
    titleLabel.setFont(componentFont.deriveFont(componentFont.getSize() + (float)JBUIScale.scale(13)).deriveFont(Font.BOLD));
    titleLabel.setBorder(JBUI.Borders.emptyBottom(17));
    return titleLabel;
  }

  @NotNull
  static JBLabel createCommentLabel(@NlsContexts.HintText @NotNull String text) {
    JBLabel commentFirstLabel = new JBLabel(text, SwingConstants.CENTER);
    commentFirstLabel.setOpaque(false);
    commentFirstLabel.setForeground(UIUtil.getContextHelpForeground());
    return commentFirstLabel;
  }
}