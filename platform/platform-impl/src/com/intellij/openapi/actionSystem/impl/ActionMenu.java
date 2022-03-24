// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.actionholder.ActionRef;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBMenu;
import com.intellij.ui.mac.foundation.NSDefaults;
import com.intellij.ui.mac.screenmenu.Menu;
import com.intellij.ui.plaf.beg.IdeaMenuUI;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.SingleAlarm;
import com.intellij.util.concurrency.EdtScheduledExecutorService;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.JBSwingUtilities;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class ActionMenu extends JBMenu {
  /**
   * By default, a "performable" popup action group menu item provides a submenu.
   * Use this key to avoid suppress that submenu for a presentation or a template presentation like this:
   * {@code presentation.putClientProperty(ActionMenu.SUPPRESS_SUBMENU, true)}.
   */
  public static final Key<Boolean> SUPPRESS_SUBMENU = Key.create("SUPPRESS_SUBMENU");

  private final String myPlace;
  private final DataContext myContext;
  private final ActionRef<ActionGroup> myGroup;
  private final PresentationFactory myPresentationFactory;
  private final Presentation myPresentation;
  private boolean myMnemonicEnabled;
  private StubItem myStubItem;  // A PATCH!!! Do not remove this code, otherwise you will lose all keyboard navigation in JMenuBar.
  private final boolean myUseDarkIcons;
  private Disposable myDisposable;
  private final @Nullable Menu myScreenMenuPeer;
  private final @Nullable SubElementSelector mySubElementSelector;

  public ActionMenu(@Nullable DataContext context,
                    @NotNull String place,
                    @NotNull ActionGroup group,
                    @NotNull PresentationFactory presentationFactory,
                    boolean enableMnemonics,
                    boolean useDarkIcons) {
    myContext = context;
    myPlace = place;
    myGroup = ActionRef.fromAction(group);
    myPresentationFactory = presentationFactory;
    myPresentation = myPresentationFactory.getPresentation(group);
    myMnemonicEnabled = enableMnemonics;
    myUseDarkIcons = useDarkIcons;

    if (Menu.isJbScreenMenuEnabled() && ActionPlaces.MAIN_MENU.equals(myPlace)) {
      myScreenMenuPeer = new Menu(myPresentation.getText(enableMnemonics));
      myScreenMenuPeer.setOnOpen(() -> fillMenu(), this);
      myScreenMenuPeer.setOnClose(() -> setSelected(false), this);
      myScreenMenuPeer.listenPresentationChanges(myPresentation);
    }
    else {
      myScreenMenuPeer = null;
    }
    mySubElementSelector = SubElementSelector.isForceDisabled ? null : new SubElementSelector(this);

    updateUI();

    init();

    // Triggering initialization of private field "popupMenu" from JMenu with our own JBPopupMenu
    getPopupMenu();
  }

  @Override
  protected Graphics getComponentGraphics(Graphics graphics) {
    if (!(getParent() instanceof JMenuBar)) return super.getComponentGraphics(graphics);
    return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics));
  }

  public @NotNull AnAction getAnAction() { return myGroup.getAction(); }

  @Override
  public void removeNotify() {
    super.removeNotify();
    if (myDisposable != null) {
      Disposer.dispose(myDisposable);
      myDisposable = null;
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

  public @Nullable Menu getScreenMenuPeer() { return myScreenMenuPeer; }

  private void init() {
    boolean macSystemMenu = SystemInfo.isMacSystemMenu && isMainMenuPlace();

    myStubItem = macSystemMenu ? null : new StubItem();
    addStubItem();
    setBorderPainted(false);

    MenuListenerImpl menuListener = new MenuListenerImpl();
    addMenuListener(menuListener);
    getModel().addChangeListener(menuListener);

    updateFromPresentation(myMnemonicEnabled);
  }

  public boolean isMainMenuPlace() {
    return myPlace.equals(ActionPlaces.MAIN_MENU);
  }

  public void updateFromPresentation(boolean enableMnemonics) {
    myMnemonicEnabled = enableMnemonics;
    setVisible(myPresentation.isVisible());
    setEnabled(myPresentation.isEnabled());

    setText(myPresentation.getText(myMnemonicEnabled));
    setMnemonic(myPresentation.getMnemonic());
    setDisplayedMnemonicIndex(myPresentation.getDisplayedMnemonicIndex());
    updateIcon();
  }

  private void addStubItem() {
    if (myStubItem != null) {
      add(myStubItem);
    }
  }

  @Override
  public void setDisplayedMnemonicIndex(int index) throws IllegalArgumentException {
    super.setDisplayedMnemonicIndex(myMnemonicEnabled ? index : -1);
  }

  @Override
  public void setMnemonic(int mnemonic) {
    super.setMnemonic(myMnemonicEnabled ? mnemonic : 0);
  }

  private void updateIcon() {
    UISettings settings = UISettings.getInstanceOrNull();
    Icon icon = myPresentation.getIcon();
    if (icon != null && settings != null && settings.getShowIconsInMenus()) {
      if (SystemInfo.isMacSystemMenu && ActionPlaces.MAIN_MENU.equals(myPlace)) {
        // JDK can't paint correctly our HiDPI icons at the system menu bar
        icon = IconLoader.getMenuBarIcon(icon, myUseDarkIcons);
      } else if (shouldConvertIconToDarkVariant()) {
        icon = IconLoader.getDarkIcon(icon, true);
      }
      if (isShowNoIcons()) {
        setIcon(null);
        setDisabledIcon(null);
      }
      else {
        setIcon(icon);
        if (myPresentation.getDisabledIcon() != null) {
          setDisabledIcon(myPresentation.getDisabledIcon());
        }
        else {
          setDisabledIcon(icon == null ? null : IconLoader.getDisabledIcon(icon));
        }
        if (myScreenMenuPeer != null) myScreenMenuPeer.setIcon(icon);
      }
    }
  }

  static boolean shouldConvertIconToDarkVariant() {
    return JBColor.isBright() && ColorUtil.isDark(JBColor.namedColor("MenuItem.background", 0xffffff));
  }

  static boolean isShowNoIcons() {
    return SystemInfo.isMac && Registry.get("ide.macos.main.menu.alignment.options").isOptionEnabled("No icons");
  }

  static boolean isAligned() {
    return SystemInfo.isMac && Registry.get("ide.macos.main.menu.alignment.options").isOptionEnabled("Aligned");
  }

  static boolean isAlignedInGroup() {
    return SystemInfo.isMac && Registry.get("ide.macos.main.menu.alignment.options").isOptionEnabled("Aligned in group");
  }

  @Override
  public void menuSelectionChanged(boolean isIncluded) {
    super.menuSelectionChanged(isIncluded);
    showDescriptionInStatusBar(isIncluded, this, myPresentation.getDescription());
  }

  public static void showDescriptionInStatusBar(boolean isIncluded, Component component, @NlsContexts.StatusBarText String description) {
    IdeFrame frame = (IdeFrame)(component instanceof IdeFrame ? component : SwingUtilities.getAncestorOfClass(IdeFrame.class, component));
    StatusBar statusBar;
    if (frame != null && (statusBar = frame.getStatusBar()) != null) {
      statusBar.setInfo(isIncluded ? description : null);
    }
  }

  @Override
  protected void processMouseEvent(MouseEvent e) {
    boolean shouldCancelIgnoringOfNextSelectionRequest = false;

    if (mySubElementSelector != null) {
      switch (e.getID()) {
        case MouseEvent.MOUSE_PRESSED:
          mySubElementSelector.ignoreNextSelectionRequest();
          shouldCancelIgnoringOfNextSelectionRequest = true;
          break;
        case MouseEvent.MOUSE_ENTERED:
          mySubElementSelector.ignoreNextSelectionRequest(getDelay() * 2);
          break;
      }
    }

    try {
      super.processMouseEvent(e);
    }
    finally {
      if (shouldCancelIgnoringOfNextSelectionRequest) {
        mySubElementSelector.cancelIgnoringOfNextSelectionRequest();
      }
    }
  }

  private class MenuListenerImpl implements ChangeListener, MenuListener {
    ScheduledFuture<?> myDelayedClear;
    boolean isSelected = false;

    @Override
    public void stateChanged(ChangeEvent e) {
      // Re-implement javax.swing.JMenu.MenuChangeListener to avoid recursive event notifications
      // if 'menuSelected' fires unrelated 'stateChanged' event, without changing 'model.isSelected()' value.
      ButtonModel model = (ButtonModel)e.getSource();
      boolean modelSelected = model.isSelected();

      if (modelSelected != isSelected) {
        isSelected = modelSelected;

        if (modelSelected) {
          menuSelected();
        }
        else {
          menuDeselected();
        }
      }
    }

    @Override
    public void menuCanceled(MenuEvent e) {
      onMenuHidden();
    }

    @Override
    public void menuDeselected(MenuEvent e) {
      // Use ChangeListener instead to guard against recursive calls
    }

    @Override
    public void menuSelected(MenuEvent e) {
      // Use ChangeListener instead to guard against recursive calls
    }

    private void menuDeselected() {
      if (myDisposable != null) {
        Disposer.dispose(myDisposable);
        myDisposable = null;
      }
      onMenuHidden();

      if (mySubElementSelector != null) mySubElementSelector.cancelNextSelection();
    }

    private void onMenuHidden() {
      Runnable clearSelf = () -> {
        clearItems();
        addStubItem();
      };

      if (SystemInfo.isMacSystemMenu && isMainMenuPlace()) {
        // Menu items may contain mnemonic, and they can affect key-event dispatching (when Alt pressed)
        // To avoid influence of mnemonic it's necessary to clear items when menu was hidden.
        // When user selects item of system menu (under macOS) AppKit generates such sequence: CloseParentMenu -> PerformItemAction
        // So we can destroy menu-item before item's action performed, and because of that action will not be executed.
        // Defer clearing to avoid this problem.
        myDelayedClear = EdtScheduledExecutorService.getInstance().schedule(clearSelf, 1000, TimeUnit.MILLISECONDS);
      }
      else {
        clearSelf.run();
      }
    }

    private void menuSelected() {
      UsabilityHelper helper = new UsabilityHelper(ActionMenu.this);
      if (myDisposable == null) {
        myDisposable = Disposer.newDisposable();
      }
      Disposer.register(myDisposable, helper);
      if (myDelayedClear != null) {
        myDelayedClear.cancel(false);
        myDelayedClear = null;
        clearItems();
      }
      if (SystemInfo.isMacSystemMenu && ActionPlaces.MAIN_MENU.equals(myPlace)) {
        fillMenu();
      }
    }
  }

  @Override
  public void setPopupMenuVisible(boolean b) {
    if (b && !(SystemInfo.isMacSystemMenu && ActionPlaces.MAIN_MENU.equals(myPlace))) {
      fillMenu();
      if (!isSelected()) {
        return;
      }
    }

    super.setPopupMenuVisible(b);

    if (b && (mySubElementSelector != null)) {
      mySubElementSelector.selectSubElementIfNecessary();
    }
  }

  public void clearItems() {
    if (SystemInfo.isMacSystemMenu && isMainMenuPlace()) {
      for (Component menuComponent : getMenuComponents()) {
        if (menuComponent instanceof ActionMenu) {
          ((ActionMenu)menuComponent).clearItems();
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
      if (PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(context) == null) {
        IdeFrame frame = ComponentUtil.getParentOfType((Class<? extends IdeFrame>)IdeFrame.class, (Component)this);
        context = dataManager.getDataContext(IdeFocusManager.getGlobalInstance().getLastFocusedFor((Window)frame));
      }
      context = Utils.wrapDataContext(context);
    }

    final boolean isDarkMenu = SystemInfo.isMacSystemMenu && NSDefaults.isDarkMenuBar();
    Utils.fillMenu(myGroup.getAction(), this, myMnemonicEnabled, myPresentationFactory, context, myPlace, true, isDarkMenu,
                   RelativePoint.getNorthEastOf(this), () -> !isSelected());
  }

  private static final class UsabilityHelper implements IdeEventQueue.EventDispatcher, AWTEventListener, Disposable {
    private Component myComponent;
    private Point myStartMousePoint;
    private Point myUpperTargetPoint;
    private Point myLowerTargetPoint;
    private SingleAlarm myCallbackAlarm;
    private MouseEvent myEventToRedispatch;

    private UsabilityHelper(Component component) {
      myCallbackAlarm = new SingleAlarm(() -> {
        Disposer.dispose(myCallbackAlarm);
        myCallbackAlarm = null;
        if (myEventToRedispatch != null) {
          IdeEventQueue.getInstance().dispatchEvent(myEventToRedispatch);
        }
      }, 50, ModalityState.any(), this);
      myComponent = component;
      PointerInfo info = MouseInfo.getPointerInfo();
      myStartMousePoint = info != null ? info.getLocation() : null;
      if (myStartMousePoint != null) {
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
          if (myStartMousePoint.x < bounds.x) {
            myUpperTargetPoint = new Point(bounds.x, bounds.y);
            myLowerTargetPoint = new Point(bounds.x, bounds.y + bounds.height);
          }
          if (myStartMousePoint.x > bounds.x + bounds.width) {
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
        boolean isMouseMovingTowardsSubmenu = bounds.contains(point) || new Polygon(
          new int[]{myStartMousePoint.x, myUpperTargetPoint.x, myLowerTargetPoint.x},
          new int[]{myStartMousePoint.y, myUpperTargetPoint.y, myLowerTargetPoint.y},
          3).contains(point);

        myEventToRedispatch = (MouseEvent)e;

        if (!isMouseMovingTowardsSubmenu) {
          myCallbackAlarm.request();
        }
        else {
          myCallbackAlarm.cancel();
        }
        return true;
      }
      return false;
    }

    @Override
    public void dispose() {
      myComponent = null;
      myEventToRedispatch = null;
      myStartMousePoint = myUpperTargetPoint = myLowerTargetPoint = null;
      Toolkit.getDefaultToolkit().removeAWTEventListener(this);
    }
  }


  private static final class SubElementSelector {
    static final boolean isForceDisabled =
      SystemInfo.isMacSystemMenu ||
      !Registry.is("ide.popup.menu.navigation.keyboard.selectFirstEnabledSubItem", false);


    SubElementSelector(@NotNull ActionMenu owner) {
      if (isForceDisabled) {
        throw new IllegalStateException("Attempt to create an instance of ActionMenu.SubElementSelector class when it is force disabled");
      }

      myOwner = owner;
      myShouldIgnoreNextSelectionRequest = false;
      myShouldIgnoreNextSelectionRequestSinceTimestamp = -1;
      myShouldIgnoreNextSelectionRequestTimeoutMs = -1;
      myCurrentRequestId = -1;
    }

    @RequiresEdt
    void ignoreNextSelectionRequest(int timeoutMs) {
      myShouldIgnoreNextSelectionRequest = true;
      myShouldIgnoreNextSelectionRequestTimeoutMs = timeoutMs;
      if (timeoutMs >= 0) {
        myShouldIgnoreNextSelectionRequestSinceTimestamp = System.currentTimeMillis();
      }
      else {
        myShouldIgnoreNextSelectionRequestSinceTimestamp = -1;
      }
    }

    @RequiresEdt
    void ignoreNextSelectionRequest() {
      ignoreNextSelectionRequest(-1);
    }

    @RequiresEdt
    void cancelIgnoringOfNextSelectionRequest() {
      myShouldIgnoreNextSelectionRequest = false;
      myShouldIgnoreNextSelectionRequestSinceTimestamp = -1;
      myShouldIgnoreNextSelectionRequestTimeoutMs = -1;
    }

    @RequiresEdt
    void selectSubElementIfNecessary() {
      final boolean shouldIgnoreThisSelectionRequest;
      if (myShouldIgnoreNextSelectionRequest) {
        if (myShouldIgnoreNextSelectionRequestTimeoutMs >= 0) {
          shouldIgnoreThisSelectionRequest =
            ( (System.currentTimeMillis() - myShouldIgnoreNextSelectionRequestSinceTimestamp) <= myShouldIgnoreNextSelectionRequestTimeoutMs );
        }
        else {
          shouldIgnoreThisSelectionRequest = true;
        }
      }
      else {
        shouldIgnoreThisSelectionRequest = false;
      }

      cancelIgnoringOfNextSelectionRequest();

      if (shouldIgnoreThisSelectionRequest) {
        return;
      }

      final int thisRequestId = ++myCurrentRequestId;
      SwingUtilities.invokeLater(() -> selectFirstEnabledElement(thisRequestId));
    }

    @RequiresEdt
    void cancelNextSelection() {
      ++myCurrentRequestId;
    }


    private final @NotNull ActionMenu myOwner;
    private boolean myShouldIgnoreNextSelectionRequest;
    private long myShouldIgnoreNextSelectionRequestSinceTimestamp;
    private int myShouldIgnoreNextSelectionRequestTimeoutMs;
    private int myCurrentRequestId;


    @RequiresEdt
    private void selectFirstEnabledElement(final int requestId) {
      if (requestId != myCurrentRequestId) {
        // the request was cancelled or a newer request was created
        return;
      }

      if (!myOwner.isSelected()) {
        return;
      }

      final var menuSelectionManager = MenuSelectionManager.defaultManager();

      final var currentSelectedPath = menuSelectionManager.getSelectedPath();
      if (currentSelectedPath.length < 2) {
        return;
      }

      final var lastElementInCurrentPath = currentSelectedPath[currentSelectedPath.length - 1];

      final MenuElement[] newSelectionPath;
      if (lastElementInCurrentPath == myOwner.myStubItem) {
        newSelectionPath = currentSelectedPath.clone();
      }
      else if (lastElementInCurrentPath == myOwner.getPopupMenu()) {
        newSelectionPath = Arrays.copyOf(currentSelectedPath, currentSelectedPath.length + 1);
      }
      else if ( (currentSelectedPath[currentSelectedPath.length - 2] == myOwner.getPopupMenu()) &&
                !ArrayUtil.contains(lastElementInCurrentPath.getComponent(), myOwner.getMenuComponents()) ) {
        newSelectionPath = currentSelectedPath.clone();
      }
      else {
        return;
      }

      final var menuComponents = myOwner.getMenuComponents();
      for (final var component : menuComponents) {
        if ((component != myOwner.myStubItem) && component.isEnabled() && (component instanceof JMenuItem)) {
          newSelectionPath[newSelectionPath.length - 1] = (MenuElement)component;
          menuSelectionManager.setSelectedPath(newSelectionPath);

          return;
        }
      }
    }
  }
}