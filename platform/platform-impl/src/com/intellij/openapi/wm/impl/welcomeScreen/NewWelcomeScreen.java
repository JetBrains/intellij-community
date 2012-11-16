/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.ide.plugins.PluginManagerUISettings;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.WelcomeScreen;
import com.intellij.ui.Gray;
import com.intellij.ui.LightColors;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class NewWelcomeScreen extends JPanel implements WelcomeScreen {
  public NewWelcomeScreen(JRootPane rootPane) {
    super(new BorderLayout());
    add(createHeaderPanel(), BorderLayout.NORTH);
    add(createFooterPanel(), BorderLayout.SOUTH);
    add(createInnerPanel(), BorderLayout.CENTER);
  }

  private WelcomePane createInnerPanel() {
    Icon placeholderIcon = new Icon() {
      @Override
      public void paintIcon(Component c, Graphics g, int x, int y) {
        g.setColor(LightColors.SLIGHTLY_GREEN);
        g.fillRoundRect(x + 4, y + 4, 32 - 8, 32 - 8, 8, 8);
        g.setColor(Color.GRAY);
        g.drawRoundRect(x + 4, y + 4, 32 - 8, 32 - 8, 8, 8);
      }

      @Override
      public int getIconWidth() {
        return 32;
      }

      @Override
      public int getIconHeight() {
        return 32;
      }
    };


    WelcomeScreenGroup root = new WelcomeScreenGroup(null, "Root");

    ActionManager actionManager = ActionManager.getInstance();
    ActionGroup quickStart = (ActionGroup)actionManager.getAction(IdeActions.GROUP_WELCOME_SCREEN_QUICKSTART);
    for (AnAction child : quickStart.getChildren(null)) {
      root.add(child);
    }

    WelcomeScreenGroup settings = new WelcomeScreenGroup(placeholderIcon, "Configure");
    root.add(settings);
    settings.add(new WelcomeScreenAction(placeholderIcon, "Plugins") {
      @Override
      public void actionPerformed(AnActionEvent e) {
        final PluginManagerConfigurable configurable = new PluginManagerConfigurable(PluginManagerUISettings.getInstance());
        ShowSettingsUtil.getInstance().editConfigurable(NewWelcomeScreen.this, configurable);
      }
    });

    settings.add(actionManager.getAction("ShowSettings"));

    WelcomeScreenGroup docs = new WelcomeScreenGroup(placeholderIcon, "Docs and How-Tos");
    root.add(docs);
    ActionGroup docsActions = (ActionGroup)actionManager.getAction(IdeActions.GROUP_WELCOME_SCREEN_DOC);
    for (AnAction child : docsActions.getChildren(null)) {
      docs.add(child);
    }

    return new WelcomePane(root);
  }

  private JPanel createFooterPanel() {
    JLabel versionLabel = new JLabel(ApplicationNamesInfo.getInstance().getFullProductName() +
                             " " +
                             ApplicationInfo.getInstance().getFullVersion() +
                             " Build " +
                             ApplicationInfo.getInstance().getBuild().asStringWithoutProductCode());
    versionLabel.setFont(versionLabel.getFont().deriveFont((float) 10));

    JPanel footerPanel = new JPanel(new BorderLayout());
    footerPanel.setBackground(Gray._210);
    footerPanel.setBorder(new EmptyBorder(5, 5, 5, 5) {
      @Override
      public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        g.setColor(Gray._190);
        g.drawLine(x, y, x + width, y);
      }
    });
    footerPanel.add(versionLabel, BorderLayout.WEST);
    return footerPanel;
  }

  private JPanel createHeaderPanel() {
    JPanel header = new JPanel(new BorderLayout());
    JLabel welcome = new JLabel("Welcome to " + ApplicationNamesInfo.getInstance().getFullProductName(), IconLoader.getIcon("/idea_logo_welcome.png"), SwingConstants.LEFT);
    welcome.setBorder(new EmptyBorder(15, 15, 15, 15));
    welcome.setFont(welcome.getFont().deriveFont((float) 32));
    welcome.setIconTextGap(30);
    welcome.setForeground(Gray._80);
    header.add(welcome);
    header.setBackground(Gray._220);

    header.setBorder(new EmptyBorder(0, 0, 1, 0) {
      @Override
      public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        g.setColor(Gray._190);
        g.drawLine(x, y + height - 1, x + width, y + height - 1);
      }
    });
    return header;
  }

  @Override
  public JComponent getWelcomePanel() {
    return this;
  }

  @Override
  public void dispose() {
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

  private class WelcomeScreenAction extends AnAction {
    private WelcomeScreenAction(Icon icon, String text) {
      super(text, null, icon);
    }

    @Override
    public boolean displayTextInToolbar() {
      return true;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      Messages.showInfoMessage(NewWelcomeScreen.this, getTemplatePresentation().getText() + " is not implemented!", "Not Implemented!!!");
    }
  }
}
