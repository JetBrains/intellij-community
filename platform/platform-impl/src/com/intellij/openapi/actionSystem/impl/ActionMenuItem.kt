// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ui.UISettings;
import com.intellij.internal.statistic.collectors.fus.actions.persistence.MainMenuCollector;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.actionholder.ActionRef;
import com.intellij.openapi.actionSystem.impl.actionholder.ActionRefKt;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.application.TransactionGuardImpl;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.components.JBCheckBoxMenuItem;
import com.intellij.ui.icons.IconUtilKt;
import com.intellij.ui.mac.screenmenu.Menu;
import com.intellij.ui.mac.screenmenu.MenuItem;
import com.intellij.ui.plaf.beg.BegMenuItemUI;
import com.intellij.ui.popup.KeepingPopupOpenAction;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.LafIconLookup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import static com.intellij.openapi.keymap.KeymapUtil.getActiveKeymapShortcuts;

public final class ActionMenuItem extends JBCheckBoxMenuItem {
  static final Icon EMPTY_ICON = EmptyIcon.create(16, 1);

  private final ActionRef<AnAction> myAction;
  private final String myPlace;
  private final boolean myInsideCheckedGroup;
  private final boolean myEnableMnemonics;
  private final boolean myToggleable;
  private final DataContext myContext;
  private final boolean myUseDarkIcons;
  private final @Nullable MenuItem myScreenMenuItemPeer;

  private String myDescription;
  private boolean myToggled;
  private boolean myKeepMenuOpen;

  ActionMenuItem(@NotNull AnAction action,
                 @NotNull String place,
                 @NotNull DataContext context,
                 boolean enableMnemonics,
                 boolean insideCheckedGroup,
                 boolean useDarkIcons) {
    myAction = ActionRefKt.createActionRef(action);
    myPlace = place;
    myContext = context;
    myEnableMnemonics = enableMnemonics;
    myToggleable = action instanceof Toggleable;
    myInsideCheckedGroup = insideCheckedGroup;
    myUseDarkIcons = useDarkIcons;
    addActionListener(e -> performAction(e.getModifiers()));
    setBorderPainted(false);

    if (Menu.isJbScreenMenuEnabled() && ActionPlaces.MAIN_MENU.equals(myPlace)) {
      myScreenMenuItemPeer = new MenuItem();
      myScreenMenuItemPeer.setActionDelegate(() -> {
        // Called on AppKit when user activates menu item
        if (isToggleable()) {
          myToggled = !myToggled;
          myScreenMenuItemPeer.setState(myToggled);
        }
        SwingUtilities.invokeLater(() -> {
          if (myAction.getAction().isEnabledInModalContext() ||
              !Boolean.TRUE.equals(myContext.getData(PlatformCoreDataKeys.IS_MODAL_CONTEXT))) {
            ((TransactionGuardImpl)TransactionGuard.getInstance()).performUserActivity(() -> performAction(0));
          }
        });
      });
    }
    else {
      myScreenMenuItemPeer = null;
    }

    updateUI();
    updateAccelerator();
  }

  public @NotNull AnAction getAnAction() {
    return myAction.getAction();
  }

  public @NotNull String getPlace() {
    return myPlace;
  }

  public @Nullable MenuItem getScreenMenuItemPeer() { return myScreenMenuItemPeer; }

  private static boolean isEnterKeyStroke(KeyStroke keyStroke) {
    return keyStroke.getKeyCode() == KeyEvent.VK_ENTER && keyStroke.getModifiers() == 0;
  }

  @Override
  public void fireActionPerformed(ActionEvent event) {
    Application app = ApplicationManager.getApplication();
    if (!app.isDisposed() && ActionPlaces.MAIN_MENU.equals(myPlace)) {
      MainMenuCollector.getInstance().record(myAction.getAction());
    }
    ((TransactionGuardImpl)TransactionGuard.getInstance()).performUserActivity(() -> super.fireActionPerformed(event));
  }

  private void updateAccelerator() {
    AnAction action = myAction.getAction();
    String id = ActionManager.getInstance().getId(action);
    if (id != null) {
      setAcceleratorFromShortcuts(getActiveKeymapShortcuts(id).getShortcuts());
    }
    else {
      ShortcutSet shortcutSet = action.getShortcutSet();
      setAcceleratorFromShortcuts(shortcutSet.getShortcuts());
    }
  }

  void updateFromPresentation(@NotNull Presentation presentation) {
    // all items must be visible at this point
    //setVisible(presentation.isVisible());
    setEnabled(presentation.isEnabled());
    setText(presentation.getText(myEnableMnemonics));
    setMnemonic(presentation.getMnemonic());
    setDisplayedMnemonicIndex(presentation.getDisplayedMnemonicIndex());
    updateIcon(presentation);
    myDescription = presentation.getDescription();
    myKeepMenuOpen = myKeepMenuOpen || presentation.isMultiChoice() || myAction.getAction() instanceof KeepingPopupOpenAction;

    if (myScreenMenuItemPeer != null) {
      myScreenMenuItemPeer.setLabel(getText(), getAccelerator());
      myScreenMenuItemPeer.setEnabled(isEnabled());
    }
  }

  @Override
  public void setDisplayedMnemonicIndex(int index) throws IllegalArgumentException {
    super.setDisplayedMnemonicIndex(myEnableMnemonics ? index : -1);
  }

  @Override
  public void setMnemonic(int mnemonic) {
    super.setMnemonic(myEnableMnemonics ? mnemonic : 0);
  }

  private void setAcceleratorFromShortcuts(Shortcut @NotNull [] shortcuts) {
    for (Shortcut shortcut : shortcuts) {
      if (shortcut instanceof KeyboardShortcut) {
        final KeyStroke firstKeyStroke = ((KeyboardShortcut)shortcut).getFirstKeyStroke();
        // If the action has `Enter` shortcut, do not add it. Otherwise, user won't be able to choose any ActionMenuItem other than that
        if (!isEnterKeyStroke(firstKeyStroke)) {
          setAccelerator(firstKeyStroke);
          if (myScreenMenuItemPeer != null) myScreenMenuItemPeer.setLabel(getText(), firstKeyStroke);
          if (KeymapUtil.isSimplifiedMacShortcuts()) {
            final String shortcutText = KeymapUtil.getPreferredShortcutText(shortcuts);
            putClientProperty("accelerator.text", shortcutText);
            if (myScreenMenuItemPeer != null) myScreenMenuItemPeer.setAcceleratorText(shortcutText);
          }
        }
        break;
      }
    }
  }

  @Override
  public void updateUI() {
    setUI(BegMenuItemUI.createUI(this));
  }

  /**
   * Updates long description of action at the status bar.
   */
  @Override
  public void menuSelectionChanged(boolean isIncluded) {
    super.menuSelectionChanged(isIncluded);
    //noinspection HardCodedStringLiteral
    ActionMenu.showDescriptionInStatusBar(isIncluded, this, myDescription);
  }

  @NlsSafe
  public String getFirstShortcutText() {
    return KeymapUtil.getFirstKeyboardShortcutText(myAction.getAction());
  }

  private void updateIcon(@NotNull Presentation presentation) {
    myToggled = isToggleable() && Toggleable.isSelected(presentation);
    if (isToggleable() && (presentation.getIcon() == null || myInsideCheckedGroup || !UISettings.getInstance().getShowIconsInMenus())) {
      if (ActionPlaces.MAIN_MENU.equals(myPlace) && SystemInfo.isMacSystemMenu) {
        setState(myToggled);
        if (myScreenMenuItemPeer != null) myScreenMenuItemPeer.setState(myToggled);
        setIcon(wrapNullIcon(getIcon()));
      }
      else if (myToggled) {
        Icon checkmark = LafIconLookup.getIcon("checkmark");
        Icon selectedCheckmark = LafIconLookup.getSelectedIcon("checkmark");
        Icon disabledCheckmark = LafIconLookup.getDisabledIcon("checkmark");
        if (ActionMenu.shouldConvertIconToDarkVariant()) {
          checkmark = IconLoader.getDarkIcon(checkmark, true);
          selectedCheckmark = IconLoader.getDarkIcon(selectedCheckmark, true);
          disabledCheckmark = IconLoader.getDarkIcon(disabledCheckmark, true);
        }
        setIcon(checkmark);
        setSelectedIcon(selectedCheckmark);
        setDisabledIcon(disabledCheckmark);
      }
      else {
        setIcon(EmptyIcon.ICON_16);
        setSelectedIcon(EmptyIcon.ICON_16);
        setDisabledIcon(EmptyIcon.ICON_16);
      }
    }
    else if (UISettings.getInstance().getShowIconsInMenus()) {
      Icon icon = presentation.getIcon();
      if (isToggleable() && myToggled) {
        icon = new PoppedIcon(icon, 16, 16);
      }
      Icon disabled = presentation.getDisabledIcon();
      if (disabled == null) {
        disabled = icon == null ? null : IconLoader.getDisabledIcon(icon);
      }
      Icon selected = presentation.getSelectedIcon();
      if (selected == null) {
        selected = icon;
      }

      setIcon(wrapNullIcon(presentation.isEnabled() ? icon : disabled));
      setSelectedIcon(wrapNullIcon(selected));
      setDisabledIcon(wrapNullIcon(disabled));
    }
  }

  private Icon wrapNullIcon(Icon icon) {
    boolean isMainMenu = ActionPlaces.MAIN_MENU.equals(myPlace);
    if (isMainMenu && ActionMenu.isShowNoIcons(myAction.getAction())) {
      return null;
    }
    if (!ActionMenu.isAligned() || !ActionMenu.isAlignedInGroup()) {
      return icon;
    }
    if (icon == null && SystemInfo.isMacSystemMenu && ActionPlaces.MAIN_MENU.equals(myPlace)) {
      return EMPTY_ICON;
    }
    return icon;
  }

  @Override
  public void setIcon(Icon icon) {
    if (icon != null) {
      if (SystemInfo.isMacSystemMenu && ActionPlaces.MAIN_MENU.equals(myPlace)) {
        // JDK can't paint correctly our HiDPI icons at the system menu bar
        icon = IconUtilKt.getMenuBarIcon(icon, myUseDarkIcons);
      }
      else if (ActionMenu.shouldConvertIconToDarkVariant()) {
        icon = IconLoader.getDarkIcon(icon, true);
      }
    }
    super.setIcon(icon);
    if (myScreenMenuItemPeer != null) myScreenMenuItemPeer.setIcon(icon);
  }

  public boolean isToggleable() {
    return myToggleable;
  }

  @Override
  public boolean isSelected() {
    return myToggled;
  }

  public boolean isKeepMenuOpen() {
    return myKeepMenuOpen;
  }

  private void performAction(int modifiers) {
    IdeFocusManager focusManager = IdeFocusManager.findInstanceByContext(myContext);
    AnAction action = myAction.getAction();
    String id = ActionManager.getInstance().getId(action);
    if (id != null) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed("context.menu.click.stats." + id.replace(' ', '.'));
    }

    focusManager.runOnOwnContext(myContext, () -> {
      AWTEvent currentEvent = IdeEventQueue.getInstance().getTrueCurrentEvent();
      AnActionEvent event = new AnActionEvent(
        currentEvent instanceof InputEvent ? (InputEvent)currentEvent : null,
        myContext, myPlace, action.getTemplatePresentation().clone(),
        ActionManager.getInstance(), modifiers, true, false);
      if (ActionUtil.lastUpdateAndCheckDumb(action, event, false)) {
        ActionUtil.performActionDumbAwareWithCallbacks(action, event);
      }
    });
  }
}
