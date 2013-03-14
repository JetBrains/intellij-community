/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.ScreenUtil;
import com.intellij.util.ui.Animator;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;

/**
 * Made non-final public for Fabrique.
 *
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public class IdeMenuBar extends JMenuBar {

  private static final int COLLAPSED_HEIGHT = 2;
  private IdeMenuBar.MyBorderDelegator myBorderDelegator;

  private enum State {
    EXPANDED, COLLAPSING, COLLAPSED, EXPANDING, TEMPORARY_EXPANDED;

    boolean isInProgress() {
      return this == COLLAPSING || this == EXPANDING;
    }
  }

  private final MyTimerListener myTimerListener;
  private ArrayList<AnAction> myVisibleActions;
  private ArrayList<AnAction> myNewVisibleActions;
  private final MenuItemPresentationFactory myPresentationFactory;
  private final DataManager myDataManager;
  private final ActionManagerEx myActionManager;
  private final Disposable myDisposable = Disposer.newDisposable();
  private boolean DISABLED = false;

  @Nullable private final Animator myAnimator;
  @Nullable private final Timer myActivationWatcher;
  @NotNull private State myState = State.EXPANDED;
  private double myProgress = 0;
  private boolean myMouseInside = false;
  private boolean myActivated = false;

  public IdeMenuBar(ActionManagerEx actionManager, DataManager dataManager) {
    myActionManager = actionManager;
    myTimerListener = new MyTimerListener();
    myVisibleActions = new ArrayList<AnAction>();
    myNewVisibleActions = new ArrayList<AnAction>();
    myPresentationFactory = new MenuItemPresentationFactory();
    myDataManager = dataManager;
    if (SystemInfo.isWindows) {
      myAnimator = new Animator("MenuBarAnimator", 16, 300, false) {
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
          repaint();
        }
      };
      Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
        @Override
        public void eventDispatched(AWTEvent event) {
          MouseEvent mouseEvent = (MouseEvent)event;
          Component component = findActualComponent(mouseEvent);

          if (myState != State.EXPANDED && !myState.isInProgress()) {
            myMouseInside = myActivated || isDescendingFrom(component, IdeMenuBar.this);
            if (myMouseInside && myState == State.COLLAPSED) {
              setState(State.EXPANDING);
              restartAnimator();
            }
            else if (!myMouseInside && myState != State.COLLAPSING && myState != State.COLLAPSED) {
              setState(State.COLLAPSING);
              restartAnimator();
            }
          }
        }

        private Component findActualComponent(MouseEvent mouseEvent) {
          Component component = mouseEvent.getComponent();
          Component deepestComponent;
          if (myState != State.EXPANDED &&
              !myState.isInProgress() &&
              contains(SwingUtilities.convertPoint(component, mouseEvent.getPoint(), IdeMenuBar.this))) {
            deepestComponent = IdeMenuBar.this;
          }
          else {
            deepestComponent = SwingUtilities.getDeepestComponentAt(mouseEvent.getComponent(), mouseEvent.getX(), mouseEvent.getY());
          }
          if (deepestComponent != null) {
            component = deepestComponent;
          }
          return component;
        }
      }, AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK);
      myActivationWatcher = new Timer(100, new ActionListener() {
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
      });
    }
    else {
      myAnimator = null;
      myActivationWatcher = null;
    }
  }

  @Override
  public void setBorder(Border border) {
    if (border == null || !SystemInfo.isWindows) {
      super.setBorder(border);
      myBorderDelegator = null;
      return;
    }

    if (myBorderDelegator == null) {
      myBorderDelegator = new MyBorderDelegator(border);
    }
    else {
      myBorderDelegator.setSource(border);
    }

    super.setBorder(myBorderDelegator);
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


  private void setState(@NotNull State state) {
    myState = state;
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

  /**
   * Invoked when enclosed frame is being shown.
   */
  @Override
  public void addNotify() {
    super.addNotify();
    updateMenuActions();
    Window window = SwingUtilities.getWindowAncestor(this);
    if (SystemInfo.isWindows && window instanceof IdeFrameImpl) {
      boolean fullScreen = WindowManagerEx.getInstanceEx().isFullScreen((IdeFrameImpl)window);
      if (fullScreen) {
        setState(State.COLLAPSING);
        restartAnimator();
      }
      else {
        if (myAnimator != null) {
          myAnimator.suspend();
        }
        setState(State.EXPANDED);
      }
    }
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
    if (myActivationWatcher != null) {
      myActivationWatcher.start();
    }
  }

  /**
   * Invoked when enclosed frame is being disposed.
   */
  @Override
  public void removeNotify() {
    if (ScreenUtil.isStandardAddRemoveNotify(this)) {
      if (myActivationWatcher != null) {
        myActivationWatcher.stop();
      }
      if (myAnimator != null) {
        myAnimator.suspend();
      }
      Disposer.dispose(myDisposable);
    }
    super.removeNotify();
  }

  void updateMenuActions() {
    myNewVisibleActions.clear();
    final DataContext dataContext = ((DataManagerImpl)myDataManager).getDataContextTest(this);

    if (!DISABLED) {
      expandActionGroup(dataContext, myNewVisibleActions, myActionManager);
    }

    if (!myNewVisibleActions.equals(myVisibleActions)) {
      // should rebuild UI
      final boolean changeBarVisibility = myNewVisibleActions.isEmpty() || myVisibleActions.isEmpty();

      final ArrayList<AnAction> temp = myVisibleActions;
      myVisibleActions = myNewVisibleActions;
      myNewVisibleActions = temp;

      removeAll();
      final boolean enableMnemonics = !UISettings.getInstance().DISABLE_MNEMONICS;
      for (final AnAction action : myVisibleActions) {
        add(new ActionMenu(null, ActionPlaces.MAIN_MENU, (ActionGroup)action, myPresentationFactory, enableMnemonics, true));
      }

      fixMenuBackground();
      updateMnemonicsVisibility();
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
    if (UIUtil.isUnderDarcula()) {
      g.setColor(UIManager.getColor("MenuItem.background"));
      g.fillRect(0, 0, getWidth(), getHeight());
    }
  }

  @Override
  protected void paintChildren(Graphics g) {
    if (SystemInfo.isWindows && myState == State.COLLAPSED) {
      return;
    }
    if (SystemInfo.isWindows && myState.isInProgress()) {
      Graphics2D g2 = (Graphics2D)g;
      AffineTransform oldTransform = g2.getTransform();
      AffineTransform newTransform = oldTransform != null ? new AffineTransform(oldTransform) : new AffineTransform();
      newTransform.concatenate(AffineTransform.getTranslateInstance(0, getHeight() - super.getPreferredSize().height));
      g2.setTransform(newTransform);
      super.paintChildren(g2);
      g2.setTransform(oldTransform);
    }
    else {
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

  private void expandActionGroup(final DataContext context, final ArrayList<AnAction> newVisibleActions, ActionManager actionManager) {
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

  private void updateMnemonicsVisibility() {
    final boolean enabled = !UISettings.getInstance().DISABLE_MNEMONICS;
    for (int i = 0; i < getMenuCount(); i++) {
      ((ActionMenu)getMenu(i)).setMnemonicEnabled(enabled);
    }
  }

  public void disableUpdates() {
    DISABLED = true;
    updateMenuActions();
  }

  public void enableUpdates() {
    DISABLED = false;
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

  private class MyBorderDelegator implements Border {
    @NotNull private Border mySource;

    private MyBorderDelegator(@NotNull Border source) {
      setSource(source);
    }

    void setSource(@NotNull Border source) {
      mySource = source;
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
      mySource.paintBorder(c, g, x, y, width, height);
    }

    @Override
    public Insets getBorderInsets(Component c) {
      Insets insets = mySource.getBorderInsets(c);
      if (myState != State.EXPANDED) {
        insets.top = 0;//get rid of "passive top pixel" in fullscreen mode
      }
      return insets;
    }

    @Override
    public boolean isBorderOpaque() {
      return mySource.isBorderOpaque();
    }
  }
}
