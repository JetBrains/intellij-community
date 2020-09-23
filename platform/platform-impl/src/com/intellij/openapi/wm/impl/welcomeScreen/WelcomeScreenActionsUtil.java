// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.Couple;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.accessibility.AccessibleState;
import javax.swing.*;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.Objects;
import java.util.function.Function;

import static com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager.getActionsButtonBackground;

public class WelcomeScreenActionsUtil {

  public static void collectAllActions(@NotNull DefaultActionGroup group, @NotNull ActionGroup actionGroup) {
    for (AnAction action : actionGroup.getChildren(null)) {
      if (action instanceof ActionGroup && !((ActionGroup)action).isPopup()) {
        collectAllActions(group, (ActionGroup)action);
      }
      else {
        group.add(action);
      }
    }
  }

  public static class ToolbarTextButtonWrapper extends AnActionButton.AnActionButtonWrapper implements CustomComponentAction {
    final JButton myButton;

    ToolbarTextButtonWrapper(@NotNull AnAction action) {
      super(action.getTemplatePresentation(), action);
      myButton = new JButton(getTemplateText());
      myButton.setOpaque(false);
      myButton.addActionListener(createActionListenerForComponent(myButton, action));
    }

    @Override
    public @NotNull JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
      return myButton;
    }

    @Override
    public void updateButton(@NotNull AnActionEvent e) {
      getDelegate().update(e);
      myButton.setText(e.getPresentation().getText());
      myButton.setVisible(e.getPresentation().isVisible());
      myButton.setEnabled(e.getPresentation().isEnabled());
    }

    public static ToolbarTextButtonWrapper wrapAsTextButton(@NotNull AnAction action) {
      return new ToolbarTextButtonWrapper(action);
    }
  }

  static boolean isActionAvailable(@NotNull AnAction action) {
    AnActionEvent event = AnActionEvent.createFromAnAction(action, null, ActionPlaces.WELCOME_SCREEN, DataContext.EMPTY_CONTEXT);
    action.update(event);
    return event.getPresentation().isVisible();
  }

  @NotNull
  static ActionListener createActionListenerForComponent(@NotNull JComponent component, @NotNull AnAction action) {
    return l -> {
      ActionToolbar toolbar = ComponentUtil.getParentOfType(ActionToolbar.class, component);
      DataContext dataContext = toolbar != null ? toolbar.getToolbarDataContext() : DataManager.getInstance().getDataContext(component);
      action.actionPerformed(AnActionEvent.createFromAnAction(action, null, ActionPlaces.WELCOME_SCREEN, dataContext));
    };
  }

  static class LargeIconWithTextWrapper extends AnActionButton.AnActionButtonWrapper implements CustomComponentAction {
    final JButton myIconButton;
    final JBLabel myLabel;
    private final JPanel myPanel;

    LargeIconWithTextWrapper(@NotNull AnAction action) {
      super(action.getTemplatePresentation(), action);
      myIconButton = new JButton();
      myIconButton.setBorder(JBUI.Borders.empty());
      myIconButton.setHorizontalAlignment(SwingConstants.CENTER);
      myIconButton.setOpaque(false);
      myIconButton.setPreferredSize(new JBDimension(60, 60));
      myIconButton.putClientProperty("JButton.focusedBackgroundColor", getActionsButtonBackground(true));
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
      myIconButton.addActionListener(createActionListenerForComponent(myIconButton, action));
      Wrapper iconWrapper = new Wrapper(myIconButton);
      iconWrapper.setBorder(JBUI.Borders.empty(0, 30));

      myLabel = new JBLabel(Objects.requireNonNull(getTemplateText()), SwingConstants.CENTER);
      myLabel.setOpaque(false);

      myPanel = new NonOpaquePanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, JBUI.scale(12), false, false));
      myPanel.add(iconWrapper);
      myPanel.add(myLabel);
      myIconButton.getAccessibleContext().setAccessibleName(myLabel.getText());
    }

    void updateIconBackground(boolean selected) {
      myIconButton.setSelected(selected);
      myIconButton.putClientProperty("JButton.backgroundColor", getActionsButtonBackground(selected));
      myIconButton.repaint();
    }

    @Override
    public @NotNull JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
      return myPanel;
    }

    @Override
    public void updateButton(@NotNull AnActionEvent e) {
      getDelegate().update(e);
      myIconButton.setIcon(e.getPresentation().getIcon());
      myIconButton.setSelectedIcon(e.getPresentation().getSelectedIcon());
      myLabel.setText(e.getPresentation().getText());
      UIUtil.setEnabled(myPanel, e.getPresentation().isEnabled(), true);
    }

    public static @NotNull LargeIconWithTextWrapper wrapAsBigIconWithText(AnAction action) {
      return new LargeIconWithTextWrapper(action);
    }
  }

  public static Couple<DefaultActionGroup> splitAndWrapActions(@NotNull ActionGroup actionGroup,
                                                        @NotNull Function<? super AnAction, ? extends AnAction> wrapper,
                                                        int mainButtonsNum) {
    DefaultActionGroup group = new DefaultActionGroup();
    collectAllActions(group, actionGroup);
    AnAction[] actions = group.getChildren(null);

    DefaultActionGroup main = new DefaultActionGroup();
    DefaultActionGroup more = new DefaultActionGroup(IdeBundle.message("welcome.screen.more.actions.link.text"), true) {
      @Override
      public boolean hideIfNoVisibleChildren() {
        return true;
      }
    };
    for (AnAction child : actions) {
      if (!isActionAvailable(child)) continue;
      if (main.getChildrenCount() < mainButtonsNum) {
        main.addAction(wrapper.apply(child));
      }
      else {
        more.addAction(wrapper.apply(child));
      }
    }
    return Couple.of(main, more);
  }
}
