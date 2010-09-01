/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.actionSystem.impl;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class ActionButton extends JComponent implements ActionButtonComponent {
  private static final Insets ICON_INSETS = new Insets(2, 2, 2, 2);

  private static final Icon ourEmptyIcon = new EmptyIcon(18, 18);

  private Dimension myMinimumButtonSize;
  private PropertyChangeListener myActionButtonSynchronizer;
  private Icon myDisabledIcon;
  private Icon myIcon;
  protected final Presentation myPresentation;
  protected final AnAction myAction;
  private final String myPlace;
  private ActionButtonLook myLook = ActionButtonLook.IDEA_LOOK;
  private boolean myMouseDown;
  private boolean myRollover;
  private static boolean ourGlobalMouseDown = false;

  private static final Icon myDropdownIcon = IconLoader.getIcon("/general/dropdown.png");

  private boolean myNoIconsInPopup = false;

  public ActionButton(
    final AnAction action,
    final Presentation presentation,
    final String place,
    @NotNull final Dimension minimumSize
    ) {
    setMinimumButtonSize(minimumSize);
    myRollover = false;
    myMouseDown = false;
    myAction = action;
    myPresentation = presentation;
    myPlace = place;
    setFocusable(false);
    enableEvents(AWTEvent.MOUSE_EVENT_MASK);
    myMinimumButtonSize = minimumSize;

    putClientProperty(UIUtil.CENTER_TOOLTIP, Boolean.TRUE);
  }

  public void setNoIconsInPopup(boolean noIconsInPopup) {
    myNoIconsInPopup = noIconsInPopup;
  }

  public void setMinimumButtonSize(@NotNull Dimension size) {
    myMinimumButtonSize = size;
  }

  public void paintChildren(Graphics g) {}

  public int getPopState() {
    if (myAction instanceof Toggleable) {
      Boolean selected = (Boolean)myPresentation.getClientProperty(Toggleable.SELECTED_PROPERTY);
      boolean flag1 = selected != null && selected.booleanValue();
      return getPopState(flag1);
    }
    else {
      return getPopState(false);
    }
  }

  protected boolean isButtonEnabled() {
    return isEnabled() && myPresentation.isEnabled();
  }

  private void onMousePresenceChanged(boolean setInfo) {
    ActionMenu.showDescriptionInStatusBar(setInfo, this, myPresentation.getDescription());
  }

  public void click() {
    performAction(new MouseEvent(this, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 0, 0, 1, false));
  }

  private void performAction(MouseEvent e) {
    AnActionEvent event = new AnActionEvent(
      e, getDataContext(),
      myPlace,
      myPresentation,
      ActionManager.getInstance(),
      e.getModifiers()
    );
    if (!ActionUtil.lastUpdateAndCheckDumb(myAction, event, false)) {
      return;
    }

    if (isButtonEnabled()) {
      final ActionManagerEx manager = ActionManagerEx.getInstanceEx();
      final DataContext dataContext = event.getDataContext();
      manager.fireBeforeActionPerformed(myAction, dataContext, event);
      Component component = PlatformDataKeys.CONTEXT_COMPONENT.getData(dataContext);
      if (component != null && !component.isShowing()) {
        return;
      }
      actionPerfomed(event);
      manager.queueActionPerformedEvent(myAction, dataContext, event);
    }
  }

  protected DataContext getDataContext() {
    return DataManager.getInstance().getDataContext();
  }

  private void actionPerfomed(final AnActionEvent event) {
    if (myAction instanceof ActionGroup && !(myAction instanceof CustomComponentAction) && ((ActionGroup)myAction).isPopup()) {
      final ActionManagerImpl am = (ActionManagerImpl)ActionManager.getInstance();
      ActionPopupMenu popupMenu = am.createActionPopupMenu(event.getPlace(), (ActionGroup)myAction, new MenuItemPresentationFactory() {
        @Override
        protected Presentation processPresentation(Presentation presentation) {
          if (myNoIconsInPopup) {
            presentation.setIcon(null);
            presentation.setHoveredIcon(null);
          }
          return presentation;
        }
      });
      popupMenu.getComponent().show(this, getWidth(), 0);
    } else {
      myAction.actionPerformed(event);
    }
  }

  public void removeNotify() {
    if (myActionButtonSynchronizer != null) {
      myPresentation.removePropertyChangeListener(myActionButtonSynchronizer);
      myActionButtonSynchronizer = null;
    }
    super.removeNotify();
  }

  public void addNotify() {
    super.addNotify();
    if (myActionButtonSynchronizer == null) {
      myActionButtonSynchronizer = new ActionButtonSynchronizer();
      myPresentation.addPropertyChangeListener(myActionButtonSynchronizer);
    }
    updateToolTipText();
    updateIcon();
  }

  public void setToolTipText(String s) {
    String tooltipText = AnAction.createTooltipText(s, myAction);
    super.setToolTipText(tooltipText.length() > 0 ? tooltipText : null);
  }

  public Dimension getPreferredSize() {
    Icon icon = getIcon();
    if (
      icon.getIconWidth() < myMinimumButtonSize.width &&
      icon.getIconHeight() < myMinimumButtonSize.height
    ) {
      return myMinimumButtonSize;
    }
    else {
      return new Dimension(
        icon.getIconWidth() + ICON_INSETS.left + ICON_INSETS.right,
        icon.getIconHeight() + ICON_INSETS.top + ICON_INSETS.bottom
      );
    }
  }

  public Dimension getMinimumSize() {
    return getPreferredSize();
  }

  /**
   * @return button's icon. Icon depends on action's state. It means that the method returns
   *         disabled icon if action is disabled. If the action's icon is <code>null</code> then it returns
   *         an empty icon.
   */
  protected Icon getIcon() {
    Icon icon = isButtonEnabled() ? myIcon : myDisabledIcon;
    if (icon == null) {
      icon = ourEmptyIcon;
    }
    return icon;
  }

  private void updateIcon() {
    myIcon = myPresentation.getIcon();
    if (myPresentation.getDisabledIcon() != null) { // set disabled icon if it is specified
      myDisabledIcon = myPresentation.getDisabledIcon();
    }
    else {
      myDisabledIcon = IconLoader.getDisabledIcon(myIcon);
    }
  }

  private void setDisabledIcon(Icon icon) {
    myDisabledIcon = icon;
  }

  void updateToolTipText() {
    String text = myPresentation.getText();
    setToolTipText(text == null ? myPresentation.getDescription() : text);
  }

  public void paintComponent(Graphics g) {
    super.paintComponent(g);

    paintButtonLook(g);

    if (myAction instanceof ActionGroup && ((ActionGroup)myAction).isPopup()) {

      int x = 5;
      int y = 4;

      if (getPopState() == PUSHED) {
        x++;
        y++;
      }

      myDropdownIcon.paintIcon(this, g, x, y);
    }
  }

  protected void paintButtonLook(Graphics g) {
    ActionButtonLook look = getButtonLook();
    look.paintBackground(g, this);
    look.paintIcon(g, this, getIcon());
    look.paintBorder(g, this);
  }

  protected ActionButtonLook getButtonLook() {
    return myLook;
  }

  public void setLook(ActionButtonLook look) {
    if (look != null) {
      myLook = look;
    }
    else {
      myLook = ActionButtonLook.IDEA_LOOK;
    }
    repaint();
  }

  protected void processMouseEvent(MouseEvent e) {
    super.processMouseEvent(e);
    if (e.isConsumed()) return;
    boolean skipPress = e.isMetaDown() || e.getButton() != MouseEvent.BUTTON1;
    switch (e.getID()) {
      case MouseEvent.MOUSE_PRESSED:
        if (skipPress || !isButtonEnabled()) return;
        myMouseDown = true;
        ourGlobalMouseDown = true;
        repaint();
        break;

      case MouseEvent.MOUSE_RELEASED:
        if (skipPress || !isButtonEnabled()) return;
        myMouseDown = false;
        ourGlobalMouseDown = false;
        if (myRollover) {
          performAction(e);
        }
        repaint();
        break;

      case MouseEvent.MOUSE_ENTERED:
        if (!myMouseDown && ourGlobalMouseDown) break;
        myRollover = true;
        repaint();
        onMousePresenceChanged(true);
        break;

      case MouseEvent.MOUSE_EXITED:
        myRollover = false;
        if (!myMouseDown && ourGlobalMouseDown) break;
        repaint();
        onMousePresenceChanged(false);
        break;
    }
  }

  private int getPopState(boolean isPushed) {
    if (isPushed || myRollover && myMouseDown && isButtonEnabled()) {
      return PUSHED;
    }
    else {
      return !myRollover || !isButtonEnabled() ? NORMAL : POPPED;
    }
  }

  public AnAction getAction() {
    return myAction;
  }

  private class ActionButtonSynchronizer implements PropertyChangeListener {
    @NonNls protected static final String SELECTED_PROPERTY_NAME = "selected";

    public void propertyChange(PropertyChangeEvent e) {
      String propertyName = e.getPropertyName();
      if (Presentation.PROP_TEXT.equals(propertyName)) {
        updateToolTipText();
      }
      else if (Presentation.PROP_ENABLED.equals(propertyName)) {
        repaint();
      }
      else if (Presentation.PROP_ICON.equals(propertyName)) {
        updateIcon();
        repaint();
      }
      else if (Presentation.PROP_DISABLED_ICON.equals(propertyName)) {
        setDisabledIcon(myPresentation.getDisabledIcon());
        repaint();
      }
      else if (Presentation.PROP_VISIBLE.equals(propertyName)) {
      }
      else if (SELECTED_PROPERTY_NAME.equals(propertyName)) {
        repaint();
      }
    }
  }
}
