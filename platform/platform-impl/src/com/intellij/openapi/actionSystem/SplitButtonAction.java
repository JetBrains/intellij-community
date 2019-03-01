// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem;

import com.intellij.icons.AllIcons;
import com.intellij.ide.HelpTooltip;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;

import static com.intellij.openapi.actionSystem.ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE;

public class SplitButtonAction extends AnAction implements CustomComponentAction {
  private final ActionGroup myActionGroup;

  public SplitButtonAction(@NotNull ActionGroup actionGroup) {
    myActionGroup = actionGroup;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
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

  private static class SplitButton extends ActionButton implements AnActionListener {
    private enum MousePressType {
      Action, Popup, None
    }

    private static final Icon ARROW_DOWN = AllIcons.General.ArrowDownSmall;

    private final ActionGroup myActionGroup;
    private AnAction selectedAction;
    private MousePressType mousePressType = MousePressType.None;
    private Disposable myDisposable;

    private SplitButton(AnAction action, Presentation presentation, String place, ActionGroup actionGroup) {
      super(action, presentation, place, DEFAULT_MINIMUM_BUTTON_SIZE);
      myActionGroup = actionGroup;

      AnAction[] actions = myActionGroup.getChildren(null);
      selectedAction = actions.length > 0 ? actions[0] : null;
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension size = super.getPreferredSize();
      size.width += ARROW_DOWN.getIconWidth() + JBUI.scale(10);
      return size;
    }

    private boolean selectedActionEnabled() {
      return selectedAction != null && selectedAction.getTemplatePresentation().isEnabled();
    }

    @Override
    public void paintComponent(Graphics g) {
      ActionButtonLook look = getButtonLook();
      if (selectedActionEnabled() || !UIUtil.isUnderDarcula()) {
        int state = getPopState();
        if (state == PUSHED) state = POPPED;
        look.paintBackground(g, this, state);
      }

      Rectangle baseRect = new Rectangle(getSize());
      JBInsets.removeFrom(baseRect, getInsets());

      if (getPopState() == PUSHED && mousePressType != MousePressType.None) {
        int arrowWidth = ARROW_DOWN.getIconWidth() + JBUI.scale(7);

        Shape clip = g.getClip();
        Area buttonClip = new Area(clip);
        Rectangle execButtonRect = new Rectangle(baseRect.x, baseRect.y, baseRect.width - arrowWidth, baseRect.height);
        if (mousePressType == MousePressType.Action) {
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

      int x = baseRect.x + baseRect.width - JBUI.scale(3) - ARROW_DOWN.getIconWidth();
      int y = baseRect.y + (baseRect.height - ARROW_DOWN.getIconHeight()) / 2;
      look.paintIconAt(g, ARROW_DOWN, x, y);

      x -= JBUI.scale(4);
      if (getPopState() == POPPED || getPopState() == PUSHED) {
        g.setColor(JBUI.CurrentTheme.ActionButton.hoverSeparatorColor());
        g.fillRect(x, baseRect.y, JBUI.scale(1), baseRect.height);
      }

      Icon actionIcon = selectedAction != null ? selectedAction.getTemplatePresentation().getIcon() : AllIcons.Actions.Stub;
      if (!selectedActionEnabled()) {
        actionIcon = ObjectUtils.notNull(IconLoader.getDisabledIcon(actionIcon));
      }

      x -= JBUI.scale(3) + actionIcon.getIconWidth();
      y = baseRect.y + (baseRect.height - actionIcon.getIconHeight()) / 2;
      x = x > baseRect.x ? x : baseRect.x;
      look.paintIconAt(g, actionIcon, x, y);
      look.paintBorder(g, this);
    }

    @Override
    protected void onMousePressed(@NotNull MouseEvent e) {
      Rectangle baseRect = new Rectangle(getSize());
      JBInsets.removeFrom(baseRect, getInsets());
      int arrowWidth = ARROW_DOWN.getIconWidth() + JBUI.scale(7);

      Rectangle execButtonRect = new Rectangle(baseRect.x, baseRect.y, baseRect.width - arrowWidth, baseRect.height);
      Rectangle arrowButtonRect = new Rectangle(execButtonRect.x + execButtonRect.width, baseRect.y, arrowWidth, baseRect.height);

      Point p = e.getPoint();
      mousePressType = execButtonRect.contains(p) ? MousePressType.Action :
                       arrowButtonRect.contains(p) ? MousePressType.Popup :
                       MousePressType.None;
    }

    @Override
    protected void actionPerformed(final AnActionEvent event) {
      HelpTooltip.hide(this);

      if (mousePressType == MousePressType.Popup) {
        showPopupMenu(event, myActionGroup);
      } else if (selectedActionEnabled()) {
        ActionUtil.performActionDumbAware(selectedAction, AnActionEvent.createFromAnAction(selectedAction, event.getInputEvent(), myPlace, getDataContext()));
      }
    }

    @Override
    public void addNotify() {
      super.addNotify();
      myDisposable = Disposer.newDisposable();
      ApplicationManager.getApplication().getMessageBus().connect(myDisposable).subscribe(AnActionListener.TOPIC, this);
    }

    @Override
    public void removeNotify() {
      super.removeNotify();
      if (myDisposable != null) {
        Disposer.dispose(myDisposable);
        myDisposable = null;
      }
    }

    @Override
    public void afterActionPerformed(@NotNull AnAction action, @NotNull DataContext dataContext, @NotNull AnActionEvent event) {
      if (selectedAction != action && myAction != action) {
        selectedAction = action;
        repaint();
      }
    }
  }
}
