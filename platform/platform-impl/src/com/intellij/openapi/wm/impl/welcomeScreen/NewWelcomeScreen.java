/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.util.DimensionService;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.WelcomeScreen;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class NewWelcomeScreen extends JPanel implements WelcomeScreen {

  public NewWelcomeScreen() {
    super(new BorderLayout());
    add(createHeaderPanel(), BorderLayout.NORTH);
    add(createFooterPanel(), BorderLayout.SOUTH);
    add(createInnerPanel(this), BorderLayout.CENTER);
  }

  private static WelcomePane createInnerPanel(WelcomeScreen screen) {
    WelcomeScreenGroup root = new WelcomeScreenGroup(null, "Quick Start");

    ActionManager actionManager = ActionManager.getInstance();
    ActionGroup quickStart = (ActionGroup)actionManager.getAction(IdeActions.GROUP_WELCOME_SCREEN_QUICKSTART);
    for (AnAction child : quickStart.getChildren(null)) {
      root.add(child);
    }

    root.add(buildRootGroup(AllIcons.General.Settings, "Configure", IdeActions.GROUP_WELCOME_SCREEN_CONFIGURE));
    root.add(buildRootGroup(AllIcons.Actions.Help, "Docs and How-Tos", IdeActions.GROUP_WELCOME_SCREEN_DOC));

    // so, we sure this is the last action
    final AnAction register = actionManager.getAction("WelcomeScreen.Register");
    if (register != null) {
      root.add(register);
    }
    return new WelcomePane(root, screen);
  }

  private static WelcomeScreenGroup buildRootGroup(Icon groupIcon, String groupText, String groupId) {
    WelcomeScreenGroup docs = new WelcomeScreenGroup(groupIcon, groupText);
    ActionGroup docsActions = (ActionGroup)ActionManager.getInstance().getAction(groupId);
    for (AnAction child : docsActions.getChildren(null)) {
      docs.add(child);
    }
    return docs;
  }

  private static JPanel createFooterPanel() {
    JLabel versionLabel = new JLabel(ApplicationNamesInfo.getInstance().getFullProductName() +
                             " " +
                             ApplicationInfo.getInstance().getFullVersion() +
                             " Build " +
                             ApplicationInfo.getInstance().getBuild().asStringWithoutProductCode());
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
    footerPanel.add(makeSmallFont(new LinkLabel("Check", null, new LinkListener() {
      @Override
      public void linkSelected(LinkLabel aSource, Object aLinkData) {
        UpdateChecker.updateAndShowResult(null, null);
      }
    })));
    footerPanel.add(makeSmallFont(new JLabel(" for updates now.")));
    return footerPanel;
  }

  private static JLabel makeSmallFont(JLabel label) {
    label.setFont(label.getFont().deriveFont((float)10));
    return label;
  }

  private static JPanel createHeaderPanel() {
    JPanel header = new JPanel(new BorderLayout());
    JLabel welcome = new JLabel("Welcome to " + ApplicationNamesInfo.getInstance().getFullProductName(),
                                IconLoader.getIcon(ApplicationInfoEx.getInstanceEx().getWelcomeScreenLogoUrl()),
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

  private static class WelcomeScreenGroup extends DefaultActionGroup {
    private WelcomeScreenGroup(Icon icon, String text, AnAction... actions) {
      super(text, true);
      for (AnAction action : actions) {
        add(action);
      }

      getTemplatePresentation().setText(text);
      getTemplatePresentation().setIcon(icon);
    }
  }
}
