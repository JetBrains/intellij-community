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

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.actionholder.ActionRef;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.ui.plaf.beg.IdeaMenuUI;
import com.intellij.ui.plaf.gtk.GtkMenuUI;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.SingleAlarm;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.plaf.MenuItemUI;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public final class ActionMenu extends JMenu {
  private final String myPlace;
  private DataContext myContext;
  private final ActionRef<ActionGroup> myGroup;
  private final PresentationFactory myPresentationFactory;
  private final Presentation myPresentation;
  private boolean myMnemonicEnabled;
  private MenuItemSynchronizer myMenuItemSynchronizer;
  private StubItem myStubItem;  // A PATCH!!! Do not remove this code, otherwise you will lose all keyboard navigation in JMenuBar.
  private final boolean myTopLevel;
  private Disposable myDisposable;

  public ActionMenu(final DataContext context,
                    @NotNull final String place,
                    final ActionGroup group,
                    final PresentationFactory presentationFactory,
                    final boolean enableMnemonics,
                    final boolean topLevel) {
    myContext = context;
    myPlace = place;
    myGroup = ActionRef.fromAction(group);
    myPresentationFactory = presentationFactory;
    myPresentation = myPresentationFactory.getPresentation(group);
    myMnemonicEnabled = enableMnemonics;
    myTopLevel = topLevel;

    updateUI();

    init();

    // addNotify won't be called for menus in MacOS system menu
    if (SystemInfo.isMacSystemMenu) {
      installSynchronizer();
    }
    if (UIUtil.isUnderIntelliJLaF()) {
      setOpaque(true);
    }

    // Triggering initialization of private field "popupMenu" from JMenu with our own JBPopupMenu
    getPopupMenu();
  }

  public void updateContext(DataContext context) {
    myContext = context;
  }

  @Override
  public void addNotify() {
    super.addNotify();
    installSynchronizer();
  }

  private void installSynchronizer() {
    if (myMenuItemSynchronizer == null) {
      myMenuItemSynchronizer = new MenuItemSynchronizer();
      myGroup.getAction().addPropertyChangeListener(myMenuItemSynchronizer);
      myPresentation.addPropertyChangeListener(myMenuItemSynchronizer);
    }
  }

  @Override
  public void removeNotify() {
    uninstallSynchronizer();
    super.removeNotify();
    if (myDisposable != null) {
      Disposer.dispose(myDisposable);
      myDisposable = null;
    }
  }

  private void uninstallSynchronizer() {
    if (myMenuItemSynchronizer != null) {
      myGroup.getAction().removePropertyChangeListener(myMenuItemSynchronizer);
      myPresentation.removePropertyChangeListener(myMenuItemSynchronizer);
      myMenuItemSynchronizer = null;
    }
  }

  private JPopupMenu mySpecialMenu;
  @Override
  public JPopupMenu getPopupMenu() {
    if (mySpecialMenu == null) {
      mySpecialMenu = new JBPopupMenu();
      mySpecialMenu.setInvoker(this);
      popupListener = createWinListener(mySpecialMenu);
      ReflectionUtil.setField(JMenu.class, this, JPopupMenu.class, "popupMenu", mySpecialMenu);
    }
    return super.getPopupMenu();
  }

  @Override
  public void updateUI() {
    boolean isAmbiance = UIUtil.isUnderGTKLookAndFeel() && "Ambiance".equalsIgnoreCase(UIUtil.getGtkThemeName());
    if (myTopLevel && !isAmbiance && UIUtil.GTK_AMBIANCE_TEXT_COLOR.equals(getForeground())) {
      setForeground(null);
    }

    if (UIUtil.isStandardMenuLAF()) {
      super.updateUI();
    }
    else {
      setUI(IdeaMenuUI.createUI(this));
      setFont(UIUtil.getMenuFont());

      JPopupMenu popupMenu = getPopupMenu();
      if (popupMenu != null) {
        popupMenu.updateUI();
      }
    }

    if (myTopLevel && isAmbiance) {
      setForeground(UIUtil.GTK_AMBIANCE_TEXT_COLOR);
    }

    if (myTopLevel && UIUtil.isUnderGTKLookAndFeel()) {
      Insets insets = getInsets();
      Insets newInsets = new Insets(insets.top, insets.left, insets.bottom, insets.right);
      if (insets.top + insets.bottom < 6) {
        newInsets.top = newInsets.bottom = 3;
      }
      if (insets.left + insets.right < 12) {
        newInsets.left = newInsets.right = 6;
      }
      if (!newInsets.equals(insets)) {
        setBorder(BorderFactory.createEmptyBorder(newInsets.top, newInsets.left, newInsets.bottom, newInsets.right));
      }
    }
  }

  @Override
  public void setUI(final MenuItemUI ui) {
    final MenuItemUI newUi = !myTopLevel && UIUtil.isUnderGTKLookAndFeel() && GtkMenuUI.isUiAcceptable(ui) ? new GtkMenuUI(ui) : ui;
    super.setUI(newUi);
  }

  private void init() {
    boolean macSystemMenu = SystemInfo.isMacSystemMenu && myPlace.equals(ActionPlaces.MAIN_MENU);

    myStubItem = macSystemMenu ? null : new StubItem();
    addStubItem();
    addMenuListener(new MenuListenerImpl());
    setBorderPainted(false);

    setVisible(myPresentation.isVisible());
    setEnabled(myPresentation.isEnabled());
    setText(myPresentation.getText());
    updateIcon();

    setMnemonicEnabled(myMnemonicEnabled);
  }

  private void addStubItem() {
    if (myStubItem != null) {
      add(myStubItem);
    }
  }

  public void setMnemonicEnabled(boolean enable) {
    myMnemonicEnabled = enable;
    setMnemonic(myPresentation.getMnemonic());
    setDisplayedMnemonicIndex(myPresentation.getDisplayedMnemonicIndex());
  }

  @Override
  public void setDisplayedMnemonicIndex(final int index) throws IllegalArgumentException {
    super.setDisplayedMnemonicIndex(myMnemonicEnabled ? index : -1);
  }

  @Override
  public void setMnemonic(int mnemonic) {
    super.setMnemonic(myMnemonicEnabled ? mnemonic : 0);
  }

  private void updateIcon() {
    if (UISettings.getInstance().SHOW_ICONS_IN_MENUS) {
      final Presentation presentation = myPresentation;
      final Icon icon = presentation.getIcon();
      setIcon(icon);
      if (presentation.getDisabledIcon() != null) {
        setDisabledIcon(presentation.getDisabledIcon());
      }
      else {
        setDisabledIcon(IconLoader.getDisabledIcon(icon));
      }
    }
  }

  @Override
  public void menuSelectionChanged(boolean isIncluded) {
    super.menuSelectionChanged(isIncluded);
    showDescriptionInStatusBar(isIncluded, this, myPresentation.getDescription());
  }

  public static void showDescriptionInStatusBar(boolean isIncluded, Component component, String description) {
    IdeFrame frame = component instanceof IdeFrame
                     ? (IdeFrame)component
                     : (IdeFrame)SwingUtilities.getAncestorOfClass(IdeFrame.class, component);
    StatusBar statusBar;
    if (frame != null && (statusBar = frame.getStatusBar()) != null) {
      statusBar.setInfo(isIncluded ? description : null);
    }
  }

  private class MenuListenerImpl implements MenuListener {
    @Override
    public void menuCanceled(MenuEvent e) {
      clearItems();
      addStubItem();
    }

    @Override
    public void menuDeselected(MenuEvent e) {
      if (myDisposable != null) {
        Disposer.dispose(myDisposable);
        myDisposable = null;
      }
      clearItems();
      addStubItem();
    }

    @Override
    public void menuSelected(MenuEvent e) {
      UsabilityHelper helper = new UsabilityHelper(ActionMenu.this);
      if (myDisposable == null) {
        myDisposable = Disposer.newDisposable();
      }
      Disposer.register(myDisposable, helper);
      fillMenu();
    }
  }

  private void clearItems() {
    if (SystemInfo.isMacSystemMenu && myPlace.equals(ActionPlaces.MAIN_MENU)) {
      for (Component menuComponent : getMenuComponents()) {
        if (menuComponent instanceof ActionMenu) {
          ((ActionMenu)menuComponent).clearItems();
          if (SystemInfo.isMacSystemMenu) {
            // hideNotify is not called on Macs
            ((ActionMenu)menuComponent).uninstallSynchronizer();
          }
        }
        else if (menuComponent instanceof ActionMenuItem) {
          // Looks like an old-fashioned ugly workaround
          // JDK 1.7 on Mac works wrong with such functional keys
          if (!(SystemInfo.isJavaVersionAtLeast("1.7") && SystemInfo.isMac)) {
            ((ActionMenuItem)menuComponent).setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F24, 0));
          }
        }
      }
    }

    removeAll();
    validate();
  }

  private void fillMenu() {
    DataContext context;
    boolean mayContextBeInvalid;

    if (myContext != null) {
      context = myContext;
      mayContextBeInvalid = false;
    }
    else {
      @SuppressWarnings("deprecation") DataContext contextFromFocus = DataManager.getInstance().getDataContext();
      context = contextFromFocus;
      if (PlatformDataKeys.CONTEXT_COMPONENT.getData(context) == null) {
        IdeFrame frame = UIUtil.getParentOfType(IdeFrame.class, this);
        context = DataManager.getInstance().getDataContext(IdeFocusManager.getGlobalInstance().getLastFocusedFor(frame));
      }
      mayContextBeInvalid = true;
    }

    Utils.fillMenu(myGroup.getAction(), this, myMnemonicEnabled, myPresentationFactory, context, myPlace, true, mayContextBeInvalid);
  }

  private class MenuItemSynchronizer implements PropertyChangeListener {
    @Override
    public void propertyChange(PropertyChangeEvent e) {
      String name = e.getPropertyName();
      if (Presentation.PROP_VISIBLE.equals(name)) {
        setVisible(myPresentation.isVisible());
        if (SystemInfo.isMacSystemMenu && myPlace.equals(ActionPlaces.MAIN_MENU)) {
          validateTree();
        }
      }
      else if (Presentation.PROP_ENABLED.equals(name)) {
        setEnabled(myPresentation.isEnabled());
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
      else if (Presentation.PROP_ICON.equals(name) || Presentation.PROP_DISABLED_ICON.equals(name)) {
        updateIcon();
      }
    }
  }
  private static class UsabilityHelper implements IdeEventQueue.EventDispatcher, AWTEventListener, Disposable {

    private Component myComponent;
    private Point myLastMousePoint;
    private Point myUpperTargetPoint;
    private Point myLowerTargetPoint;
    private SingleAlarm myCallbackAlarm;
    private MouseEvent myEventToRedispatch;

    private UsabilityHelper(Component component) {
      myCallbackAlarm = new SingleAlarm(new Runnable() {
        @Override
        public void run() {
          Disposer.dispose(myCallbackAlarm);
          myCallbackAlarm = null;
          if (myEventToRedispatch != null) {
            IdeEventQueue.getInstance().dispatchEvent(myEventToRedispatch);
          }
        }
      }, 50, this);
      myComponent = component;
      PointerInfo info = MouseInfo.getPointerInfo();
      myLastMousePoint = info != null ? info.getLocation() : null;
      if (myLastMousePoint != null) {
        Toolkit.getDefaultToolkit().addAWTEventListener(this, AWTEvent.COMPONENT_EVENT_MASK);
        IdeEventQueue.getInstance().addDispatcher(this, this);
      }
    }

    @Override
    public void eventDispatched(AWTEvent event) {
      if (event instanceof ComponentEvent) {
        ComponentEvent componentEvent = (ComponentEvent)event;
        Component component = componentEvent.getComponent();
        JPopupMenu popup = UIUtil.findParentByClass(component, JPopupMenu.class);
        if (popup != null && popup.getInvoker() == myComponent) {
          Rectangle bounds = popup.getBounds();
          if (bounds.isEmpty()) return;
          bounds.setLocation(popup.getLocationOnScreen());
          if (myLastMousePoint.x < bounds.x) {
            myUpperTargetPoint = new Point(bounds.x, bounds.y);
            myLowerTargetPoint = new Point(bounds.x, bounds.y + bounds.height);
          }
          if (myLastMousePoint.x > bounds.x + bounds.width) {
            myUpperTargetPoint = new Point(bounds.x + bounds.width, bounds.y);
            myLowerTargetPoint = new Point(bounds.x + bounds.width, bounds.y + bounds.height);
          }
        }
      }
    }

    @Override
    public boolean dispatch(AWTEvent e) {
      if (e instanceof MouseEvent && myUpperTargetPoint != null && myLowerTargetPoint != null && myCallbackAlarm != null) {
        if (e.getID() == MouseEvent.MOUSE_PRESSED || e.getID() == MouseEvent.MOUSE_RELEASED || e.getID() == MouseEvent.MOUSE_CLICKED) {
          return false;
        }
        Point point = ((MouseEvent)e).getLocationOnScreen();

        myCallbackAlarm.cancel();
        boolean isMouseMovingTowardsSubmenu = new Polygon(
          new int[]{myLastMousePoint.x, myUpperTargetPoint.x, myLowerTargetPoint.x},
          new int[]{myLastMousePoint.y, myUpperTargetPoint.y, myLowerTargetPoint.y},
          3).contains(point);

        myEventToRedispatch = (MouseEvent)e;

        if (!isMouseMovingTowardsSubmenu) {
          myCallbackAlarm.request();
        }
        myLastMousePoint = point;
        return true;
      }
      return false;
    }

    @Override
    public void dispose() {
      myComponent = null;
      myEventToRedispatch = null;
      myLastMousePoint = myUpperTargetPoint = myLowerTargetPoint = null;
      Toolkit.getDefaultToolkit().removeAWTEventListener(this);
    }
  }
}
