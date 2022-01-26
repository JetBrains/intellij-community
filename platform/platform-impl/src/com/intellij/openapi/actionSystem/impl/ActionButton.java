// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.icons.AllIcons;
import com.intellij.ide.HelpTooltip;
import com.intellij.internal.statistic.collectors.fus.ui.persistence.ToolbarClicksCollector;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.*;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.impl.IdeMouseEventDispatcher;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.ui.popup.PopupState;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.*;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Objects;
import java.util.Set;

public class ActionButton extends JComponent implements ActionButtonComponent, AnActionHolder, Accessible {
  // Contains action IDs which descriptions are permitted for displaying in the ActionButton tooltip
  @NonNls private static final Set<String> WHITE_LIST = Set.of("ExternalSystem.ProjectRefreshAction", "LoadConfigurationAction");

  /**
   * By default, a popup action group button displays 'dropdown' icon.
   * Use this key to avoid suppress that icon for a presentation or a template presentation like this:
   * {@code presentation.putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, true)}.
   */
  public static final Key<Boolean> HIDE_DROPDOWN_ICON = Key.create("HIDE_DROPDOWN_ICON");

  private JBDimension myMinimumButtonSize;
  private PropertyChangeListener myPresentationListener;
  private Icon myDisabledIcon;
  protected Icon myIcon;
  protected final Presentation myPresentation;
  protected final AnAction myAction;
  protected final String myPlace;
  protected final PopupState<JPopupMenu> myPopupState = PopupState.forPopupMenu();
  private ActionButtonLook myLook = ActionButtonLook.SYSTEM_LOOK;
  private boolean myMouseDown;
  protected boolean myRollover;
  private boolean wasPopupJustClosedByButtonClick = false;

  private static boolean ourGlobalMouseDown;

  private boolean myNoIconsInPopup;
  private Insets myInsets;
  private boolean myManualUpdate;

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
    return isEnabled(super.isEnabled());
  }

  protected boolean isEnabled(boolean componentEnabled) {
    return componentEnabled && myPresentation.isEnabled();
  }

  private void onMousePresenceChanged(boolean setInfo) {
    ActionMenu.showDescriptionInStatusBar(setInfo, this, myPresentation.getDescription());
  }

  public void click() {
    performAction(makeClickMouseEvent());
  }

  @NotNull
  private MouseEvent makeClickMouseEvent() {
    return new MouseEvent(this, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 0, 0, 1, false);
  }

  private void performAction(MouseEvent e) {
    AnActionEvent event = AnActionEvent.createFromInputEvent(e, myPlace, myPresentation, getDataContext(), false, true);
    if (ActionUtil.lastUpdateAndCheckDumb(myAction, event, false) && isEnabled()) {
      ActionUtil.performDumbAwareWithCallbacks(myAction, event, () -> actionPerformed(event));
      if (event.getInputEvent() instanceof MouseEvent) {
        ToolbarClicksCollector.record(myAction, myPlace, e, event.getDataContext());
      }
      ActionToolbar toolbar = ActionToolbar.findToolbarBy(this);
      if (toolbar != null) {
        toolbar.updateActionsImmediately();
      }
    }
  }

  protected DataContext getDataContext() {
    return ActionToolbar.getDataContextFor(this);
  }

  protected void actionPerformed(@NotNull AnActionEvent event) {
    HelpTooltip.hide(this);
    if (isPopupMenuAction(event)) {
      if (!wasPopupJustClosedByButtonClick) {
        showActionGroupPopup((ActionGroup)myAction, event);
      }
      wasPopupJustClosedByButtonClick = false;
    }
    else {
      myAction.actionPerformed(event);
    }
  }

  protected void showActionGroupPopup(@NotNull ActionGroup actionGroup, @NotNull AnActionEvent event) {
    createAndShowActionGroupPopup(actionGroup, event);
  }

  protected @NotNull JBPopup createAndShowActionGroupPopup(@NotNull ActionGroup actionGroup, @NotNull AnActionEvent event) {
    PopupFactoryImpl.ActionGroupPopup popup = new PopupFactoryImpl.ActionGroupPopup(
      null, actionGroup, event.getDataContext(), false,
      false, true, false,
      null, -1, null,
      ActionPlaces.getActionGroupPopupPlace(event.getPlace()),
      createPresentationFactory(), false) {
      @Override
      public void cancel(InputEvent inputEvent) {
        super.cancel(inputEvent);
        if (inputEvent instanceof MouseEvent && inputEvent.getID() == MouseEvent.MOUSE_PRESSED) {
          MouseEvent e = (MouseEvent)inputEvent;
          Component target = ObjectUtils.doIfNotNull(e.getComponent(), c -> SwingUtilities.getDeepestComponentAt(c, e.getX(), e.getY()));
          if (ActionButton.this == target) wasPopupJustClosedByButtonClick = true;
        }
      }
    };
    popup.setShowSubmenuOnHover(true);
    popup.showUnderneathOf(event.getInputEvent().getComponent());
    return popup;
  }

  @NotNull
  private MenuItemPresentationFactory createPresentationFactory() {
    return new MenuItemPresentationFactory() {
      @Override
      protected void processPresentation(@NotNull Presentation presentation) {
        super.processPresentation(presentation);
        if (myNoIconsInPopup) {
          presentation.setIcon(null);
          presentation.setHoveredIcon(null);
        }
      }
    };
  }

  private boolean isPopupMenuAction(@NotNull AnActionEvent event) {
    if (!(myAction instanceof ActionGroup)) return false;
    if (myAction instanceof CustomComponentAction) return false;
    if (!event.getPresentation().isPopupGroup()) return false;
    // do not call potentially slow `canBePerformed` for a button managed by a toolbar
    if (event.getPresentation().isPerformGroup() ||
        myManualUpdate && ((ActionGroup)myAction).canBePerformed(event.getDataContext())) return false;
    return true;
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
    if (!(getParent() instanceof ActionToolbar)) {
      ActionManagerEx.doWithLazyActionManager(__ -> update());
    }
    else {
      updateToolTipText();
      updateIcon();
    }
  }

  public void update() {
    myManualUpdate = true;
    // the following code mirrors the ActionUpdater#updateActionReal code
    boolean wasPopup = myAction instanceof ActionGroup && ((ActionGroup)myAction).isPopup(myPlace);
    myPresentation.setPopupGroup(myAction instanceof ActionGroup && (myPresentation.isPopupGroup() || wasPopup));
    AnActionEvent e = AnActionEvent.createFromInputEvent(null, myPlace, myPresentation, getDataContext(), false, true);
    ActionUtil.performDumbAwareUpdate(LaterInvocator.isInModalContext(), myAction, e, false);
    ActionUpdater.assertActionGroupPopupStateIsNotChanged(myAction, myPlace, wasPopup, myPresentation);
    updateToolTipText();
    updateIcon();
  }

  @Override
  public void setToolTipText(@NlsContexts.Tooltip String toolTipText) {
    if (!Registry.is("ide.helptooltip.enabled")) {
      while (StringUtil.endsWithChar(toolTipText, '.')) {
        toolTipText = toolTipText.substring(0, toolTipText.length() - 1);
      }

      String shortcutsText = getShortcutText();
      if (Strings.isNotEmpty(shortcutsText)) {
        toolTipText += " (" + shortcutsText + ")";
      }
      super.setToolTipText(Strings.isNotEmpty(toolTipText) ? toolTipText : null);
    }
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
    myInsets = insets != null ? JBInsets.create(insets) : JBInsets.emptyInsets();
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
    boolean enabled = isEnabled();
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
      if (Strings.isNotEmpty(text) || Strings.isNotEmpty(description)) {
        HelpTooltip ht = new HelpTooltip().setTitle(text).setShortcut(getShortcutText());
        if (myAction instanceof TooltipLinkProvider) {
          TooltipLinkProvider.TooltipLink link = ((TooltipLinkProvider)myAction).getTooltipLink(this);
          if (link != null) {
            ht.setLink(link.tooltip, link.action);
          }
        }
        String id = ActionManager.getInstance().getId(myAction);
        if (!Objects.equals(text, description) && ((id != null && WHITE_LIST.contains(id)) || myAction instanceof TooltipDescriptionProvider)) {
          ht.setDescription(description);
        }
        ht.installOn(this);
      }
    }
    else {
      HelpTooltip.dispose(this);
      setToolTipText(text == null ? description : text);
    }
  }

  @Nullable
  protected @NlsSafe String getShortcutText() {
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
    if (!(myAction instanceof ActionGroup)) return false;
    if (!myPresentation.isPopupGroup()) return false;
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
    Icon arrowIcon = isEnabled() ? AllIcons.General.Dropdown :
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
        if (skipPress || !isEnabled()) return;
        myMouseDown = true;
        onMousePressed(e);
        ourGlobalMouseDown = true;
        repaint();
        break;

      case MouseEvent.MOUSE_RELEASED:
        if (skipPress || !isEnabled()) return;
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

  protected void resetMouseState() {
    myMouseDown = false;
    ourGlobalMouseDown = false;
  }

  protected void onMouseReleased(@NotNull MouseEvent e) {
    resetMouseState();
    // Extension point
  }

  protected void onMousePressed(@NotNull MouseEvent e) {
    // Extension point
  }


  private static boolean checkSkipPressForEvent(@NotNull MouseEvent e) {
    return e.isMetaDown() || e.getButton() != MouseEvent.BUTTON1;
  }

  private int getPopState(boolean isPushed) {
    if (isPushed || myRollover && myMouseDown && isEnabled()) {
      return PUSHED;
    }
    else if (myRollover && isEnabled()) {
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
  public @NotNull AnAction getAction() {
    return myAction;
  }

  protected void presentationPropertyChanged(@NotNull PropertyChangeEvent e) {
    @NonNls String propertyName = e.getPropertyName();
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
    protected AccessibleActionButton() {
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
      setCustomAccessibleStateSet(var1);
      return var1;
    }

    protected void setCustomAccessibleStateSet(@NotNull AccessibleStateSet accessibleStateSet) {
      int state = getPopState();

      // TODO: Not sure what the "POPPED" state represents
      //if (state == POPPED) {
      //  var1.add(AccessibleState.?);
      //}

      if (state == ActionButtonComponent.PUSHED) {
        accessibleStateSet.add(AccessibleState.PRESSED);
      }
      if (state == ActionButtonComponent.SELECTED) {
        accessibleStateSet.add(AccessibleState.CHECKED);
      }

      if (isFocusOwner()) {
        accessibleStateSet.add(AccessibleState.FOCUSED);
      }
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
}
