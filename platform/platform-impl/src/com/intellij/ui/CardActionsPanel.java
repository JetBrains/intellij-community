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
package com.intellij.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class CardActionsPanel extends JPanel {
  private final JBCardLayout myLayout = new JBCardLayout();
  private int nCards = 0;

  public CardActionsPanel(ActionGroup rootGroup) {
    setLayout(myLayout);

    createCardForGroup(rootGroup, "root", null);
  }

  private void createCardForGroup(ActionGroup group, String cardId, final String parentId) {
    JPanel card = new JPanel(new BorderLayout());

    JPanel withBottomFiller = new JPanel(new BorderLayout());
    withBottomFiller.add(card, BorderLayout.NORTH);
    add(withBottomFiller, cardId);

    List<Button> buttons = buildButtons(group, cardId);

    JPanel buttonsPanel = new JPanel(new GridLayout(buttons.size(), 1, 5, 5));
    buttonsPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
    for (Button button : buttons) {
      buttonsPanel.add(button);
    }
    card.add(buttonsPanel, BorderLayout.CENTER);
    String title;
    if (parentId != null) {
      title = group.getTemplatePresentation().getText();
    }
    else {
      title = "Quick Starts";
    }
    card.add(new HeaderPanel(title, parentId), BorderLayout.NORTH);
  }

  private List<Button> buildButtons(ActionGroup group, String parentId) {
    AnAction[] actions = group.getChildren(null);

    List<Button> buttons = new ArrayList<Button>();

    for (AnAction action : actions) {
      if (action instanceof ActionGroup) {
        ActionGroup childGroup = (ActionGroup)action;
        if (childGroup.isPopup()) {
          final String id = String.valueOf(++nCards);
          createCardForGroup(childGroup, id, parentId);
          AnAction activateCard = new AnAction() {
            @Override
            public void actionPerformed(AnActionEvent e) {
              myLayout.swipe(CardActionsPanel.this, id, JBCardLayout.SwipeDirection.FORWARD);
            }
          };

          buttons.add(new Button(activateCard, action.getTemplatePresentation()));
        }
        else {
          buttons.addAll(buildButtons(childGroup, parentId));
        }
      }
      else {
        buttons.add(new Button(action, action.getTemplatePresentation()));
      }
    }
    return buttons;
  }

  private class HeaderPanel extends JPanel {
    private HeaderPanel(String text, final String parentId) {
      super(new BorderLayout(5, 5));

      setBackground(Gray._210);

      if (parentId != null) {
        AnAction back = new AnAction("Back", null, AllIcons.Actions.Back) {
          @Override
          public void actionPerformed(AnActionEvent e) {
            myLayout.swipe(CardActionsPanel.this, parentId, JBCardLayout.SwipeDirection.BACKWARD);
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
      add(title, BorderLayout.CENTER);
    }

    @Override
    public Dimension getPreferredSize() {
      return new Dimension(super.getPreferredSize().width, 28);
    }
  }

  private static class Button extends ActionButtonWithText {
    public Button(AnAction action, Presentation presentation) {
      super(action, presentation, ActionPlaces.WELCOME_SCREEN, new Dimension(32, 32));
    }

    @Override
    protected int horizontalTextAlignment() {
      return SwingConstants.LEFT;
    }
  }
}
