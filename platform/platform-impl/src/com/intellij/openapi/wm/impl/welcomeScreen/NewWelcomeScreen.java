// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.util.DimensionService;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.wm.WelcomeScreen;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.components.ActionLink;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public final class NewWelcomeScreen extends JPanel implements WelcomeScreen {
  public NewWelcomeScreen() {
    super(new BorderLayout());
    add(createHeaderPanel(), BorderLayout.NORTH);
    add(createFooterPanel(), BorderLayout.SOUTH);
    add(createInnerPanel(this), BorderLayout.CENTER);
  }

  private static WelcomePane createInnerPanel(WelcomeScreen screen) {
    WelcomeScreenGroup root = new WelcomeScreenGroup(null, IdeBundle.message("welcome.screen.quick.start.action.text"));

    ActionManager actionManager = ActionManager.getInstance();
    ActionGroup quickStart = (ActionGroup)actionManager.getAction(IdeActions.GROUP_WELCOME_SCREEN_QUICKSTART);
    for (AnAction child : quickStart.getChildren(null)) {
      root.add(child);
    }

    root.add(buildRootGroup(AllIcons.General.Settings, IdeBundle.message("welcome.screen.configure.action.text"), IdeActions.GROUP_WELCOME_SCREEN_CONFIGURE));
    root.add(buildRootGroup(AllIcons.Actions.Help, IdeBundle.message("welcome.screen.action.docs.how.tos.action.text"), IdeActions.GROUP_WELCOME_SCREEN_DOC));

    return new WelcomePane(root, screen);
  }

  private static WelcomeScreenGroup buildRootGroup(@NotNull Icon groupIcon, @NotNull @NlsActions.ActionText String groupText, @NotNull String groupId) {
    WelcomeScreenGroup result = new WelcomeScreenGroup(groupIcon, groupText);
    ActionGroup docsActions = (ActionGroup)ActionManager.getInstance().getAction(groupId);
    for (AnAction child : docsActions.getChildren(null)) {
      result.add(child);
    }
    return result;
  }

  private static JPanel createFooterPanel() {
    JLabel versionLabel = new JLabel(IdeBundle.message("label.version.0.1.build.2", ApplicationNamesInfo.getInstance().getFullProductName(),
                                                       ApplicationInfo.getInstance().getFullVersion(),
                                                       ApplicationInfo.getInstance().getBuild().asStringWithoutProductCode()));
    makeSmallFont(versionLabel);
    versionLabel.setForeground(WelcomeScreenColors.FOOTER_FOREGROUND);

    JPanel footerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    footerPanel.setBackground(WelcomeScreenColors.FOOTER_BACKGROUND);
    footerPanel.setBorder(new EmptyBorder(2, 5, 2, 5) {
      @Override
      public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        g.setColor(WelcomeScreenColors.BORDER_COLOR);
        g.drawLine(x, y, x + width, y);
      }
    });
    footerPanel.add(versionLabel);
    footerPanel.add(makeSmallFont(new JLabel(".  ")));
    footerPanel.add(makeSmallFont(new ActionLink(IdeBundle.message("link.check"), e -> {
        UpdateChecker.updateAndShowResult(null);
    })));
    footerPanel.add(makeSmallFont(new JLabel(IdeBundle.message("welcome.screen.check.for.updates.comment"))));
    return footerPanel;
  }

  private static JComponent makeSmallFont(JComponent label) {
    label.setFont(label.getFont().deriveFont((float)10));
    return label;
  }

  private static JPanel createHeaderPanel() {
    JPanel header = new JPanel(new BorderLayout());
    JLabel welcome = new JLabel(IdeBundle.message("label.welcome.to.0", ApplicationNamesInfo.getInstance().getFullProductName()),
                                IconLoader.getIcon(ApplicationInfoEx.getInstanceEx().getWelcomeScreenLogoUrl(), NewWelcomeScreen.class.getClassLoader()),
                                SwingConstants.LEFT);
    welcome.setBorder(new EmptyBorder(10, 15, 10, 15));
    welcome.setFont(welcome.getFont().deriveFont((float) 32));
    welcome.setIconTextGap(20);
    welcome.setForeground(WelcomeScreenColors.WELCOME_HEADER_FOREGROUND);
    header.add(welcome);
    header.setBackground(WelcomeScreenColors.WELCOME_HEADER_BACKGROUND);

    header.setBorder(new BottomLineBorder());
    return header;
  }

  @Override
  public JComponent getWelcomePanel() {
    return this;
  }

  @Override
  public void setupFrame(JFrame frame) {
    frame.setResizable(false);
    frame.pack();
    Point location = DimensionService.getInstance().getLocation(WelcomeFrame.DIMENSION_KEY, null);
    Rectangle screenBounds = ScreenUtil.getScreenRectangle(location != null ? location : new Point(0, 0));
    frame.setLocation(new Point(
      screenBounds.x + (screenBounds.width - frame.getWidth()) / 2,
      screenBounds.y + (screenBounds.height - frame.getHeight()) / 3
    ));
  }

  @Override
  public void dispose() {
  }

  public static boolean isNewWelcomeScreen(@NotNull AnActionEvent e) {
    return e.getPlace() == ActionPlaces.WELCOME_SCREEN;
  }

  public static void updateNewProjectIconIfWelcomeScreen(@NotNull AnActionEvent e) {
    if (isNewWelcomeScreen(e)) {
      Presentation presentation = e.getPresentation();
      presentation.setIcon(AllIcons.General.Add);
      if (FlatWelcomeFrame.USE_TABBED_WELCOME_SCREEN) {
        presentation.setIcon(AllIcons.Welcome.CreateNewProjectTab);
        presentation.setSelectedIcon(AllIcons.Welcome.CreateNewProjectTabSelected);
      }
    }
  }

  private static final class WelcomeScreenGroup extends DefaultActionGroup {
    private WelcomeScreenGroup(Icon icon, @NlsActions.ActionText String text, AnAction... actions) {
      super(text, true);
      for (AnAction action : actions) {
        add(action);
      }

      getTemplatePresentation().setText(text);
      getTemplatePresentation().setIcon(icon);
    }
  }
}
