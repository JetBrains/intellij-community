/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.impl.DataManagerImpl;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.impl.ActionMenu;
import com.intellij.openapi.actionSystem.impl.MenuItemPresentationFactory;
import com.intellij.openapi.actionSystem.impl.WeakTimerListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.ex.IdeFrameEx;
import com.intellij.openapi.wm.impl.status.ClockPanel;
import com.intellij.ui.Gray;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.util.ui.Animator;
import com.intellij.util.ui.UIUtil;
import org.java.ayatana.ApplicationMenu;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public class IdeMenuBar extends JMenuBar implements IdeEventQueue.EventDispatcher {
  private static final int COLLAPSED_HEIGHT = 2;

  private enum State {
    EXPANDED, COLLAPSING, COLLAPSED, EXPANDING, TEMPORARY_EXPANDED;

    boolean isInProgress() {
      return this == COLLAPSING || this == EXPANDING;
    }
  }

  private final MyTimerListener myTimerListener;
  private List<AnAction> myVisibleActions;
  private List<AnAction> myNewVisibleActions;
  private final MenuItemPresentationFactory myPresentationFactory;
  private final DataManager myDataManager;
  private final ActionManagerEx myActionManager;
  private final Disposable myDisposable = Disposer.newDisposable();
  private boolean myDisabled = false;

  @Nullable private final ClockPanel myClockPanel;
  @Nullable private final Animator myAnimator;
  @Nullable private final Timer myActivationWatcher;
  @NotNull private State myState = State.EXPANDED;
  private double myProgress = 0;
  private boolean myActivated = false;

  public IdeMenuBar(ActionManagerEx actionManager, DataManager dataManager) {
    myActionManager = actionManager;
    myTimerListener = new MyTimerListener();
    myVisibleActions = new ArrayList<AnAction>();
    myNewVisibleActions = new ArrayList<AnAction>();
    myPresentationFactory = new MenuItemPresentationFactory();
    myDataManager = dataManager;

    if (WindowManagerImpl.isFloatingMenuBarSupported()) {
      myAnimator = new MyAnimator();
      myActivationWatcher = new Timer(100, new MyActionListener());
      myClockPanel = new ClockPanel();
      add(myClockPanel);
      addPropertyChangeListener(WindowManagerImpl.FULL_SCREEN, new PropertyChangeListener() {
        @Override public void propertyChange(PropertyChangeEvent evt) {
          updateState();
        }
      });
      addMouseListener(new MyMouseListener());
    }
    else {
      myAnimator = null;
      myActivationWatcher = null;
      myClockPanel = null;
    }
  }

  @Override
  public Border getBorder() {
    //avoid moving lines
    if (myState == State.EXPANDING || myState == State.COLLAPSING) {
      return new EmptyBorder(0,0,0,0);
    }

    //fix for Darcula double border
    if (myState == State.TEMPORARY_EXPANDED && UIUtil.isUnderDarcula()) {
      return new CustomLineBorder(Gray._75, 0, 0, 1, 0);
    }

    //save 1px for mouse handler
    if (myState == State.COLLAPSED) {
      return new EmptyBorder(0, 0, 1, 0);
    }

    return UISettings.getInstance().SHOW_MAIN_TOOLBAR || UISettings.getInstance().SHOW_NAVIGATION_BAR ? super.getBorder() : null;
  }

  @Override
  public void paint(Graphics g) {
    //otherwise, there will be 1px line on top
    if (myState == State.COLLAPSED) {
      return;
    }
    super.paint(g);
  }

  @Override
  public void doLayout() {
    super.doLayout();
    if (myClockPanel != null) {
      if (myState != State.EXPANDED) {
        myClockPanel.setVisible(true);
        Dimension preferredSize = myClockPanel.getPreferredSize();
        myClockPanel.setBounds(getBounds().width - preferredSize.width, 0, preferredSize.width, preferredSize.height);
      }
      else {
        myClockPanel.setVisible(false);
      }
    }
  }

  @Override
  public void menuSelectionChanged(boolean isIncluded) {
    if (!isIncluded && myState == State.TEMPORARY_EXPANDED) {
      myActivated = false;
      setState(State.COLLAPSING);
      restartAnimator();
      return;
    }
    if (isIncluded && myState == State.COLLAPSED) {
      myActivated = true;
      setState(State.TEMPORARY_EXPANDED);
      revalidate();
      repaint();
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          JMenu menu = getMenu(getSelectionModel().getSelectedIndex());
          if (menu.isPopupMenuVisible()) {
            menu.setPopupMenuVisible(false);
            menu.setPopupMenuVisible(true);
          }
        }
      });
    }
    super.menuSelectionChanged(isIncluded);
  }

  private static boolean isDescendingFrom(@Nullable Component a, @NotNull Component b) {
    while (a != null) {
      if (a == b) {
        return true;
      }

      if (a instanceof JPopupMenu) {
        a = ((JPopupMenu)a).getInvoker();
      }
      else {
        a = a.getParent();
      }
    }
    return false;
  }

  private boolean isActivated() {
    int index = getSelectionModel().getSelectedIndex();
    if (index == -1) {
      return false;
    }
    return getMenu(index).isPopupMenuVisible();
  }

  private void updateState() {
    if (myAnimator != null) {
      Window window = SwingUtilities.getWindowAncestor(this);
      if (window instanceof IdeFrameEx) {
        boolean fullScreen = ((IdeFrameEx)window).isInFullScreen();
        if (fullScreen) {
          setState(State.COLLAPSING);
          restartAnimator();
        }
        else {
          myAnimator.suspend();
          setState(State.EXPANDED);
          if (myClockPanel != null) {
            myClockPanel.setVisible(false);
          }
        }
      }
    }
  }

  private void setState(@NotNull State state) {
    myState = state;
    if (myState == State.EXPANDING && myActivationWatcher != null && !myActivationWatcher.isRunning()) {
      myActivationWatcher.start();
    } else if (myActivationWatcher != null && myActivationWatcher.isRunning()) {
      if (state == State.EXPANDED || state == State.COLLAPSED) {
        myActivationWatcher.stop();
      }
    }
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension dimension = super.getPreferredSize();
    if (myState.isInProgress()) {
      dimension.height =
        COLLAPSED_HEIGHT + (int)((myState == State.COLLAPSING ? (1 - myProgress) : myProgress) * (dimension.height - COLLAPSED_HEIGHT));
    }
    else if (myState == State.COLLAPSED) {
      dimension.height = COLLAPSED_HEIGHT;
    }
    return dimension;
  }

  private void restartAnimator() {
    if (myAnimator != null) {
      myAnimator.reset();
      myAnimator.resume();
    }
  }

  @Override
  public void addNotify() {
    super.addNotify();

    updateMenuActions();

    // Add updater for menus
    myActionManager.addTimerListener(1000, new WeakTimerListener(myActionManager, myTimerListener));
    UISettingsListener UISettingsListener = new UISettingsListener() {
      public void uiSettingsChanged(final UISettings source) {
        updateMnemonicsVisibility();
        myPresentationFactory.reset();
      }
    };
    UISettings.getInstance().addUISettingsListener(UISettingsListener, myDisposable);
    Disposer.register(ApplicationManager.getApplication(), myDisposable);
    IdeEventQueue.getInstance().addDispatcher(this, myDisposable);
  }

  @Override
  public void removeNotify() {
    if (ScreenUtil.isStandardAddRemoveNotify(this)) {
      if (myAnimator != null) {
        myAnimator.suspend();
      }
      Disposer.dispose(myDisposable);
    }
    super.removeNotify();
  }

  @Override
  public boolean dispatch(AWTEvent e) {
    if (e instanceof MouseEvent) {
      MouseEvent mouseEvent = (MouseEvent)e;
      Component component = findActualComponent(mouseEvent);

      if (myState != State.EXPANDED /*&& !myState.isInProgress()*/) {
        boolean mouseInside = myActivated || isDescendingFrom(component, this);
        if (e.getID() == MouseEvent.MOUSE_EXITED && e.getSource() == SwingUtilities.windowForComponent(this) && !myActivated) mouseInside = false;
        if (mouseInside && myState == State.COLLAPSED) {
          setState(State.EXPANDING);
          restartAnimator();
        }
        else if (!mouseInside && myState != State.COLLAPSING && myState != State.COLLAPSED) {
          setState(State.COLLAPSING);
          restartAnimator();
        }
      }
    }
    return false;
  }

  private Component findActualComponent(MouseEvent mouseEvent) {
    Component component = mouseEvent.getComponent();
    Component deepestComponent;
    if (myState != State.EXPANDED &&
        !myState.isInProgress() &&
        contains(SwingUtilities.convertPoint(component, mouseEvent.getPoint(), this))) {
      deepestComponent = this;
    }
    else {
      deepestComponent = SwingUtilities.getDeepestComponentAt(mouseEvent.getComponent(), mouseEvent.getX(), mouseEvent.getY());
    }
    if (deepestComponent != null) {
      component = deepestComponent;
    }
    return component;
  }

  void updateMenuActions() {
    myNewVisibleActions.clear();

    if (!myDisabled) {
      DataContext dataContext = ((DataManagerImpl)myDataManager).getDataContextTest(this);
      expandActionGroup(dataContext, myNewVisibleActions, myActionManager);
    }

    if (!myNewVisibleActions.equals(myVisibleActions)) {
      // should rebuild UI
      final boolean changeBarVisibility = myNewVisibleActions.isEmpty() || myVisibleActions.isEmpty();

      final List<AnAction> temp = myVisibleActions;
      myVisibleActions = myNewVisibleActions;
      myNewVisibleActions = temp;

      removeAll();
      final boolean enableMnemonics = !UISettings.getInstance().DISABLE_MNEMONICS;
      for (final AnAction action : myVisibleActions) {
        add(new ActionMenu(null, ActionPlaces.MAIN_MENU, (ActionGroup)action, myPresentationFactory, enableMnemonics, true));
      }

      fixMenuBackground();
      updateMnemonicsVisibility();
      if (myClockPanel != null) {
        add(myClockPanel);
      }
      validate();

      if (changeBarVisibility) {
        invalidate();
        final JFrame frame = (JFrame)SwingUtilities.getAncestorOfClass(JFrame.class, this);
        if (frame != null) {
          frame.validate();
        }
      }
    }
  }

  @Override
  public void updateUI() {
    super.updateUI();
    fixMenuBackground();
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    if (UIUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF()) {
      g.setColor(UIManager.getColor("MenuItem.background"));
      g.fillRect(0, 0, getWidth(), getHeight());
    }
  }

  @Override
  protected void paintChildren(Graphics g) {
    if (myState.isInProgress()) {
      Graphics2D g2 = (Graphics2D)g;
      AffineTransform oldTransform = g2.getTransform();
      AffineTransform newTransform = oldTransform != null ? new AffineTransform(oldTransform) : new AffineTransform();
      newTransform.concatenate(AffineTransform.getTranslateInstance(0, getHeight() - super.getPreferredSize().height));
      g2.setTransform(newTransform);
      super.paintChildren(g2);
      g2.setTransform(oldTransform);
    }
    else if (myState != State.COLLAPSED) {
      super.paintChildren(g);
    }
  }

  /**
   * Hacks a problem under Alloy LaF which draws menu bar in different background menu items are drawn in.
   */
  private void fixMenuBackground() {
    if (UIUtil.isUnderAlloyLookAndFeel() && getMenuCount() > 0) {
      final JMenu menu = getMenu(0);
      if (menu != null) {  // hack for Substance LAF compatibility
        menu.updateUI();
        setBackground(menu.getBackground());
      }
    }
  }

  private void expandActionGroup(final DataContext context, final List<AnAction> newVisibleActions, ActionManager actionManager) {
    final ActionGroup mainActionGroup = (ActionGroup)CustomActionsSchema.getInstance().getCorrectedAction(IdeActions.GROUP_MAIN_MENU);
    if (mainActionGroup == null) return;
    final AnAction[] children = mainActionGroup.getChildren(null);
    for (final AnAction action : children) {
      if (!(action instanceof ActionGroup)) {
        continue;
      }
      final Presentation presentation = myPresentationFactory.getPresentation(action);
      final AnActionEvent e = new AnActionEvent(null, context, ActionPlaces.MAIN_MENU, presentation, actionManager, 0);
      e.setInjectedContext(action.isInInjectedContext());
      action.update(e);
      if (presentation.isVisible()) { // add only visible items
        newVisibleActions.add(action);
      }
    }
  }

  @Override
  public int getMenuCount() {
    int menuCount = super.getMenuCount();
    return myClockPanel != null ? menuCount - 1: menuCount;
  }

  private void updateMnemonicsVisibility() {
    final boolean enabled = !UISettings.getInstance().DISABLE_MNEMONICS;
    for (int i = 0; i < getMenuCount(); i++) {
      ((ActionMenu)getMenu(i)).setMnemonicEnabled(enabled);
    }
  }

  public void disableUpdates() {
    myDisabled = true;
    updateMenuActions();
  }

  public void enableUpdates() {
    myDisabled = false;
    updateMenuActions();
  }

  private final class MyTimerListener implements TimerListener {
    @Override
    public ModalityState getModalityState() {
      return ModalityState.stateForComponent(IdeMenuBar.this);
    }

    @Override
    public void run() {
      if (!isShowing()) {
        return;
      }

      final Window myWindow = SwingUtilities.windowForComponent(IdeMenuBar.this);
      if (myWindow != null && !myWindow.isActive()) return;

      // do not update when a popup menu is shown (if popup menu contains action which is also in the menu bar, it should not be enabled/disabled)
      final MenuSelectionManager menuSelectionManager = MenuSelectionManager.defaultManager();
      final MenuElement[] selectedPath = menuSelectionManager.getSelectedPath();
      if (selectedPath.length > 0) {
        return;
      }

      // don't update toolbar if there is currently active modal dialog
      final Window window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
      if (window instanceof Dialog) {
        if (((Dialog)window).isModal()) {
          return;
        }
      }

      updateMenuActions();
      if (UIUtil.isWinLafOnVista()) {
        repaint();
      }
    }
  }

  private class MyAnimator extends Animator {
    public MyAnimator() {
      super("MenuBarAnimator", 16, 300, false);
    }

    @Override
    public void paintNow(int frame, int totalFrames, int cycle) {
      myProgress = (1 - Math.cos(Math.PI * ((float)frame / totalFrames))) / 2;
      revalidate();
      repaint();
    }

    @Override
    protected void paintCycleEnd() {
      myProgress = 1;
      switch (myState) {
        case COLLAPSING:
          setState(State.COLLAPSED);
          break;
        case EXPANDING:
          setState(State.TEMPORARY_EXPANDED);
          break;
        default:
      }
      revalidate();
      if (myState == State.COLLAPSED) {
        //we should repaint parent, to clear 1px on top when menu is collapsed
        getParent().repaint();
      } else {
        repaint();
      }
    }
  }

  private class MyActionListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      if (myState == State.EXPANDED || myState == State.EXPANDING) {
        return;
      }
      boolean activated = isActivated();
      if (myActivated && !activated && myState == State.TEMPORARY_EXPANDED) {
        myActivated = false;
        setState(State.COLLAPSING);
        restartAnimator();
      }
      if (activated) {
        myActivated = true;
      }
    }
  }

  private static class MyMouseListener extends MouseAdapter {
    @Override
    public void mousePressed(MouseEvent e) {
      Component c = e.getComponent();
      if (c instanceof IdeMenuBar) {
        Dimension size = c.getSize();
        Insets insets = ((IdeMenuBar)c).getInsets();
        Point p = e.getPoint();
        if (p.y < insets.top || p.y >= size.height - insets.bottom) {
          Component item = ((IdeMenuBar)c).findComponentAt(p.x, size.height / 2);
          if (item instanceof JMenuItem) {
            // re-target border clicks as a menu item ones
            item.dispatchEvent(
              new MouseEvent(item, e.getID(), e.getWhen(), e.getModifiers(), 1, 1, e.getClickCount(), e.isPopupTrigger(), e.getButton()));
            e.consume();
            return;
          }
        }
      }

      super.mouseClicked(e);
    }
  }

  public static void installAppMenuIfNeeded(@NotNull final JFrame frame) {
    if (SystemInfo.isLinux && Registry.is("linux.native.menu") && "Unity".equals(System.getenv("XDG_CURRENT_DESKTOP"))) {
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          ApplicationMenu.tryInstall(frame);
        }
      });
    }
  }
}
