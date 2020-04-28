// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.impl.DataManagerImpl;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionMenu;
import com.intellij.openapi.actionSystem.impl.MenuItemPresentationFactory;
import com.intellij.openapi.actionSystem.impl.WeakTimerListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.impl.status.ClockPanel;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.Gray;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.mac.foundation.NSDefaults;
import com.intellij.util.concurrency.NonUrgentExecutor;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Timer;
import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public class IdeMenuBar extends JMenuBar implements IdeEventQueue.EventDispatcher, UISettingsListener {
  private static final Logger LOG = Logger.getInstance(IdeMenuBar.class);
  private static final int COLLAPSED_HEIGHT = 2;

  private enum State {
    EXPANDED, COLLAPSING, COLLAPSED, EXPANDING, TEMPORARY_EXPANDED;

    boolean isInProgress() {
      return this == COLLAPSING || this == EXPANDING;
    }
  }

  private List<AnAction> myVisibleActions = new ArrayList<>();
  private List<AnAction> myNewVisibleActions = new ArrayList<>();
  private final MenuItemPresentationFactory myPresentationFactory = new MenuItemPresentationFactory();
  protected final Disposable myDisposable = Disposer.newDisposable();

  @Nullable private final ClockPanel myClockPanel;
  @Nullable private final MyExitFullScreenButton myButton;
  @Nullable private final Animator myAnimator;
  @Nullable private final Timer myActivationWatcher;
  @NotNull private State myState = State.EXPANDED;
  private double myProgress;
  private boolean myActivated;

  @NotNull
  public static IdeMenuBar createMenuBar() {
    return SystemInfo.isLinux ? new LinuxIdeMenuBar() : new IdeMenuBar();
  }

  protected IdeMenuBar() {
    if (FrameInfoHelper.isFloatingMenuBarSupported()) {
      myAnimator = new MyAnimator();
      myActivationWatcher = TimerUtil.createNamedTimer("IdeMenuBar", 100, new MyActionListener());
      myClockPanel = new ClockPanel();
      myButton = new MyExitFullScreenButton();
      add(myClockPanel);
      add(myButton);
      addPropertyChangeListener(IdeFrameDecorator.FULL_SCREEN, event -> updateState());
      addMouseListener(new MyMouseListener());
    }
    else {
      myAnimator = null;
      myActivationWatcher = null;
      myClockPanel = null;
      myButton = null;
    }
  }

  @Override
  protected Graphics getComponentGraphics(Graphics graphics) {
    return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics));
  }

  @NotNull
  public State getState() {
    // JMenuBar calls getBorder on init before our own init (super is called before our constructor).
    //noinspection ConstantConditions
    return myState == null ? State.EXPANDING : myState;
  }

  @Override
  public JMenu add(JMenu menu) {
    menu.setFocusable(false);
    return super.add(menu);
  }

  @Override
  public Border getBorder() {
    State state = getState();
    // avoid moving lines
    if (state == State.EXPANDING || state == State.COLLAPSING) {
      return JBUI.Borders.empty();
    }

    // fix for Darcula double border
    if (state == State.TEMPORARY_EXPANDED && StartupUiUtil.isUnderDarcula()) {
      return JBUI.Borders.customLine(Gray._75, 0, 0, 1, 0);
    }

    // save 1px for mouse handler
    if (state == State.COLLAPSED) {
      return JBUI.Borders.emptyBottom(1);
    }

    return UISettings.getInstance().getShowMainToolbar() || UISettings.getInstance().getShowNavigationBar() ? super.getBorder() : null;
  }

  @Override
  public void paint(Graphics g) {
    // otherwise, there will be 1px line on top
    if (getState() == State.COLLAPSED) {
      return;
    }
    super.paint(g);
  }

  @Override
  public void doLayout() {
    super.doLayout();

    if (myClockPanel == null || myButton == null) {
      return;
    }

    if (getState() == State.EXPANDED) {
      myClockPanel.setVisible(false);
      myButton.setVisible(false);
    }
    else {
      myClockPanel.setVisible(true);
      myButton.setVisible(true);
      Dimension preferredSize = myButton.getPreferredSize();
      myButton.setBounds(getBounds().width - preferredSize.width, 0, preferredSize.width, preferredSize.height);
      preferredSize = myClockPanel.getPreferredSize();
      myClockPanel.setBounds(getBounds().width - preferredSize.width - myButton.getWidth(), 0, preferredSize.width, preferredSize.height);
    }
  }

  @Override
  public void menuSelectionChanged(boolean isIncluded) {
    if (!isIncluded && getState() == State.TEMPORARY_EXPANDED) {
      myActivated = false;
      setState(State.COLLAPSING);
      restartAnimator();
      return;
    }

    if (isIncluded && getState() == State.COLLAPSED) {
      myActivated = true;
      setState(State.TEMPORARY_EXPANDED);
      revalidate();
      repaint();
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(() -> {
        JMenu menu = getMenu(getSelectionModel().getSelectedIndex());
        if (menu.isPopupMenuVisible()) {
          menu.setPopupMenuVisible(false);
          menu.setPopupMenuVisible(true);
        }
      });
    }

    super.menuSelectionChanged(isIncluded);
  }

  private boolean isActivated() {
    int index = getSelectionModel().getSelectedIndex();
    return index != -1 && getMenu(index).isPopupMenuVisible();
  }

  private void updateState() {
    if (myAnimator == null) {
      return;
    }

    Window window = SwingUtilities.getWindowAncestor(this);
    if (!(window instanceof IdeFrame)) {
      return;
    }

    boolean fullScreen = ((IdeFrame)window).isInFullScreen();
    if (fullScreen) {
      setState(State.COLLAPSING);
      restartAnimator();
    }
    else {
      myAnimator.suspend();
      setState(State.EXPANDED);
      if (myClockPanel != null) {
        myClockPanel.setVisible(false);
        if (myButton != null) {
          myButton.setVisible(false);
        }
      }
    }
  }

  private void setState(@NotNull State state) {
    myState = state;
    if (myState == State.EXPANDING && myActivationWatcher != null && !myActivationWatcher.isRunning()) {
      myActivationWatcher.start();
    }
    else if (myActivationWatcher != null && myActivationWatcher.isRunning()) {
      if (state == State.EXPANDED || state == State.COLLAPSED) {
        myActivationWatcher.stop();
      }
    }
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension dimension = super.getPreferredSize();
    if (getState().isInProgress()) {
      dimension.height = COLLAPSED_HEIGHT + (int)((getState() == State.COLLAPSING ? 1 - myProgress : myProgress) * (dimension.height - COLLAPSED_HEIGHT));
    }
    else if (getState() == State.COLLAPSED) {
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

    // add updater for menus
    doWithLazyActionManager(actionManager -> {
      doUpdateMenuActions(false, actionManager);
      actionManager.addTimerListener(1000, new WeakTimerListener(new MyTimerListener()));
    });

    Disposer.register(ApplicationManager.getApplication(), myDisposable);
    IdeEventQueue.getInstance().addDispatcher(this, myDisposable);
  }

  private static void doWithLazyActionManager(@NotNull Consumer<ActionManager> whatToDo) {
    ActionManager created = ApplicationManager.getApplication().getServiceIfCreated(ActionManager.class);
    if (created == null) {
      NonUrgentExecutor.getInstance().execute(() -> {
        ActionManager actionManager = ActionManager.getInstance();
        ApplicationManager.getApplication().invokeLater(() -> whatToDo.accept(actionManager), ModalityState.any());
      });
    }
    else {
      whatToDo.accept(created);
    }
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
  public void uiSettingsChanged(@NotNull UISettings uiSettings) {
    updateMnemonicsVisibility();
    myPresentationFactory.reset();
  }

  @Override
  public boolean dispatch(@NotNull AWTEvent e) {
    if (e instanceof MouseEvent && getState() != State.EXPANDED /*&& !myState.isInProgress()*/) {
      considerRestartingAnimator((MouseEvent)e);
    }
    return false;
  }

  private void considerRestartingAnimator(MouseEvent mouseEvent) {
    boolean mouseInside = myActivated || UIUtil.isDescendingFrom(findActualComponent(mouseEvent), this);
    if (mouseEvent.getID() == MouseEvent.MOUSE_EXITED && mouseEvent.getSource() == SwingUtilities.windowForComponent(this) && !myActivated) mouseInside = false;
    if (mouseInside && getState() == State.COLLAPSED) {
      setState(State.EXPANDING);
      restartAnimator();
    }
    else if (!mouseInside && getState() != State.COLLAPSING && getState() != State.COLLAPSED) {
      setState(State.COLLAPSING);
      restartAnimator();
    }
  }

  @Nullable
  private Component findActualComponent(MouseEvent mouseEvent) {
    Component component = mouseEvent.getComponent();
    if (component == null) {
      return null;
    }
    Component deepestComponent;
    if (getState() != State.EXPANDED &&
        !getState().isInProgress() &&
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
    updateMenuActions(false);
  }

  void updateMenuActions(boolean forceRebuild) {
    doUpdateMenuActions(forceRebuild, ActionManager.getInstance());
  }

  private void doUpdateMenuActions(boolean forceRebuild, @NotNull ActionManager manager) {
    myNewVisibleActions.clear();

    DataContext dataContext = ((DataManagerImpl)DataManager.getInstance()).getDataContextTest(this);
    expandActionGroup(dataContext, myNewVisibleActions, manager);

    if (!forceRebuild && myNewVisibleActions.equals(myVisibleActions)) {
      return;
    }

    // should rebuild UI
    boolean changeBarVisibility = myNewVisibleActions.isEmpty() || myVisibleActions.isEmpty();

    List<AnAction> temp = myVisibleActions;
    myVisibleActions = myNewVisibleActions;
    myNewVisibleActions = temp;

    removeAll();
    boolean enableMnemonics = !UISettings.getInstance().getDisableMnemonics();
    boolean isDarkMenu = isDarkMenu();
    for (AnAction action : myVisibleActions) {
      add(createActionMenu(enableMnemonics, isDarkMenu, (ActionGroup)action));
    }

    updateGlobalMenuRoots();
    updateMnemonicsVisibility();
    if (myClockPanel != null) {
      add(myClockPanel);
      add(myButton);
    }
    validate();

    if (changeBarVisibility) {
      invalidate();
      JFrame frame = (JFrame)SwingUtilities.getAncestorOfClass(JFrame.class, this);
      if (frame != null) {
        frame.validate();
      }
    }
  }

  protected boolean isDarkMenu() {
    return SystemInfo.isMacSystemMenu && NSDefaults.isDarkMenuBar();
  }

  @NotNull
  protected ActionMenu createActionMenu(boolean enableMnemonics, boolean isDarkMenu, ActionGroup action) {
    return new ActionMenu(null, ActionPlaces.MAIN_MENU, action, myPresentationFactory, enableMnemonics, isDarkMenu);
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    paintBackground(g);
  }

  protected void paintBackground(Graphics g) {
    if (StartupUiUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF()) {
      g.setColor(UIManager.getColor("MenuItem.background"));
      g.fillRect(0, 0, getWidth(), getHeight());
    }
  }

  @Override
  protected void paintChildren(Graphics g) {
    if (getState().isInProgress()) {
      Graphics2D g2 = (Graphics2D)g;
      AffineTransform oldTransform = g2.getTransform();
      AffineTransform newTransform = oldTransform != null ? new AffineTransform(oldTransform) : new AffineTransform();
      newTransform.concatenate(AffineTransform.getTranslateInstance(0, getHeight() - super.getPreferredSize().height));
      g2.setTransform(newTransform);
      super.paintChildren(g2);
      g2.setTransform(oldTransform);
    }
    else if (getState() != State.COLLAPSED) {
      super.paintChildren(g);
    }
  }

  private void expandActionGroup(final DataContext context, final List<? super AnAction> newVisibleActions, ActionManager actionManager) {
    final ActionGroup mainActionGroup = getMainMenuActionGroup();
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

  @Nullable
  public ActionGroup getMainMenuActionGroup() {
    return (ActionGroup)CustomActionsSchema.getInstance().getCorrectedAction(IdeActions.GROUP_MAIN_MENU);
  }

  @Override
  public int getMenuCount() {
    int menuCount = super.getMenuCount();
    return myClockPanel != null ? menuCount - 2: menuCount;
  }

  private void updateMnemonicsVisibility() {
    final boolean enabled = !UISettings.getInstance().getDisableMnemonics();
    for (int i = 0; i < getMenuCount(); i++) {
      JMenu menu = getMenu(i);
      if (menu instanceof ActionMenu) {
        ((ActionMenu)menu).setMnemonicEnabled(enabled);
      }
    }
  }

  protected void updateGlobalMenuRoots() {
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

      Window myWindow = SwingUtilities.windowForComponent(IdeMenuBar.this);
      if (myWindow != null && !myWindow.isActive()) {
        return;
      }

      // do not update when a popup menu is shown (if popup menu contains action which is also in the menu bar, it should not be enabled/disabled)
      MenuSelectionManager menuSelectionManager = MenuSelectionManager.defaultManager();
      MenuElement[] selectedPath = menuSelectionManager.getSelectedPath();
      if (selectedPath.length > 0) {
        return;
      }

      // don't update toolbar if there is currently active modal dialog
      Window window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
      if (window instanceof Dialog && ((Dialog)window).isModal()) {
        return;
      }

      updateMenuActions();
    }
  }

  private final class MyAnimator extends Animator {
    MyAnimator() {
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
      switch (getState()) {
        case COLLAPSING:
          setState(State.COLLAPSED);
          break;
        case EXPANDING:
          setState(State.TEMPORARY_EXPANDED);
          break;
        default:
      }
      if (!isShowing()) {
        return;
      }
      revalidate();
      if (getState() == State.COLLAPSED) {
        //we should repaint parent, to clear 1px on top when menu is collapsed
        getParent().repaint();
      }
      else {
        repaint();
      }
    }
  }

  private class MyActionListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      if (getState() == State.EXPANDED || getState() == State.EXPANDING) {
        return;
      }
      boolean activated = isActivated();
      if (myActivated && !activated && getState() == State.TEMPORARY_EXPANDED) {
        myActivated = false;
        setState(State.COLLAPSING);
        restartAnimator();
      }
      if (activated) {
        myActivated = true;
      }
    }
  }

  private static final class MyMouseListener extends MouseAdapter {
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
            item.dispatchEvent(MouseEventAdapter.convert(e, item, 1, 1));
            e.consume();
          }
        }
      }
    }
  }

  public static void installAppMenuIfNeeded(@NotNull JFrame frame) {
    JMenuBar menuBar = frame.getJMenuBar();
    // must be called when frame is visible (otherwise frame.getPeer() == null)
    if (menuBar instanceof IdeMenuBar) {
      try {
        ((IdeMenuBar)menuBar).doInstallAppMenuIfNeeded(frame);
      }
      catch (Throwable e) {
        LOG.warn("cannot install app menu", e);
      }
    }
    else if (menuBar != null) {
      LOG.info("The menu bar '" + menuBar + " of frame '" + frame + "' isn't instance of IdeMenuBar");
    }
  }

  protected void doInstallAppMenuIfNeeded(@NotNull JFrame frame) {
  }

  public void onToggleFullScreen(boolean isFullScreen) {
  }

  private static final class MyExitFullScreenButton extends JButton {
    private MyExitFullScreenButton() {
      setFocusable(false);
      addActionListener(e -> {
        ProjectFrameHelper frameHelper = ProjectFrameHelper.getFrameHelper(SwingUtilities.getWindowAncestor(this));
        if (frameHelper != null) {
          frameHelper.toggleFullScreen(false);
        }
      });
      addMouseListener(new MouseAdapter() {
        @Override
        public void mouseEntered(MouseEvent e) {
          model.setRollover(true);
        }

        @Override
        public void mouseExited(MouseEvent e) {
          model.setRollover(false);
        }
      });
    }
    @Override
    public Dimension getPreferredSize() {
      int height;
      Container parent = getParent();
      if (isVisible() && parent != null) {
        height = parent.getSize().height - parent.getInsets().top - parent.getInsets().bottom;
      }
      else {
        height = super.getPreferredSize().height;
      }
      //noinspection SuspiciousNameCombination
      return new Dimension(height, height);
    }

    @Override
    public Dimension getMaximumSize() {
      return getPreferredSize();
    }

    @Override
    public void paint(Graphics g) {
      Graphics2D g2d = (Graphics2D)g.create();
      try {
        g2d.setColor(UIManager.getColor("Label.background"));
        g2d.fillRect(0, 0, getWidth()+1, getHeight()+1);
        g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        double s = (double)getHeight() / 13;
        g2d.translate(s, s);
        Shape plate = new RoundRectangle2D.Double(0, 0, s * 11, s * 11, s, s);
        Color color = UIManager.getColor("Label.foreground");
        boolean hover = model.isRollover() || model.isPressed();
        g2d.setColor(ColorUtil.withAlpha(color, hover? .25: .18));
        g2d.fill(plate);
        g2d.setColor(ColorUtil.withAlpha(color, hover? .4 : .33));
        g2d.draw(plate);
        g2d.setColor(ColorUtil.withAlpha(color, hover ? .7 : .66));
        GeneralPath path = new GeneralPath();
        path.moveTo(s * 2, s * 6);
        path.lineTo(s * 5, s * 6);
        path.lineTo(s * 5, s * 9);
        path.lineTo(s * 4, s * 8);
        path.lineTo(s * 2, s * 10);
        //noinspection DuplicateExpressions
        path.quadTo(s * 2 - s/ Math.sqrt(2), s * 9 + s/Math.sqrt(2), s, s * 9);
        path.lineTo(s * 3, s * 7);
        path.lineTo(s * 2, s * 6);
        path.closePath();
        g2d.fill(path);
        g2d.draw(path);
        path = new GeneralPath();
        path.moveTo(s * 6, s * 2);
        path.lineTo(s * 6, s * 5);
        path.lineTo(s * 9, s * 5);
        path.lineTo(s * 8, s * 4);
        path.lineTo(s * 10, s * 2);
        //noinspection DuplicateExpressions
        path.quadTo(s * 9 + s/ Math.sqrt(2), s * 2 - s/Math.sqrt(2), s * 9 , s);
        path.lineTo(s * 7, s * 3);
        path.lineTo(s * 6, s * 2);
        path.closePath();
        g2d.fill(path);
        g2d.draw(path);
      }
      finally {
        g2d.dispose();
      }
    }
  }
}
