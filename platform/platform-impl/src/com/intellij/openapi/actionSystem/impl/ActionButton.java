// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.HelpTooltip;
import com.intellij.internal.statistic.collectors.fus.ui.persistence.ToolbarClicksCollector;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.impl.IdeMouseEventDispatcher;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.popup.util.PopupState;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.*;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Set;

public class ActionButton extends JComponent implements ActionButtonComponent, AnActionHolder, Accessible {
  /**
   * By default button representing popup action group displays 'dropdown' icon.
   * This key allows to avoid 'dropdown' icon painting, just put it in ActionButton's presentation or template presentation of ActionGroup like this:
   * {@code presentation.putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, Boolean.TRUE)}
   */

  public static final Key<Boolean> HIDE_DROPDOWN_ICON = Key.create("HIDE_DROPDOWN_ICON");
  private JBDimension myMinimumButtonSize;
  private PropertyChangeListener myPresentationListener;
  private Icon myDisabledIcon;
  private Icon myIcon;
  protected final Presentation myPresentation;
  protected final AnAction myAction;
  protected final String myPlace;
  protected final PopupState myPopupState = new PopupState();
  private ActionButtonLook myLook = ActionButtonLook.SYSTEM_LOOK;
  private boolean myMouseDown;
  private boolean myRollover;
  private static boolean ourGlobalMouseDown;

  private boolean myNoIconsInPopup;
  private Insets myInsets;

  public ActionButton(@NotNull AnAction action,
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
    // Button should be focusable if screen reader is active
    setFocusable(ScreenReader.isActive());
    enableEvents(AWTEvent.MOUSE_EVENT_MASK);
    // Pressing the SPACE key is the same as clicking the button
    addKeyListener(new KeyAdapter() {
      @Override
      public void keyReleased(KeyEvent e) {
        if (e.getModifiers() == 0 && e.getKeyCode() == KeyEvent.VK_SPACE) {
          click();
        }
      }
    });
    addFocusListener(new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
        repaint();
      }

      @Override
      public void focusLost(FocusEvent e) {
        repaint();
      }
    });

    putClientProperty(UIUtil.CENTER_TOOLTIP_DEFAULT, Boolean.TRUE);
  }

  public void setNoIconsInPopup(boolean noIconsInPopup) {
    myNoIconsInPopup = noIconsInPopup;
  }

  // used in Rider, please don't change visibility
  public void setMinimumButtonSize(@NotNull Dimension size) {
    myMinimumButtonSize = JBDimension.create(size);
  }

  @Override
  public void paintChildren(Graphics g) {}

  @Override
  public int getPopState() {
    return getPopState(isSelected());
  }

  protected final boolean isRollover() {
    return myRollover;
  }

  public final boolean isSelected() {
    return myAction instanceof Toggleable && Toggleable.isSelected(myPresentation);
  }

  @Override
  public boolean isEnabled() {
    return super.isEnabled() && myPresentation.isEnabled();
  }

  boolean isButtonEnabled() {
    return isEnabled();
  }

  private void onMousePresenceChanged(boolean setInfo) {
    ActionMenu.showDescriptionInStatusBar(setInfo, this, myPresentation.getDescription());
  }

  public void click() {
    performAction(new MouseEvent(this, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 0, 0, 1, false));
  }

  private void performAction(MouseEvent e) {
    AnActionEvent event = AnActionEvent.createFromInputEvent(e, myPlace, myPresentation, getDataContext(), false, true);
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
      if (event.getInputEvent() instanceof MouseEvent) {
        ToolbarClicksCollector.record(myAction, myPlace, e, dataContext);
      }
      ActionToolbar toolbar = getActionToolbar();
      if (toolbar != null) {
        toolbar.updateActionsImmediately();
      }
    }
  }

  protected DataContext getDataContext() {
    ActionToolbar actionToolbar = getActionToolbar();
    return actionToolbar != null ? actionToolbar.getToolbarDataContext() : DataManager.getInstance().getDataContext();
  }

  private ActionToolbar getActionToolbar() {
    return ComponentUtil.getParentOfType((Class<? extends ActionToolbar>)ActionToolbar.class, (Component)this);
  }

  protected void actionPerformed(final AnActionEvent event) {
    HelpTooltip.hide(this);
    if (isPopupMenuAction(event, myAction)) {
      showPopupMenu(event, (ActionGroup) myAction);
    }
    else {
      ActionUtil.performActionDumbAwareWithCallbacks(myAction, event, event.getDataContext());
    }
  }

  // used in Rider, please don't change visibility
  protected void showPopupMenu(AnActionEvent event, ActionGroup actionGroup) {
    if (myPopupState.isRecentlyHidden()) return; // do not show new popup
    final ActionManagerImpl am = (ActionManagerImpl) ActionManager.getInstance();
    String place = ActionPlaces.getActionGroupPopupPlace(event.getPlace());
    ActionPopupMenuImpl popupMenu = (ActionPopupMenuImpl)am.createActionPopupMenu(place, actionGroup, new MenuItemPresentationFactory() {
      @Override
      protected void processPresentation(Presentation presentation) {
        if (myNoIconsInPopup) {
          presentation.setIcon(null);
          presentation.setHoveredIcon(null);
        }
      }
    });
    popupMenu.setDataContextProvider(() -> getDataContext());
    popupMenu.getComponent().addPopupMenuListener(myPopupState);

    if (event.isFromActionToolbar()) {
      popupMenu.getComponent().show(this, 0, getHeight());
    }
    else {
      popupMenu.getComponent().show(this, getWidth(), 0);
    }
  }

  private static boolean isPopupMenuAction(AnActionEvent event, AnAction action) {
    return action instanceof ActionGroup &&
        !(action instanceof CustomComponentAction) &&
        ((ActionGroup) action).isPopup() &&
        !((ActionGroup) action).canBePerformed(event.getDataContext());
  }

  @Override
  public void removeNotify() {
    if (myRollover) {
      onMousePresenceChanged(false);
    }
    if (myPresentationListener != null) {
      myPresentation.removePropertyChangeListener(myPresentationListener);
      myPresentationListener = null;
    }
    myRollover = false;
    myMouseDown = false;
    HelpTooltip.dispose(this);
    super.removeNotify();
  }

  @Override
  public void addNotify() {
    super.addNotify();
    if (myPresentationListener == null) {
      myPresentation.addPropertyChangeListener(myPresentationListener = this::presentationPropertyChanged);
    }
    update();
  }

  public void update() {
    AnActionEvent e = AnActionEvent.createFromInputEvent(null, myPlace, myPresentation, getDataContext(), false, true);
    ActionUtil.performDumbAwareUpdate(LaterInvocator.isInModalContext(), myAction, e, false);
    updateToolTipText();
    updateIcon();
  }

  @Override
  public void setToolTipText(String toolTipText) {
    if (!Registry.is("ide.helptooltip.enabled")) {
      while (StringUtil.endsWithChar(toolTipText, '.')) {
        toolTipText = toolTipText.substring(0, toolTipText.length() - 1);
      }

      String shortcutsText = getShortcutText();
      if (StringUtil.isNotEmpty(shortcutsText)) {
        toolTipText += " (" + shortcutsText + ")";
      }
      super.setToolTipText(StringUtil.isNotEmpty(toolTipText) ? toolTipText : null);
    }
  }

  @Override
  public Insets getInsets() {
    ActionToolbarImpl owner = ComponentUtil.getParentOfType((Class<? extends ActionToolbarImpl>)ActionToolbarImpl.class, (Component)this);
    return owner != null && owner.getOrientation() == SwingConstants.VERTICAL ? JBInsets.create(2, 1) : JBInsets.create(1, 2);
  }

  @Override
  public void updateUI() {
    if (myLook != null) {
      myLook.updateUI();
    }
    updateToolTipText();
  }

  @Override
  public Dimension getPreferredSize() {
    if (myMinimumButtonSize != null) myMinimumButtonSize.update();
    Icon icon = getIcon();
    Dimension size = icon.getIconWidth() < myMinimumButtonSize.width && icon.getIconHeight() < myMinimumButtonSize.height ?
            new Dimension(myMinimumButtonSize) :
            new Dimension(Math.max(myMinimumButtonSize.width, icon.getIconWidth() + myInsets.left + myInsets.right),
                          Math.max(myMinimumButtonSize.height, icon.getIconHeight() + myInsets.top + myInsets.bottom));

    JBInsets.addTo(size, getInsets());
    return size;
  }

  public void setIconInsets(@Nullable Insets insets) {
    myInsets = insets != null ? JBUI.insets(insets) : JBUI.emptyInsets();
  }

  @Override
  public Dimension getMinimumSize() {
    return getPreferredSize();
  }

  /**
   * @return button's icon. Icon depends on action's state and button's state. It means that the method returns
   *         disabled icon if action is disabled.
   *         In case of rollover (POPPED) or pressed (PUSHED) button's state hovered icon is used (if presented)
   *         If the action's icon is {@code null} then it returns
   *         an empty icon.
   */
  public Icon getIcon() {
    boolean enabled = isButtonEnabled();
    int popState = getPopState();
    Icon hoveredIcon = (popState == POPPED || popState == PUSHED) ? myPresentation.getHoveredIcon() : null;
    Icon icon = enabled ? hoveredIcon != null ? hoveredIcon : myIcon : myDisabledIcon;
    return icon == null ? getFallbackIcon(enabled) : icon;
  }

  @NotNull
  protected Icon getFallbackIcon(boolean enabled) {
    return EmptyIcon.ICON_18;
  }

  public void updateIcon() {
    myIcon = myPresentation.getIcon();
    // set disabled icon if it is specified
    if (myPresentation.getDisabledIcon() != null) {
      myDisabledIcon = myPresentation.getDisabledIcon();
    }
    else if (myIcon == null) {
      myDisabledIcon = null;
    }
    else if (IconLoader.isGoodSize(myIcon)) {
      myDisabledIcon = IconLoader.getDisabledIcon(myIcon);
    }
    else {
      myDisabledIcon = null;
      Logger.getInstance(ActionButton.class).error("invalid icon (" + myIcon + ") for action " + myAction.getClass());
    }
  }

  protected void updateToolTipText() {
    String text = myPresentation.getText();
    String description = myPresentation.getDescription();
    if (Registry.is("ide.helptooltip.enabled")) {
      HelpTooltip.dispose(this);
      if (StringUtil.isNotEmpty(text) || StringUtil.isNotEmpty(description)) {
        HelpTooltip ht = new HelpTooltip().setTitle(text).setShortcut(getShortcutText());

        String id = ActionManager.getInstance().getId(myAction);
        if (!StringUtil.equals(text, description) && WHITE_LIST.contains(id)) {
          ht.setDescription(description);
        }
        ht.installOn(this);
      }
    } else {
      setToolTipText(text == null ? description : text);
    }
  }

  @Nullable
  protected String getShortcutText() {
    return KeymapUtil.getFirstKeyboardShortcutText(myAction);
  }

  @Override
  public void paintComponent(Graphics g) {
    jComponentPaint(g);
    paintButtonLook(g);
    if (shallPaintDownArrow()) {
      paintDownArrow(g);
    }
  }

  // used in Rider, please don't change visibility
  protected void jComponentPaint(Graphics g) {
    super.paintComponent(g);
  }

  protected boolean shallPaintDownArrow() {
    if (!(myAction instanceof ActionGroup && ((ActionGroup)myAction).isPopup())) return false;
    if (Boolean.TRUE == myAction.getTemplatePresentation().getClientProperty(HIDE_DROPDOWN_ICON)) return false;
    if (Boolean.TRUE == myPresentation.getClientProperty(HIDE_DROPDOWN_ICON)) return false;
    return true;
  }

  private void paintDownArrow(Graphics g) {
    Container parent = getParent();
    boolean horizontal = !(parent instanceof ActionToolbarImpl) ||
                         ((ActionToolbarImpl)parent).getOrientation() == SwingConstants.HORIZONTAL;
    int x = horizontal ? JBUIScale.scale(6) : JBUIScale.scale(5);
    int y = horizontal ? JBUIScale.scale(5) : JBUIScale.scale(6);
    Icon arrowIcon = isButtonEnabled() ? AllIcons.General.Dropdown :
                     IconLoader.getDisabledIcon(AllIcons.General.Dropdown);
    arrowIcon.paintIcon(this, g, x, y);
  }

  protected void paintButtonLook(Graphics g) {
    ActionButtonLook look = getButtonLook();
    if (isEnabled() || !StartupUiUtil.isUnderDarcula()) {
      look.paintBackground(g, this);
    }
    look.paintIcon(g, this, getIcon());
    look.paintBorder(g, this);
  }

  protected ActionButtonLook getButtonLook() {
    return myLook;
  }

  public void setLook(ActionButtonLook look) {
    myLook = look == null ? ActionButtonLook.SYSTEM_LOOK : look;
    repaint();
  }

  @Override
  protected void processMouseEvent(MouseEvent e) {
    IdeMouseEventDispatcher.requestFocusInNonFocusedWindow(e);
    super.processMouseEvent(e);
    if (e.isConsumed()) return;
    boolean skipPress = checkSkipPressForEvent(e);
    switch (e.getID()) {
      case MouseEvent.MOUSE_PRESSED:
        if (skipPress || !isButtonEnabled()) return;
        myMouseDown = true;
        onMousePressed(e);
        ourGlobalMouseDown = true;
        repaint();
        break;

      case MouseEvent.MOUSE_RELEASED:
        if (skipPress || !isButtonEnabled()) return;
        myMouseDown = false;
        ourGlobalMouseDown = false;
        onMouseReleased(e);
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

  @SuppressWarnings("unused")  // used in Rider
  protected void onMouseReleased(@NotNull MouseEvent e) {
    // Extension point
  }

  @SuppressWarnings("unused")  // used in Rider
  protected void onMousePressed(@NotNull MouseEvent e) {
    // Extension point
  }


  private static boolean checkSkipPressForEvent(@NotNull MouseEvent e) {
    return e.isMetaDown() || e.getButton() != MouseEvent.BUTTON1;
  }

  private int getPopState(boolean isPushed) {
    if (isPushed || myRollover && myMouseDown && isButtonEnabled()) {
      return PUSHED;
    }
    else if (myRollover && isButtonEnabled()) {
      return POPPED;
    }
    else if (isFocusOwner()) {
      return SELECTED;
    }
    else {
      return NORMAL;
    }
  }

  @Override
  public AnAction getAction() {
    return myAction;
  }

  protected void presentationPropertyChanged(@NotNull PropertyChangeEvent e) {
    String propertyName = e.getPropertyName();
    if (Presentation.PROP_TEXT.equals(propertyName) || Presentation.PROP_DESCRIPTION.equals(propertyName)) {
      updateToolTipText();
    }
    else if (Presentation.PROP_ENABLED.equals(propertyName) || Presentation.PROP_ICON.equals(propertyName)) {
      updateIcon();
      repaint();
    }
    else if (Presentation.PROP_DISABLED_ICON.equals(propertyName)) {
      myDisabledIcon = myPresentation.getDisabledIcon();
      repaint();
    }
    else if ("selected".equals(propertyName)) {
      repaint();
    }
    else if (HIDE_DROPDOWN_ICON.toString().equals(propertyName)) {
      repaint();
    }
  }

  // Accessibility

  @Override
  @NotNull
  public AccessibleContext getAccessibleContext() {
    AccessibleContext context = accessibleContext;
    if(context == null) {
      accessibleContext = context = new AccessibleActionButton();
    }

    return context;
  }

  protected class AccessibleActionButton extends JComponent.AccessibleJComponent implements AccessibleAction {
    AccessibleActionButton() {
    }

    @Override
    public AccessibleRole getAccessibleRole() {
      return AccessibleRole.PUSH_BUTTON;
    }

    @Override
    public String getAccessibleName() {
      String name = accessibleName;
      if (name == null) {
        name = (String)getClientProperty(ACCESSIBLE_NAME_PROPERTY);
        if (name == null) {
          name = ActionButton.this.getToolTipText();
          if (name == null) {
            name = myPresentation.getText();
            if (name == null) {
              name = super.getAccessibleName();
            }
          }
        }
      }

      return name;
    }

    @Override
    public String getAccessibleDescription() {
      return AccessibleContextUtil.getUniqueDescription(this, super.getAccessibleDescription());
    }

    @Override
    public AccessibleIcon[] getAccessibleIcon() {
      Icon icon = getIcon();
      if (icon instanceof Accessible) {
        AccessibleContext context = ((Accessible)icon).getAccessibleContext();
        if (context instanceof AccessibleIcon) {
          return new AccessibleIcon[]{(AccessibleIcon)context};
        }
      }

      return null;
    }

    @Override
    public AccessibleStateSet getAccessibleStateSet() {
      AccessibleStateSet var1 = super.getAccessibleStateSet();
      int state = getPopState();

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

      if (isFocusOwner()) {
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
      if (index == 0) {
        click();
        return true;
      }
      return false;
    }
  }

  // Contains actions IDs which descriptions are permitted for displaying in the ActionButton tooltip
  private static final Set<String> WHITE_LIST = ContainerUtil.immutableSet("ExternalSystem.ProjectRefreshAction", "LoadConfigurationAction");
}
