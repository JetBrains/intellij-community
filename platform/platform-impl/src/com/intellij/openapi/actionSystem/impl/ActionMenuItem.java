// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ui.UISettings;
import com.intellij.internal.statistic.collectors.fus.actions.persistence.MainMenuCollector;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.actionholder.ActionRef;
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
import com.intellij.ui.mac.screenmenu.MenuItem;
import com.intellij.ui.plaf.beg.BegMenuItemUI;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.LafIconLookup;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import static com.intellij.openapi.keymap.KeymapUtil.getActiveKeymapShortcuts;

public class ActionMenuItem extends JBCheckBoxMenuItem {
  static final Icon EMPTY_ICON = EmptyIcon.create(16, 1);

  private final ActionRef<AnAction> myAction;
  private final Presentation myPresentation;
  private final String myPlace;
  private final boolean myInsideCheckedGroup;
  private final boolean myEnableMnemonics;
  private final boolean myToggleable;
  private final DataContext myContext;
  private boolean myToggled;
  private final boolean myUseDarkIcons;
  private final MenuItem myScreenMenuItemPeer;

  public ActionMenuItem(@NotNull AnAction action,
                        @NotNull Presentation presentation,
                        @NotNull String place,
                        @NotNull DataContext context,
                        boolean enableMnemonics,
                        boolean unused,
                        boolean insideCheckedGroup,
                        boolean useDarkIcons,
                        MenuItem screenMenuItemPeer) {
    myAction = ActionRef.fromAction(action);
    myPresentation = presentation;
    myPlace = place;
    myContext = context;
    myEnableMnemonics = enableMnemonics;
    myToggleable = action instanceof Toggleable;
    myInsideCheckedGroup = insideCheckedGroup;
    myUseDarkIcons = useDarkIcons;
    myScreenMenuItemPeer = screenMenuItemPeer;
    if (myScreenMenuItemPeer != null) {
      myScreenMenuItemPeer.setActionDelegate(()-> {
        // Called on AppKit when menu opening
        if (isToggleable()) {
          myToggled = !myToggled;
          myScreenMenuItemPeer.setState(myToggled);
        }

        ApplicationManager.getApplication().invokeLater(()->{
          IdeFocusManager focusManager = IdeFocusManager.findInstanceByContext(myContext);

          focusManager.runOnOwnContext(myContext, () -> {
            AWTEvent currentEvent = IdeEventQueue.getInstance().getTrueCurrentEvent();
            final AnActionEvent event = new AnActionEvent(
              currentEvent instanceof InputEvent ? (InputEvent)currentEvent : null,
              myContext, myPlace, myPresentation, ActionManager.getInstance(), 0, true, false
            );
            AnAction menuItemAction = myAction.getAction();
            if (ActionUtil.lastUpdateAndCheckDumb(menuItemAction, event, false)) {
              ActionUtil.performActionDumbAwareWithCallbacks(menuItemAction, event);
            }
          });
        });//invokeLater
      });//setActionDelegate
    }

    addActionListener(new ActionTransmitter());
    setBorderPainted(false);

    updateUI();
    init();
  }

  public ActionMenuItem(@NotNull AnAction action,
                        @NotNull Presentation presentation,
                        @NotNull String place,
                        @NotNull DataContext context,
                        boolean enableMnemonics,
                        boolean unused,
                        boolean insideCheckedGroup,
                        boolean useDarkIcons
  ) {
    this(action, presentation, place, context, enableMnemonics, unused, insideCheckedGroup, useDarkIcons, null);
  }

  public @NotNull AnAction getAnAction() {
    return myAction.getAction();
  }

  public @NotNull String getPlace() {
    return myPlace;
  }

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

  private void init() {
    updateFromPresentation();

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

  void updateFromPresentation() {
    setVisible(myPresentation.isVisible());
    setEnabled(myPresentation.isEnabled());
    setMnemonic(myPresentation.getMnemonic());
    setText(myPresentation.getText(myEnableMnemonics));
    setDisplayedMnemonicIndex(myPresentation.getDisplayedMnemonicIndex());
    updateIcon();

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
        //If action has Enter shortcut, do not add it. Otherwise, user won't be able to chose any ActionMenuItem other than that
        if (!isEnterKeyStroke(firstKeyStroke)) {
          setAccelerator(firstKeyStroke);
          if (myScreenMenuItemPeer != null) myScreenMenuItemPeer.setLabel(getText(), firstKeyStroke);
          if (false && KeymapUtil.isSimplifiedMacShortcuts()) {
            // TODO: fix simplifiedMacShortcuts (broken navigation with keys)
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
    ActionMenu.showDescriptionInStatusBar(isIncluded, this, myPresentation.getDescription());
  }

  @NlsSafe
  public String getFirstShortcutText() {
    return KeymapUtil.getFirstKeyboardShortcutText(myAction.getAction());
  }

  private void updateIcon() {
    myToggled = isToggleable() && Toggleable.isSelected(myPresentation);
    if (isToggleable() && (myPresentation.getIcon() == null || myInsideCheckedGroup || !UISettings.getInstance().getShowIconsInMenus())) {
      if (ActionPlaces.MAIN_MENU.equals(myPlace) && SystemInfo.isMacSystemMenu) {
        setState(myToggled);
        if (myScreenMenuItemPeer != null) myScreenMenuItemPeer.setState(myToggled);
        setIcon(wrapNullIcon(getIcon()));
      }
      else if (myToggled) {
        setIcon(LafIconLookup.getIcon("checkmark"));
        setSelectedIcon(LafIconLookup.getSelectedIcon("checkmark"));
        setDisabledIcon(LafIconLookup.getDisabledIcon("checkmark"));
      }
      else {
        setIcon(EmptyIcon.ICON_16);
        setSelectedIcon(EmptyIcon.ICON_16);
        setDisabledIcon(EmptyIcon.ICON_16);
      }
    }
    else if (UISettings.getInstance().getShowIconsInMenus()) {
      Icon icon = myPresentation.getIcon();
      if (isToggleable() && myToggled) {
        icon = new PoppedIcon(icon, 16, 16);
      }
      Icon disabled = myPresentation.getDisabledIcon();
      if (disabled == null) {
        disabled = icon == null ? null : IconLoader.getDisabledIcon(icon);
      }
      Icon selected = myPresentation.getSelectedIcon();
      if (selected == null) {
        selected = icon;
      }

      setIcon(wrapNullIcon(myPresentation.isEnabled() ? icon : disabled));
      setSelectedIcon(wrapNullIcon(selected));
      setDisabledIcon(wrapNullIcon(disabled));
    }
  }

  private Icon wrapNullIcon(Icon icon) {
    if (ActionMenu.isShowNoIcons()) {
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
    if (SystemInfo.isMacSystemMenu && ActionPlaces.MAIN_MENU.equals(myPlace) && icon != null) {
      // JDK can't paint correctly our HiDPI icons at the system menu bar
      icon = IconLoader.getMenuBarIcon(icon, myUseDarkIcons);
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

  private final class ActionTransmitter implements ActionListener {

    @Override
    public void actionPerformed(@NotNull ActionEvent e) {
      IdeFocusManager focusManager = IdeFocusManager.findInstanceByContext(myContext);
      String id = ActionManager.getInstance().getId(myAction.getAction());
      if (id != null) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed("context.menu.click.stats." + id.replace(' ', '.'));
      }

      focusManager.runOnOwnContext(myContext, () -> {
        AWTEvent currentEvent = IdeEventQueue.getInstance().getTrueCurrentEvent();
        final AnActionEvent event = new AnActionEvent(
          currentEvent instanceof InputEvent ? (InputEvent)currentEvent : null,
          myContext, myPlace, myPresentation, ActionManager.getInstance(), e.getModifiers(), true, false
        );
        AnAction menuItemAction = myAction.getAction();
        if (ActionUtil.lastUpdateAndCheckDumb(menuItemAction, event, false)) {
          ActionUtil.performActionDumbAwareWithCallbacks(menuItemAction, event);
        }
      });
    }
  }
}
