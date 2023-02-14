// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.HelpTooltip;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.openapi.actionSystem.impl.MenuItemPresentationFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.SimpleMessageBusConnection;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.util.Objects;

public class SplitButtonAction extends ActionGroup implements CustomComponentAction {
  private final ActionGroup myActionGroup;
  private final static Key<AnAction> FIRST_ACTION = Key.create("firstAction");

  public SplitButtonAction(@NotNull ActionGroup actionGroup) {
    myActionGroup = actionGroup;
    setPopup(true);
  }

  public @NotNull ActionGroup getActionGroup() {
    return myActionGroup;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return myActionGroup.getActionUpdateThread();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    SplitButton splitButton = ObjectUtils.tryCast(presentation.getClientProperty(CustomComponentAction.COMPONENT_KEY), SplitButton.class);

    myActionGroup.update(e);
 
    if (presentation.isVisible()) {
      AnAction action = splitButton != null ? splitButton.selectedAction : getFirstEnabledAction(e);
      if (action != null) {
        Presentation actionPresentation = e.getUpdateSession().presentation(action);
        presentation.copyFrom(actionPresentation, splitButton);
        if (splitButton != null) {
          boolean shouldRepaint = splitButton.actionEnabled != presentation.isEnabled();
          splitButton.actionEnabled = presentation.isEnabled();
          if (shouldRepaint) splitButton.repaint();
        }
        presentation.setEnabledAndVisible(true);
      }

      presentation.putClientProperty(FIRST_ACTION, splitButton != null ? null : action);
    }
  }

  @Nullable
  private AnAction getFirstEnabledAction(@NotNull AnActionEvent e) {
    UpdateSession session = e.getUpdateSession();
    var children = session.children(myActionGroup);
    var firstEnabled = ContainerUtil.find(children, a -> session.presentation(a).isEnabled());
    return firstEnabled != null ? firstEnabled : ContainerUtil.getFirstItem(children);
  }

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    return myActionGroup.getChildren(e);
  }

  @Override
  public boolean isDumbAware() {
    return myActionGroup.isDumbAware();
  }

  @Override
  @NotNull
  public JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
    return new SplitButton(this, presentation, place, myActionGroup);
  }

  private static final class SplitButton extends ActionButton {
    private enum MousePressType {
      Action, Popup, None
    }

    private static final Icon ARROW_DOWN = AllIcons.General.ButtonDropTriangle;

    private final ActionGroup myActionGroup;
    private AnAction selectedAction;
    private boolean actionEnabled = true;
    private MousePressType mousePressType = MousePressType.None;
    private SimpleMessageBusConnection myConnection;

    private SplitButton(@NotNull AnAction action, @NotNull Presentation presentation, String place, ActionGroup actionGroup) {
      super(action, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
      myActionGroup = actionGroup;
      selectedAction = presentation.getClientProperty(FIRST_ACTION);
    }

    private void copyPresentation(Presentation presentation) {
      myPresentation.copyFrom(presentation, this);
      actionEnabled = presentation.isEnabled();
      myPresentation.setEnabled(true);
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension size = super.getPreferredSize();
      size.width += ARROW_DOWN.getIconWidth() + JBUIScale.scale(7);
      return size;
    }

    private boolean selectedActionEnabled() {
      return selectedAction != null && actionEnabled;
    }

    @Override
    public void paintComponent(Graphics g) {
      ActionButtonLook look = getButtonLook();
      if (selectedActionEnabled() || !StartupUiUtil.isUnderDarcula()) {
        int state = getPopState();
        if (state == PUSHED) state = POPPED;
        look.paintBackground(g, this, state);
      }

      Rectangle baseRect = new Rectangle(getSize());
      JBInsets.removeFrom(baseRect, getInsets());

      if (getPopState() == PUSHED && mousePressType != MousePressType.None && selectedActionEnabled() || isToggleActionPushed()) {
        int arrowWidth = ARROW_DOWN.getIconWidth() + JBUIScale.scale(7);

        Shape clip = g.getClip();
        Area buttonClip = new Area(clip);
        Rectangle execButtonRect = new Rectangle(baseRect.x, baseRect.y, baseRect.width - arrowWidth, baseRect.height);
        if (mousePressType == MousePressType.Action || isToggleActionPushed()) {
          buttonClip.intersect(new Area(execButtonRect));
        }
        else if (mousePressType == MousePressType.Popup) {
          Rectangle arrowButtonRect = new Rectangle(execButtonRect.x + execButtonRect.width, baseRect.y, arrowWidth, baseRect.height);
          buttonClip.intersect(new Area(arrowButtonRect));
        }

        g.setClip(buttonClip);
        look.paintBackground(g, this, PUSHED);
        g.setClip(clip);
      }

      int x = baseRect.x + baseRect.width - JBUIScale.scale(3) - ARROW_DOWN.getIconWidth();
      int y = baseRect.y + (baseRect.height - ARROW_DOWN.getIconHeight()) / 2 + JBUIScale.scale(1);
      look.paintIcon(g, this, ARROW_DOWN, x, y);

      x -= JBUIScale.scale(4);
      int popState = getPopState();
      if (popState == POPPED || popState == PUSHED) {
        g.setColor(JBUI.CurrentTheme.ActionButton.hoverSeparatorColor());
        g.fillRect(x, baseRect.y, JBUIScale.scale(1), baseRect.height);
      }

      Icon actionIcon = getIcon();
      if (!selectedActionEnabled()) {
        Icon disabledIcon = myPresentation.getDisabledIcon();
        actionIcon = disabledIcon != null || actionIcon == null ? disabledIcon : IconLoader.getDisabledIcon(actionIcon);
        if (actionIcon == null) {
          actionIcon = getFallbackIcon(false);
        }
      }

      x = baseRect.x + (x -  actionIcon.getIconWidth()) / 2;
      y = baseRect.y + (baseRect.height - actionIcon.getIconHeight()) / 2;
      look.paintIcon(g, this, actionIcon, x, y);
    }

    private boolean isToggleActionPushed() {
      return selectedAction instanceof Toggleable && Toggleable.isSelected(myPresentation);
    }

      @Override
    protected void onMousePressed(@NotNull MouseEvent e) {
      Rectangle baseRect = new Rectangle(getSize());
      JBInsets.removeFrom(baseRect, getInsets());
      int arrowWidth = ARROW_DOWN.getIconWidth() + JBUIScale.scale(7);

      Rectangle execButtonRect = new Rectangle(baseRect.x, baseRect.y, baseRect.width - arrowWidth, baseRect.height);
      Rectangle arrowButtonRect = new Rectangle(execButtonRect.x + execButtonRect.width, baseRect.y, arrowWidth, baseRect.height);

      Point p = e.getPoint();
      mousePressType = execButtonRect.contains(p) ? MousePressType.Action :
                       arrowButtonRect.contains(p) ? MousePressType.Popup :
                       MousePressType.None;
    }

    @Override
    protected void actionPerformed(@NotNull AnActionEvent event) {
      HelpTooltip.hide(this);

      if (mousePressType == MousePressType.Popup || !selectedActionEnabled()) {
        showActionGroupPopup(myActionGroup, event);
      }
      else {
        selectedAction.actionPerformed(event);
      }
    }

    @Override
    protected void showActionGroupPopup(@NotNull ActionGroup actionGroup, @NotNull AnActionEvent event) {
      if (myPopupState.isRecentlyHidden()) return; // do not show new popup
      ActionManagerImpl am = (ActionManagerImpl) ActionManager.getInstance();
      ActionPopupMenu popupMenu = am.createActionPopupMenu(event.getPlace(), actionGroup, new MenuItemPresentationFactory() {
        @Override
        protected void processPresentation(@NotNull Presentation presentation) {
          super.processPresentation(presentation);
          if (StringUtil.defaultIfEmpty(presentation.getText(), "").equals(myPresentation.getText()) &&
              StringUtil.defaultIfEmpty(presentation.getDescription(), "").equals(myPresentation.getDescription())) {
            presentation.setEnabled(selectedActionEnabled());
          }
        }
      });
      popupMenu.setTargetComponent(this);

      JPopupMenu menu = popupMenu.getComponent();
      myPopupState.prepareToShow(menu);
      if (event.isFromActionToolbar()) {
        menu.show(this, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.width + getInsets().left, getHeight());
      }
      else {
        JBPopupMenu.showAtRight(this, menu);
      }

      HelpTooltip.setMasterPopupOpenCondition(this, () -> !menu.isVisible());
    }

    @Override
    public void addNotify() {
      super.addNotify();
      DataContext context = DataManager.getInstance().getDataContext(getParent());
      Disposable parentDisposable = Objects.requireNonNullElse(CommonDataKeys.PROJECT.getData(context), ApplicationManager.getApplication());
      myConnection = ApplicationManager.getApplication().getMessageBus().connect(parentDisposable);
      myConnection.subscribe(AnActionListener.TOPIC, new AnActionListener() {
        @Override
        public void beforeActionPerformed(@NotNull AnAction action, @NotNull AnActionEvent event) {
          if (event.getDataContext().getData(PlatformCoreDataKeys.CONTEXT_COMPONENT) == SplitButton.this) {
            selectedAction = action;
            copyPresentation(event.getPresentation());
            repaint();
          }
        }
      });
    }

    @Override
    public void removeNotify() {
      super.removeNotify();
      if (myConnection != null) {
        myConnection.disconnect();
        myConnection = null;
      }
    }
  }
}