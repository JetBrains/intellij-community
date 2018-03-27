// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.laf.darcula.DarculaLaf;
import com.intellij.internal.statistic.customUsageCollectors.actions.MainMenuCollector;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.actionholder.ActionRef;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.components.JBCheckBoxMenuItem;
import com.intellij.ui.plaf.beg.BegMenuItemUI;
import com.intellij.ui.plaf.gtk.GtkMenuItemUI;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.plaf.MenuItemUI;
import javax.swing.plaf.synth.SynthMenuItemUI;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashSet;
import java.util.Set;

import static com.intellij.openapi.keymap.KeymapUtil.getActiveKeymapShortcuts;

public class ActionMenuItem extends JBCheckBoxMenuItem {
  private final ActionRef<AnAction> myAction;
  private final Presentation myPresentation;
  private final String myPlace;
  private final boolean myInsideCheckedGroup;
  private final boolean myEnableMnemonics;
  private final boolean myToggleable;
  private DataContext myContext;
  private AnActionEvent myEvent;
  private MenuItemSynchronizer myMenuItemSynchronizer;
  private boolean myToggled;

  public ActionMenuItem(final AnAction action,
                        final Presentation presentation,
                        @NotNull final String place,
                        @NotNull DataContext context,
                        final boolean enableMnemonics,
                        final boolean prepareNow,
                        final boolean insideCheckedGroup) {
    myAction = ActionRef.fromAction(action);
    myPresentation = presentation;
    myPlace = place;
    myContext = context;
    myEnableMnemonics = enableMnemonics;
    myToggleable = action instanceof Toggleable;
    myInsideCheckedGroup = insideCheckedGroup;

    myEvent = new AnActionEvent(null, context, place, myPresentation, ActionManager.getInstance(), 0, true, false);
    addActionListener(new ActionTransmitter());
    setBorderPainted(false);

    updateUI();

    if (prepareNow) {
      init();
    }
    else {
      setText("loading...");
    }
  }

  public AnAction getAnAction() {
    return myAction.getAction();
  }

  public String getPlace() {
    return myPlace;
  }

  private static boolean isEnterKeyStroke(KeyStroke keyStroke) {
    return keyStroke.getKeyCode() == KeyEvent.VK_ENTER && keyStroke.getModifiers() == 0;
  }

  public void prepare() {
    init();
    installSynchronizer();
  }

  /**
   * We have to make this method public to allow BegMenuItemUI to invoke it.
   */
  @Override
  public void fireActionPerformed(ActionEvent event) {
    AnAction action = myAction.getAction();
    if (action != null && ActionPlaces.MAIN_MENU.equals(myPlace)) {
      MainMenuCollector.getInstance().record(action);
    }
    TransactionGuard.submitTransaction(ApplicationManager.getApplication(), () -> super.fireActionPerformed(event));
  }

  @Override
  public void addNotify() {
    super.addNotify();
    installSynchronizer();
    init();
  }

  @Override
  public void removeNotify() {
    uninstallSynchronizer();
    super.removeNotify();
  }

  private void installSynchronizer() {
    if (myMenuItemSynchronizer == null) {
      myMenuItemSynchronizer = new MenuItemSynchronizer();
    }
  }

  private void uninstallSynchronizer() {
    if (myMenuItemSynchronizer != null) {
      Disposer.dispose(myMenuItemSynchronizer);
      myMenuItemSynchronizer = null;
    }
  }

  private void init() {
    setVisible(myPresentation.isVisible());
    setEnabled(myPresentation.isEnabled());
    setMnemonic(myEnableMnemonics ? myPresentation.getMnemonic() : 0);
    setText(myPresentation.getText());
    final int mnemonicIndex = myEnableMnemonics ? myPresentation.getDisplayedMnemonicIndex() : -1;

    if (getText() != null && mnemonicIndex >= 0 && mnemonicIndex < getText().length()) {
      setDisplayedMnemonicIndex(mnemonicIndex);
    }

    AnAction action = myAction.getAction();
    updateIcon(action);
    String id = ActionManager.getInstance().getId(action);
    if (id != null) {
      setAcceleratorFromShortcuts(getActiveKeymapShortcuts(id).getShortcuts());
    }
    else {
      final ShortcutSet shortcutSet = action.getShortcutSet();
      if (shortcutSet != null) {
        setAcceleratorFromShortcuts(shortcutSet.getShortcuts());
      }
    }
  }

  private void setAcceleratorFromShortcuts(@NotNull Shortcut[] shortcuts) {
    for (Shortcut shortcut : shortcuts) {
      if (shortcut instanceof KeyboardShortcut) {
        final KeyStroke firstKeyStroke = ((KeyboardShortcut)shortcut).getFirstKeyStroke();
        //If action has Enter shortcut, do not add it. Otherwise, user won't be able to chose any ActionMenuItem other than that
        if (!isEnterKeyStroke(firstKeyStroke)) {
          setAccelerator(firstKeyStroke);
        }
        break;
      }
    }
  }

  @Override
  public void updateUI() {
    if (UIUtil.isStandardMenuLAF()) {
      super.updateUI();
    }
    else {
      setUI(BegMenuItemUI.createUI(this));
    }
  }

  @Override
  public void setUI(MenuItemUI ui) {
    MenuItemUI newUi = UIUtil.isUnderGTKLookAndFeel() && ui instanceof SynthMenuItemUI ? new GtkMenuItemUI((SynthMenuItemUI)ui) : ui;
    super.setUI(newUi);
  }

  /**
   * Updates long description of action at the status bar.
   */
  @Override
  public void menuSelectionChanged(boolean isIncluded) {
    super.menuSelectionChanged(isIncluded);
    ActionMenu.showDescriptionInStatusBar(isIncluded, this, myPresentation.getDescription());
  }

  public String getFirstShortcutText() {
    return KeymapUtil.getFirstKeyboardShortcutText(myAction.getAction());
  }

  public void updateContext(@NotNull DataContext context) {
    myContext = context;
    myEvent = new AnActionEvent(null, context, myPlace, myPresentation, ActionManager.getInstance(), 0, true, false);
  }

  private void updateIcon(AnAction action) {
    if (isToggleable() && (myPresentation.getIcon() == null || myInsideCheckedGroup || !UISettings.getInstance().getShowIconsInMenus())) {
      action.update(myEvent);
      myToggled = Boolean.TRUE.equals(myEvent.getPresentation().getClientProperty(Toggleable.SELECTED_PROPERTY));
      if (ActionPlaces.MAIN_MENU.equals(myPlace) && SystemInfo.isMacSystemMenu ||
          UIUtil.isUnderWindowsLookAndFeel() && SystemInfo.isWin7OrNewer) {
        setState(myToggled);
      }
      else if (!(getUI() instanceof GtkMenuItemUI)) {
        if (myToggled) {
          boolean darcula = UIUtil.isUnderDarcula();
          setIcon(DarculaLaf.getCheckmarkIcon(darcula));
          setSelectedIcon(DarculaLaf.getCheckmarkSelectedIcon(darcula));
          setDisabledIcon(DarculaLaf.getCheckmarkDisabledIcon(darcula));
        }
        else {
          setIcon(EmptyIcon.ICON_16);
          setSelectedIcon(EmptyIcon.ICON_16);
          setDisabledIcon(EmptyIcon.ICON_16);
        }
      }
    }
    else {
      if (UISettings.getInstance().getShowIconsInMenus()) {
        Icon icon = myPresentation.getIcon();
        if (action instanceof ToggleAction && ((ToggleAction)action).isSelected(myEvent)) {
          icon = new PoppedIcon(icon, 16, 16);
        }
        setIcon(icon);
        Icon selected = myPresentation.getSelectedIcon();
        setSelectedIcon(selected != null ? selected : icon);
        Icon disabled = myPresentation.getDisabledIcon();
        setDisabledIcon(disabled != null ? disabled : IconLoader.getDisabledIcon(icon));
      }
    }
  }

  @Override
  public void setIcon(Icon icon) {
    if (SystemInfo.isMacSystemMenu && ActionPlaces.MAIN_MENU.equals(myPlace)) {
      // JDK can't paint correctly our HiDPI icons at the system menu bar
      icon = IconLoader.get1xIcon(icon);
    }
    super.setIcon(icon);
  }

  public boolean isToggleable() {
    return myToggleable;
  }

  @Override
  public boolean isSelected() {
    return myToggled;
  }

  private final class ActionTransmitter implements ActionListener {
    /**
     * @param component component
     * @return whether the component in Swing tree or not. This method is more
     *         weak then {@link Component#isShowing() }
     */
    private boolean isInTree(final Component component) {
      if (component instanceof Window) {
        return component.isShowing();
      }
      else {
        Window windowAncestor = SwingUtilities.getWindowAncestor(component);
        return windowAncestor != null && windowAncestor.isShowing();
      }
    }

    @Override
    public void actionPerformed(final ActionEvent e) {
      final IdeFocusManager fm = IdeFocusManager.findInstanceByContext(myContext);
      final ActionCallback typeAhead = new ActionCallback();
      final String id = ActionManager.getInstance().getId(myAction.getAction());
      if (id != null) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed("context.menu.click.stats." + id.replace(' ', '.'));
      }
      fm.typeAheadUntil(typeAhead, getText());
      fm.runOnOwnContext(myContext, () -> {
        final AnActionEvent event = new AnActionEvent(
          new MouseEvent(ActionMenuItem.this, MouseEvent.MOUSE_PRESSED, 0, e.getModifiers(), getWidth() / 2, getHeight() / 2, 1, false),
          myContext, myPlace, myPresentation, ActionManager.getInstance(), e.getModifiers(), true, false
        );
        final AnAction menuItemAction = myAction.getAction();
        if (ActionUtil.lastUpdateAndCheckDumb(menuItemAction, event, false)) {
          ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
          actionManager.fireBeforeActionPerformed(menuItemAction, myContext, event);
          fm.doWhenFocusSettlesDown(typeAhead::setDone);
          ActionUtil.performActionDumbAware(menuItemAction, event);
          actionManager.queueActionPerformedEvent(menuItemAction, myContext, event);
        }
        else {
          typeAhead.setDone();
        }
      });
    }
  }

  private final class MenuItemSynchronizer implements PropertyChangeListener, Disposable {
    @NonNls private static final String SELECTED = "selected";

    private final Set<String> mySynchronized = new HashSet<>();

    private MenuItemSynchronizer() {
      myPresentation.addPropertyChangeListener(this);
    }

    @Override
    public void dispose() {
      myPresentation.removePropertyChangeListener(this);
    }

    @Override
    public void propertyChange(PropertyChangeEvent e) {
      boolean queueForDispose = getParent() == null;

      String name = e.getPropertyName();
      if (mySynchronized.contains(name)) return;

      mySynchronized.add(name);

      try {
        if (Presentation.PROP_VISIBLE.equals(name)) {
          final boolean visible = myPresentation.isVisible();
          if (!visible && SystemInfo.isMacSystemMenu && myPlace.equals(ActionPlaces.MAIN_MENU)) {
            setEnabled(false);
          }
          else {
            setVisible(visible);
          }
        }
        else if (Presentation.PROP_ENABLED.equals(name)) {
          setEnabled(myPresentation.isEnabled());
          updateIcon(myAction.getAction());
        }
        else if (Presentation.PROP_MNEMONIC_KEY.equals(name)) {
          setMnemonic(myPresentation.getMnemonic());
        }
        else if (Presentation.PROP_MNEMONIC_INDEX.equals(name)) {
          setDisplayedMnemonicIndex(myPresentation.getDisplayedMnemonicIndex());
        }
        else if (Presentation.PROP_TEXT.equals(name)) {
          setText(myPresentation.getText());
        }
        else if (Presentation.PROP_ICON.equals(name) || Presentation.PROP_DISABLED_ICON.equals(name) || SELECTED.equals(name)) {
          updateIcon(myAction.getAction());
        }
      }
      finally {
        mySynchronized.remove(name);
        if (queueForDispose) {
          // later since we cannot remove property listeners inside event processing
          //noinspection SSBasedInspection
          SwingUtilities.invokeLater(() -> {
            if (getParent() == null) {
              uninstallSynchronizer();
            }
          });
        }
      }
    }
  }
}
