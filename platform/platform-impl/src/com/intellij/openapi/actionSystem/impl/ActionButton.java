/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class ActionButton extends JComponent implements ActionButtonComponent, AnActionHolder, Accessible {

  private static final Icon ourEmptyIcon = EmptyIcon.ICON_18;

  private JBDimension myMinimumButtonSize;
  private PropertyChangeListener myActionButtonSynchronizer;
  private Icon myDisabledIcon;
  private Icon myIcon;
  protected final Presentation myPresentation;
  protected final AnAction myAction;
  protected final String myPlace;
  private ActionButtonLook myLook = ActionButtonLook.IDEA_LOOK;
  private boolean myMouseDown;
  private boolean myRollover;
  private static boolean ourGlobalMouseDown = false;

  private boolean myNoIconsInPopup = false;
  private Insets myInsets;

  public ActionButton(AnAction action,
                      Presentation presentation,
                      String place,
                      @NotNull Dimension minimumSize) {
    setMinimumButtonSize(minimumSize);
    setIconInsets(null);
    myRollover = false;
    myMouseDown = false;
    myAction = action;
    myPresentation = presentation;
    myPlace = place;
    setFocusable(false);
    enableEvents(AWTEvent.MOUSE_EVENT_MASK);

    putClientProperty(UIUtil.CENTER_TOOLTIP_DEFAULT, Boolean.TRUE);
  }

  public void setNoIconsInPopup(boolean noIconsInPopup) {
    myNoIconsInPopup = noIconsInPopup;
  }

  public void setMinimumButtonSize(@NotNull Dimension size) {
    myMinimumButtonSize = JBDimension.create(size);
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
    AnActionEvent event = AnActionEvent.createFromInputEvent(e, myPlace, myPresentation, getDataContext());
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
      actionPerformed(event);
      manager.queueActionPerformedEvent(myAction, dataContext, event);
    }
  }

  protected DataContext getDataContext() {
    ActionToolbar actionToolbar = UIUtil.getParentOfType(ActionToolbar.class, this);
    return actionToolbar != null ? actionToolbar.getToolbarDataContext() : DataManager.getInstance().getDataContext();
  }

  private void actionPerformed(final AnActionEvent event) {
    if (myAction instanceof ActionGroup && !(myAction instanceof CustomComponentAction) && ((ActionGroup)myAction).isPopup()) {
      final ActionManagerImpl am = (ActionManagerImpl)ActionManager.getInstance();
      ActionPopupMenuImpl popupMenu = (ActionPopupMenuImpl)am.createActionPopupMenu(event.getPlace(), (ActionGroup)myAction, new MenuItemPresentationFactory() {
        @Override
        protected void processPresentation(Presentation presentation) {
          if (myNoIconsInPopup) {
            presentation.setIcon(null);
            presentation.setHoveredIcon(null);
          }
        }
      });
      popupMenu.setDataContextProvider(new Getter<DataContext>() {
        @Override
        public DataContext get() {
          return ActionButton.this.getDataContext();
        }
      });
      if (ActionPlaces.isToolbarPlace(event.getPlace())) {
        popupMenu.getComponent().show(this, 0, getHeight());
      }
      else {
        popupMenu.getComponent().show(this, getWidth(), 0);
      }

    } else {
      ActionUtil.performActionDumbAware(myAction, event);
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
    AnActionEvent e = new AnActionEvent(null, getDataContext(), myPlace, myPresentation, ActionManager.getInstance(), 0);
    ActionUtil.performDumbAwareUpdate(myAction, e, false);
    updateToolTipText();
    updateIcon();
  }

  public void setToolTipText(String s) {
    String tooltipText = KeymapUtil.createTooltipText(s, myAction);
    super.setToolTipText(tooltipText.length() > 0 ? tooltipText : null);
  }

  public Dimension getPreferredSize() {
    Icon icon = getIcon();
    if (icon.getIconWidth() < myMinimumButtonSize.width &&
        icon.getIconHeight() < myMinimumButtonSize.height) {
      return myMinimumButtonSize;
    }
    else {
      return new Dimension(
        Math.max(myMinimumButtonSize.width, icon.getIconWidth() + myInsets.left + myInsets.right),
        Math.max(myMinimumButtonSize.height, icon.getIconHeight() + myInsets.top + myInsets.bottom)
      );
    }
  }


  public void setIconInsets(@Nullable Insets insets) {
    myInsets = insets != null ? JBUI.insets(insets) : new Insets(0,0,0,0);
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
    return icon == null ? ourEmptyIcon : icon;
  }

  public void updateIcon() {
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

      AllIcons.General.Dropdown.paintIcon(this, g, x, y);
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
        revalidate(); // recalc preferred size & repaint instantly
      }
      else if (Presentation.PROP_ENABLED.equals(propertyName)) {
        updateIcon();
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

  // Accessibility

  @Override
  public AccessibleContext getAccessibleContext() {
    if(this.accessibleContext == null) {
      this.accessibleContext = new AccessibleActionButton();
    }

    return this.accessibleContext;
  }


  protected class AccessibleActionButton extends JComponent.AccessibleJComponent implements AccessibleAction {
    public AccessibleActionButton() {
    }

    @Override
    public AccessibleRole getAccessibleRole() {
      return AccessibleRole.PUSH_BUTTON;
    }

    @Override
    public String getAccessibleName() {
      String name = accessibleName;
      if (name == null) {
        name = (String)ActionButton.this.getClientProperty(ACCESSIBLE_NAME_PROPERTY);
        if (name == null) {
          name = ActionButton.this.getToolTipText();
          if (name == null) {
            name = ActionButton.this.myPresentation.getText();
            if (name == null) {
              name = super.getAccessibleName();
            }
          }
        }
      }

      return name;
    }

    @Override
    public AccessibleIcon[] getAccessibleIcon() {
      Icon icon = ActionButton.this.getIcon();
      if (icon instanceof Accessible) {
        AccessibleContext context = ((Accessible)icon).getAccessibleContext();
        if (context != null && context instanceof AccessibleIcon) {
          return new AccessibleIcon[]{(AccessibleIcon)context};
        }
      }

      return null;
    }

    @Override
    public AccessibleStateSet getAccessibleStateSet() {
      AccessibleStateSet var1 = super.getAccessibleStateSet();
      int state = ActionButton.this.getPopState();

      // TODO: Not sure what the "POPPED" state represents
      //if (state == POPPED) {
      //  var1.add(AccessibleState.?);
      //}

      if (state == ActionButtonComponent.PUSHED) {
        var1.add(AccessibleState.PRESSED);
      }
      if (state == ActionButtonComponent.SELECTED) {
        var1.add(AccessibleState.CHECKED);
      }

      if (ActionButton.this.isFocusOwner()) {
        var1.add(AccessibleState.FOCUSED);
      }

      return var1;
    }

    @Override
    public AccessibleAction getAccessibleAction() {
      return this;
    }

    // Implements AccessibleAction

    @Override
    public int getAccessibleActionCount() {
      return 1;
    }

    @Override
    public String getAccessibleActionDescription(int index) {
      return index == 0 ? UIManager.getString("AbstractButton.clickText") : null;
    }

    @Override
    public boolean doAccessibleAction(int index) {
      if (index == 0) { //
        ActionButton.this.click();
        return true;
      }
      else {
        return false;
      }
    }
  }
}
