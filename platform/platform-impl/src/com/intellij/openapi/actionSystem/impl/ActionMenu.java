// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.actionholder.ActionRef;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.components.JBMenu;
import com.intellij.ui.mac.foundation.NSDefaults;
import com.intellij.ui.plaf.beg.IdeaMenuUI;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.SingleAlarm;
import com.intellij.util.ui.JBSwingUtilities;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public final class ActionMenu extends JBMenu {
  private static final boolean KEEP_MENU_HIERARCHY = SystemInfo.isMacSystemMenu && Registry.is("keep.menu.hierarchy", false);
  private final String myPlace;
  private DataContext myContext;
  private final ActionRef<ActionGroup> myGroup;
  private final PresentationFactory myPresentationFactory;
  private final Presentation myPresentation;
  private boolean myMnemonicEnabled;
  private MenuItemSynchronizer myMenuItemSynchronizer;
  private StubItem myStubItem;  // A PATCH!!! Do not remove this code, otherwise you will lose all keyboard navigation in JMenuBar.
  private final boolean myUseDarkIcons;
  private Disposable myDisposable;

  public ActionMenu(final DataContext context,
                    @NotNull final String place,
                    final ActionGroup group,
                    final PresentationFactory presentationFactory,
                    final boolean enableMnemonics,
                    final boolean useDarkIcons
  ) {
    myContext = context;
    myPlace = place;
    myGroup = ActionRef.fromAction(group);
    myPresentationFactory = presentationFactory;
    myPresentation = myPresentationFactory.getPresentation(group);
    myMnemonicEnabled = enableMnemonics;
    myUseDarkIcons = useDarkIcons;

    updateUI();

    init();

    // addNotify won't be called for menus in MacOS system menu
    if (SystemInfo.isMacSystemMenu) {
      installSynchronizer();
    }

    // Triggering initialization of private field "popupMenu" from JMenu with our own JBPopupMenu
    getPopupMenu();
  }

  @Override
  protected Graphics getComponentGraphics(Graphics graphics) {
    if (!(getParent() instanceof JMenuBar)) return super.getComponentGraphics(graphics);
    return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics));
  }

  public void updateContext(DataContext context) {
    myContext = context;
  }

  public AnAction getAnAction() { return myGroup.getAction(); }

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
    setUI(IdeaMenuUI.createUI(this));
    setFont(UIUtil.getMenuFont());

    JPopupMenu popupMenu = getPopupMenu();
    if (popupMenu != null) {
      popupMenu.updateUI();
    }
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
    UISettings settings = UISettings.getInstanceOrNull();
    if (settings != null && settings.getShowIconsInMenus()) {
      final Presentation presentation = myPresentation;
      Icon icon = presentation.getIcon();
      if (SystemInfo.isMacSystemMenu && ActionPlaces.MAIN_MENU.equals(myPlace) && icon != null) {
        // JDK can't paint correctly our HiDPI icons at the system menu bar
        icon = IconLoader.getMenuBarIcon(icon, myUseDarkIcons);
      }
      setIcon(icon);
      if (presentation.getDisabledIcon() != null) {
        setDisabledIcon(presentation.getDisabledIcon());
      }
      else {
        setDisabledIcon(icon == null ? null : IconLoader.getDisabledIcon(icon));
      }
    }
  }

  @Override
  public void menuSelectionChanged(boolean isIncluded) {
    super.menuSelectionChanged(isIncluded);
    showDescriptionInStatusBar(isIncluded, this, myPresentation.getDescription());
  }

  public static void showDescriptionInStatusBar(boolean isIncluded, Component component, String description) {
    IdeFrame frame = (IdeFrame)(component instanceof IdeFrame ? component : SwingUtilities.getAncestorOfClass(IdeFrame.class, component));
    StatusBar statusBar;
    if (frame != null && (statusBar = frame.getStatusBar()) != null) {
      statusBar.setInfo(isIncluded ? description : null);
    }
  }

  private class MenuListenerImpl implements MenuListener {
    boolean myIsHidden = false;
    @Override
    public void menuCanceled(MenuEvent e) {
      onMenuHidden();
    }

    @Override
    public void menuDeselected(MenuEvent e) {
      if (myDisposable != null) {
        Disposer.dispose(myDisposable);
        myDisposable = null;
      }
      onMenuHidden();
    }

    private void onMenuHidden() {
      if (KEEP_MENU_HIERARCHY) {
        return;
      }

      Runnable clearSelf = () -> {
        clearItems();
        addStubItem();
      };

      if (SystemInfo.isMacSystemMenu && myPlace.equals(ActionPlaces.MAIN_MENU)) {
        // Menu items may contain mnemonic and they can affect key-event dispatching (when Alt pressed)
        // To avoid influence of mnemonic it's necessary to clear items when menu was hidden.
        // When user selects item of system menu (under MacOs) AppKit generates such sequence: CloseParentMenu -> PerformItemAction
        // So we can destroy menu-item before item's action performed, and because of that action will not be executed.
        // Defer clearing to avoid this problem.
        Disposable listenerHolder = Disposer.newDisposable();
        Disposer.register(ApplicationManager.getApplication(), listenerHolder);
        IdeEventQueue.getInstance().addDispatcher(e -> {
          if (e instanceof KeyEvent) {
            if (myIsHidden) {
              clearSelf.run();
            }
            ApplicationManager.getApplication().invokeLater(() -> Disposer.dispose(listenerHolder));
          }
          return false;
        }, listenerHolder);

        myIsHidden = true;
      }
      else {
        clearSelf.run();
      }
    }

    @Override
    public void menuSelected(MenuEvent e) {
      UsabilityHelper helper = new UsabilityHelper(ActionMenu.this);
      if (myDisposable == null) {
        myDisposable = Disposer.newDisposable();
      }
      Disposer.register(myDisposable, helper);
      if (KEEP_MENU_HIERARCHY || myIsHidden) {
        clearItems();
      }
      myIsHidden = false;
      fillMenu();
    }
  }

  public void clearItems() {
    if (SystemInfo.isMacSystemMenu && myPlace.equals(ActionPlaces.MAIN_MENU)) {
      for (Component menuComponent : getMenuComponents()) {
        if (menuComponent instanceof ActionMenu) {
          ((ActionMenu)menuComponent).clearItems();
          // hideNotify is not called on Macs
          ((ActionMenu)menuComponent).uninstallSynchronizer();
        }
        else if (menuComponent instanceof ActionMenuItem) {
          // Looks like an old-fashioned ugly workaround
          // JDK 1.7 on Mac works wrong with such functional keys
          if (!SystemInfo.isMac) {
            ((ActionMenuItem)menuComponent).setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F24, 0));
          }
        }
      }
    }

    removeAll();
    validate();
  }

  public void fillMenu() {
    DataContext context;

    if (myContext != null) {
      context = myContext;
    }
    else {
      DataManager dataManager = DataManager.getInstance();
      @SuppressWarnings("deprecation") DataContext contextFromFocus = dataManager.getDataContext();
      context = contextFromFocus;
      if (PlatformDataKeys.CONTEXT_COMPONENT.getData(context) == null) {
        IdeFrame frame = ComponentUtil.getParentOfType((Class<? extends IdeFrame>)IdeFrame.class, (Component)this);
        context = dataManager.getDataContext(IdeFocusManager.getGlobalInstance().getLastFocusedFor((Window)frame));
      }
    }

    final boolean isDarkMenu = SystemInfo.isMacSystemMenu && NSDefaults.isDarkMenuBar();
    Utils.fillMenu(myGroup.getAction(), this, myMnemonicEnabled, myPresentationFactory, context, myPlace, true, LaterInvocator.isInModalContext(), isDarkMenu);
  }

  private class MenuItemSynchronizer implements PropertyChangeListener {
    @Override
    public void propertyChange(PropertyChangeEvent e) {
      String name = e.getPropertyName();
      if (Presentation.PROP_VISIBLE.equals(name)) {
        setVisible(myPresentation.isVisible());
        if (SystemInfo.isMacSystemMenu && myPlace.equals(ActionPlaces.MAIN_MENU)) {
          validate();
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

    private long myLastEventTime = 0L;
    private boolean myInBounds = false;
    private SingleAlarm myCheckAlarm;

    private UsabilityHelper(Component component) {
      myCallbackAlarm = new SingleAlarm(() -> {
        Disposer.dispose(myCallbackAlarm);
        myCallbackAlarm = null;
        if (myEventToRedispatch != null) {
          IdeEventQueue.getInstance().dispatchEvent(myEventToRedispatch);
        }
      }, 50, ModalityState.any(), this);
      myCheckAlarm = new SingleAlarm(() -> {
        if (myLastEventTime > 0 && System.currentTimeMillis() - myLastEventTime > 1500) {
          if (!myInBounds && myCallbackAlarm != null && !myCallbackAlarm.isDisposed()) {
            myCallbackAlarm.request();
          }
        }
        myCheckAlarm.request();
      }, 100, ModalityState.any(), this);
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
        JPopupMenu popup = ComponentUtil.getParentOfType((Class<? extends JPopupMenu>)JPopupMenu.class, component);
        if (popup != null && popup.getInvoker() == myComponent && popup.isShowing()) {
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
    public boolean dispatch(@NotNull AWTEvent e) {
      if (e instanceof MouseEvent && myUpperTargetPoint != null && myLowerTargetPoint != null && myCallbackAlarm != null) {
        if (e.getID() == MouseEvent.MOUSE_PRESSED || e.getID() == MouseEvent.MOUSE_RELEASED || e.getID() == MouseEvent.MOUSE_CLICKED) {
          return false;
        }
        Point point = ((MouseEvent)e).getLocationOnScreen();
        Rectangle bounds = myComponent.getBounds();
        bounds.setLocation(myComponent.getLocationOnScreen());
        myInBounds = bounds.contains(point);
        boolean isMouseMovingTowardsSubmenu = myInBounds || new Polygon(
          new int[]{myLastMousePoint.x, myUpperTargetPoint.x, myLowerTargetPoint.x},
          new int[]{myLastMousePoint.y, myUpperTargetPoint.y, myLowerTargetPoint.y},
          3).contains(point);

        myEventToRedispatch = (MouseEvent)e;
        myLastEventTime = System.currentTimeMillis();

        if (!isMouseMovingTowardsSubmenu) {
          myCallbackAlarm.request();
        } else {
          myCallbackAlarm.cancel();
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