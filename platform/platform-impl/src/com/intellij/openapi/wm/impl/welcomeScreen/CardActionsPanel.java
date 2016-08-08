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

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.JBCardLayout;
import com.intellij.ui.JBColor;
import com.intellij.ui.LightColors;
import com.intellij.util.ui.CenteredIcon;
import com.intellij.util.ui.GraphicsUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class CardActionsPanel extends JPanel {
  private final boolean USE_ICONS = true;
  private final JBCardLayout myLayout = new JBCardLayout();
  private final JPanel myContent = new JPanel(myLayout);
  private int nCards = 0;

  public CardActionsPanel(ActionGroup rootGroup) {
    setLayout(new GridLayout(1, 1));
    add(myContent);
    createCardForGroup(rootGroup, "root", null);
  }

  private void createCardForGroup(ActionGroup group, String cardId, final String parentId) {
    JPanel card = new JPanel(new BorderLayout());
    if (!USE_ICONS) {
      card.setOpaque(false);
    }

    JPanel withBottomFiller = new JPanel(new BorderLayout());
    if (!USE_ICONS) {
      withBottomFiller.setOpaque(true);
      withBottomFiller.setBackground(Color.white);
    }
    withBottomFiller.add(card, BorderLayout.NORTH);
    myContent.add(withBottomFiller, cardId);

    List<JComponent> components = buildComponents(group, cardId);

    JPanel componentsPanel = new JPanel(new GridLayout(components.size(), 1, 5, 5));
    if (!USE_ICONS) {
      componentsPanel.setOpaque(false);
    }
    componentsPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
    for (JComponent component : components) {
      componentsPanel.add(component);
    }
    card.add(componentsPanel, BorderLayout.CENTER);
    String title;
    title = group.getTemplatePresentation().getText();
    card.add(new HeaderPanel(title, parentId), BorderLayout.NORTH);
  }

  private List<JComponent> buildComponents(ActionGroup group, String parentId) {
    AnAction[] actions = group.getChildren(null);

    List<JComponent> components = new ArrayList<>();
    PresentationFactory factory = new PresentationFactory();

    for (AnAction action : actions) {
      Presentation presentation = action.getTemplatePresentation().clone();
      if (!USE_ICONS) {
        presentation.setIcon(null);
      }
      if (action instanceof ActionGroup) {
        ActionGroup childGroup = (ActionGroup)action;
        if (childGroup.isPopup()) {
          final String id = String.valueOf(++nCards);
          createCardForGroup(childGroup, id, parentId);

          components.add(new Button(new ActivateCard(id), presentation));
        }
        else {
          components.addAll(buildComponents(childGroup, parentId));
        }
      }
      else if (action instanceof AbstractActionWithPanel){
        final JPanel panel = ((AbstractActionWithPanel)action).createPanel();
        components.add(panel);
      }
      else {
        action.update(new AnActionEvent(null, DataManager.getInstance().getDataContext(this),
                                        ActionPlaces.WELCOME_SCREEN, presentation, ActionManager.getInstance(), 0));
        if (presentation.isVisible()) {
          components.add(new Button(action, presentation));
        }
      }
    }
    return components;
  }

  private class HeaderPanel extends JPanel {
    private HeaderPanel(String text, final String parentId) {
      super(new BorderLayout(5, 5));

      setBackground(WelcomeScreenColors.CAPTION_BACKGROUND);

      if (parentId != null) {
        AnAction back = new AnAction("Back", null, AllIcons.Actions.Back) {
          @Override
          public void actionPerformed(AnActionEvent e) {
            myLayout.swipe(myContent, parentId, JBCardLayout.SwipeDirection.BACKWARD);
          }
        };

        ActionToolbar toolbar =
          ActionManager.getInstance().createActionToolbar(ActionPlaces.CONTEXT_TOOLBAR, new DefaultActionGroup(back), true);

        JComponent toolbarComponent = toolbar.getComponent();
        toolbarComponent.setOpaque(false);
        add(toolbarComponent, BorderLayout.WEST);
      }

      JLabel title = new JLabel(text);
      title.setHorizontalAlignment(SwingConstants.CENTER);
      title.setForeground(WelcomeScreenColors.CAPTION_FOREGROUND);
      add(title, BorderLayout.CENTER);
      setBorder(new BottomLineBorder());
    }

    @Override
    public Dimension getPreferredSize() {
      return new Dimension(super.getPreferredSize().width, 28);
    }
  }

  private static class Button extends ActionButtonWithText {
    private static final Icon DEFAULT_ICON = new Icon() {
      @Override
      public void paintIcon(Component c, Graphics g, int x, int y) {
        g.setColor(LightColors.SLIGHTLY_GREEN);
        g.fillRoundRect(x + 4, y + 4, 32 - 8, 32 - 8, 8, 8);
        g.setColor(JBColor.GRAY);
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

    @Override
    public void paintComponent(Graphics g) {
      super.paintComponent(g);

      AnAction action = getAction();
      if (action instanceof ActivateCard) {
        Rectangle bounds = getBounds();

        Icon icon = AllIcons.Actions.Forward; //AllIcons.Icons.Ide.NextStepGrayed;
        int y = (bounds.height - icon.getIconHeight()) / 2;
        int x = bounds.width - icon.getIconWidth() - 15;

        if (getPopState() == POPPED) {
          final GraphicsConfig config = GraphicsUtil.setupAAPainting(g);
          g.setColor(WelcomeScreenColors.CAPTION_BACKGROUND);
          g.fillOval(x - 3, y - 3, icon.getIconWidth() + 6, icon.getIconHeight() + 6);

          g.setColor(WelcomeScreenColors.GROUP_ICON_BORDER_COLOR);
          g.drawOval(x - 3, y - 3, icon.getIconWidth() + 6, icon.getIconHeight() + 6);
          config.restore();
        }
        else {
          icon = IconLoader.getDisabledIcon(icon);
        }

        icon.paintIcon(this, g, x, y);
      }
    }

    public Button(AnAction action, Presentation presentation) {
      super(action,
            wrapIcon(presentation),
            ActionPlaces.WELCOME_SCREEN,
            new Dimension(32, 32));
      setBorder(new EmptyBorder(3, 3, 3, 3));
    }

    @Override
    public String getToolTipText() {
      return null;
    }

    @Override
    protected int horizontalTextAlignment() {
      return SwingConstants.LEFT;
    }

    @Override
    protected int iconTextSpace() {
      return 8;
    }

    private static Presentation wrapIcon(Presentation presentation) {
      Icon original = presentation.getIcon();
      CenteredIcon centered = new CenteredIcon(original != null ? original : DEFAULT_ICON, 40, 40, false);
      presentation.setIcon(centered);
      return presentation;
    }
  }

  private class ActivateCard extends AnAction {
    private final String myId;

    public ActivateCard(String id) {
      myId = id;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myLayout.swipe(myContent, myId, JBCardLayout.SwipeDirection.FORWARD);
    }
  }
}
