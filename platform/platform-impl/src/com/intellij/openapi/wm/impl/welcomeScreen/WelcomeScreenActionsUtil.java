// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.impl.ActualActionUiKind;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBOptionButton;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

import static com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager.getActionsButtonBackground;

public final class WelcomeScreenActionsUtil {
  @ApiStatus.Internal
  public static final DataKey<Boolean> NON_MODAL_WELCOME_SCREEN = DataKey.create("NON_MODAL_WELCOME_SCREEN");

  @ApiStatus.Internal
  public static final Key<Icon> TEXT_BUTTON_ICON = Key.create("WelcomeScreenActionsUtil.TEXT_BUTTON_ICON");

  @ApiStatus.Internal
  public static final Key<Icon> TEXT_BUTTON_RIGHT_ICON = Key.create("WelcomeScreenActionsUtil.TEXT_BUTTON_RIGHT_ICON");

  @ApiStatus.Internal
  public static final Key<@Nls String> INLINE_ACTIONS_POPUP_AD_TEXT = Key.create("WelcomeScreenActionsUtil.POPUP_HINT");

  public static @NotNull CustomComponentAction createToolbarTextButtonAction(@NotNull AnAction action) {
    return new CustomComponentAction() {
      @Override
      public @NotNull JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
        WelcomeScreenTextButton textButton = new WelcomeScreenTextButton();
        JBOptionButton button = textButton.button;
        button.setAddSeparator(false);

        button.setAction(new AbstractAction() {
          @Override
          public void actionPerformed(ActionEvent e) {
            performAnActionForComponent(action, button);
          }
        });
        List<AnAction> actions = presentation.getClientProperty(ActionUtil.INLINE_ACTIONS);
        if (!ContainerUtil.isEmpty(actions)) {
          button.setOptions(actions);
        }
        else {
          button.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
              if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                button.doClick();
              }
            }
          });
        }
        button.setBackground(WelcomeScreenUIManager.getMainAssociatedComponentBackground());
        button.putClientProperty(JBOptionButton.PLACE, place);

        String popupHint = presentation.getClientProperty(INLINE_ACTIONS_POPUP_AD_TEXT);
        button.setOptionAdText(popupHint);

        return textButton;
      }

      @Override
      public void updateCustomComponent(@NotNull JComponent component, @NotNull Presentation presentation) {
        if (!(component instanceof WelcomeScreenTextButton button)) return;
        button.button.getAction().putValue(Action.NAME, presentation.getText());
        button.button.getAction().putValue(Action.SMALL_ICON, presentation.getClientProperty(TEXT_BUTTON_ICON));

        Icon rightIcon = presentation.getClientProperty(TEXT_BUTTON_RIGHT_ICON);
        button.rightIcon.setIcon(rightIcon);
        button.rightIcon.setVisible(rightIcon != null);

        UIUtil.setEnabled(button, presentation.isEnabled(), true);
      }
    };
  }

  private static class WelcomeScreenTextButton extends JBPanel<WelcomeScreenTextButton> {
    private final JBOptionButton button = new JBOptionButton(null, null);
    private final JLabel rightIcon = new JLabel();

    WelcomeScreenTextButton() {
      super(new BorderLayout());
      add(button, BorderLayout.CENTER);
      add(rightIcon, BorderLayout.EAST);
      andTransparent();
    }
  }

  static void performAnActionForComponent(@NotNull AnAction action, @NotNull JComponent component) {
    ActionToolbar toolbar = ActionToolbar.findToolbarBy(component);
    ActionUiKind uiKind = toolbar == null ? ActionUiKind.NONE : new ActualActionUiKind.Toolbar(toolbar);
    DataContext context = ActionToolbar.getDataContextFor(component);

    JLayeredPane layeredPane = UIUtil.getWindowLayeredPaneFor(component);
    Point p = SwingUtilities.convertPoint(component, 0, 0, layeredPane);
    Rectangle popupLocation = new Rectangle(p.x, p.y + component.getHeight() + JBUI.scale(4), 0, 0);
    DataContext popupContext = SimpleDataContext.builder()
      .setParent(context)
      .add(PlatformDataKeys.DOMINANT_HINT_AREA_RECTANGLE, popupLocation)
      .build();
    AnActionEvent actionEvent = AnActionEvent.createEvent(
      action, popupContext, null, ActionPlaces.WELCOME_SCREEN, uiKind, null);
    ActionUtil.performAction(action, actionEvent);
  }

  private static class LargeIconWithTextPanel extends NonOpaquePanel {
    final JButton myIconButton;
    final JBLabel myLabel;

    LargeIconWithTextPanel() {
      super(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, JBUI.scale(12), false, false));

      myIconButton = new JButton();
      myIconButton.setBorder(JBUI.Borders.empty());
      myIconButton.setHorizontalAlignment(SwingConstants.CENTER);
      myIconButton.setOpaque(false);
      myIconButton.setPreferredSize(new JBDimension(60, 60));

      if (ExperimentalUI.isNewUI()) {
        myIconButton.putClientProperty("JButton.focusedBackgroundColor", getActionsButtonBackground(false));
        myIconButton.putClientProperty("JButton.outlineFocusColor", WelcomeScreenUIManager.getActionsButtonSelectionBorder());
        myIconButton.putClientProperty("JButton.outlineFocusSize", JBUI.scale(2));
      }
      else {
        myIconButton.putClientProperty("JButton.focusedBackgroundColor", getActionsButtonBackground(true));
      }
      myIconButton.putClientProperty("JButton.backgroundColor", getActionsButtonBackground(false));

      myIconButton.addFocusListener(new FocusListener() {
        @Override
        public void focusGained(FocusEvent e) {
          updateIconBackground(true);
        }

        @Override
        public void focusLost(FocusEvent e) {
          updateIconBackground(false);
        }
      });
      myIconButton.addKeyListener(new KeyAdapter() {
        @Override
        public void keyPressed(KeyEvent e) {
          if (e.getKeyCode() == KeyEvent.VK_ENTER) {
            myIconButton.doClick();
          }
        }
      });
      Wrapper iconWrapper = new Wrapper(myIconButton);
      iconWrapper.setFocusable(false);
      iconWrapper.setBorder(JBUI.Borders.empty(0, 30));

      myLabel = new JBLabel("", SwingConstants.CENTER);
      myLabel.setOpaque(false);

      setFocusable(false);
      add(iconWrapper);
      add(myLabel);
    }

    void updateIconBackground(boolean selected) {
      if (!ExperimentalUI.isNewUI()) {
        myIconButton.setSelected(selected);
        myIconButton.putClientProperty("JButton.backgroundColor", getActionsButtonBackground(selected));
        myIconButton.repaint();
      }
    }
  }

  static @NotNull CustomComponentAction createBigIconWithTextAction(@NotNull AnAction action) {
    return new CustomComponentAction() {
      @Override
      public @NotNull JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
        String text = presentation.getText();
        if (StringUtil.isEmpty(text)) {
          Utils.reportEmptyTextMenuItem(action, place);
        }
        LargeIconWithTextPanel panel = new LargeIconWithTextPanel();
        panel.myIconButton.addActionListener(l -> performAnActionForComponent(action, panel.myIconButton));
        return panel;
      }

      @Override
      public void updateCustomComponent(@NotNull JComponent component, @NotNull Presentation presentation) {
        if (!(component instanceof LargeIconWithTextPanel panel)) return;
        panel.myIconButton.setIcon(presentation.getIcon());
        panel.myIconButton.setSelectedIcon(presentation.getSelectedIcon());
        //noinspection DialogTitleCapitalization
        panel.myLabel.setText(presentation.getText());
        panel.myIconButton.getAccessibleContext().setAccessibleName(presentation.getText());
        UIUtil.setEnabled(panel, presentation.isEnabled(), true);
      }
    };
  }
}
