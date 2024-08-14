// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.popup;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.WriteIntentReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.PopupBorder;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.UiInterceptors;
import com.intellij.ui.popup.list.ComboBoxPopup;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.ui.popup.tree.TreePopupImpl;
import com.intellij.ui.popup.util.MnemonicsSearch;
import com.intellij.ui.speedSearch.ElementFilter;
import com.intellij.ui.speedSearch.SpeedSearch;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.TimerUtil;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Collections;

public abstract class WizardPopup extends AbstractPopup implements ActionListener, ElementFilter {
  private static final Logger LOG = Logger.getInstance(WizardPopup.class);

  private static final Dimension MAX_SIZE = new Dimension(Integer.MAX_VALUE, 600);

  protected static final int STEP_X_PADDING = 2;

  private final WizardPopup myParent;
  private boolean alignByParentBounds = true;

  protected final PopupStep<Object> myStep;
  protected WizardPopup myChild;

  private  boolean myIsActiveRoot = true;

  private final Timer myAutoSelectionTimer =
    TimerUtil.createNamedTimer("Wizard auto-selection", Registry.intValue("ide.popup.auto.delay", 500), this);

  private final MnemonicsSearch myMnemonicsSearch;
  private Object myParentValue;

  private Point myLastOwnerPoint;
  private Window myOwnerWindow;
  private MyComponentAdapter myOwnerListener;

  private final ActionMap myActionMap = new ActionMap();
  private final InputMap myInputMap = new InputMap();

  private boolean myKeyPressedReceived;

  /**
   * @deprecated use {@link #WizardPopup(Project, JBPopup, PopupStep)}
   */
  @Deprecated(forRemoval = true)
  public WizardPopup(@NotNull PopupStep<Object> aStep) {
    this(CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext()), null, aStep);
  }

  public WizardPopup(@Nullable Project project, @Nullable JBPopup aParent, @NotNull PopupStep<Object> aStep) {
    myParent = (WizardPopup) aParent;
    myStep = aStep;

    mySpeedSearch.setEnabled(myStep.isSpeedSearchEnabled());

    final JComponent content = createContent();
    content.putClientProperty(KEY, this);
    JComponent popupComponent = createPopupComponent(content);

    init(project, popupComponent, getPreferredFocusableComponent(), true, true, true, null,
         isResizable(), aStep.getTitle(), null, true, Collections.emptySet(), false, null, null, null, false, null, true, false, true, null, 0f,
         null, true, false, new Component[0], null, SwingConstants.LEFT, true, Collections.emptyList(),
         null, null, false, true, true, null, true, null);

    registerAction("disposeAll", KeyEvent.VK_ESCAPE, InputEvent.SHIFT_MASK, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (mySpeedSearch.isHoldingFilter()) {
          mySpeedSearch.reset();
        }
        else {
          disposeAll();
        }
      }
    });

    AbstractAction goBackAction = new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        goBack();
      }
    };

    registerAction("goBack3", KeyEvent.VK_ESCAPE, 0, goBackAction);

    myMnemonicsSearch = new MnemonicsSearch(this) {
      @Override
      protected void select(Object value) {
        onSelectByMnemonic(value);
      }
    };

    initActionShortcutDelegates(aStep, popupComponent);

  }

  private void initActionShortcutDelegates(@NotNull PopupStep<?> step, @NotNull JComponent component) {
    var itemsSource = step.getMnemonicNavigationFilter();
    if (itemsSource == null) {
      return;
    }
    for (Object item : itemsSource.getValues()) {
      if (item instanceof ShortcutProvider itemShortcut) {
        var shortcut = itemShortcut.getShortcut();
        if (shortcut != null && shortcut.hasShortcuts()) {
          var action = new ActionShortcutDelegate(item, shortcut);
          action.registerCustomShortcutSet(component, this);
        }
      }
    }
  }

  protected @NotNull JComponent createPopupComponent(JComponent content) {
    JScrollPane scrollPane = createScrollPane(content);
    scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scrollPane.getHorizontalScrollBar().setBorder(null);

    scrollPane.getActionMap().get("unitScrollLeft").setEnabled(false);
    scrollPane.getActionMap().get("unitScrollRight").setEnabled(false);

    scrollPane.setBorder(JBUI.Borders.empty());
    return scrollPane;
  }

  protected @NotNull JScrollPane createScrollPane(JComponent content) {
    return ScrollPaneFactory.createScrollPane(content);
  }

  private void disposeAll() {
    WizardPopup root = PopupDispatcher.getActiveRoot();
    disposeAllParents(null);
    root.getStep().canceled();
  }

  public void goBack() {
    if (mySpeedSearch.isHoldingFilter()) {
      mySpeedSearch.reset();
      return;
    }

    if (myParent != null) {
      myParent.disposeChildren();
    }
    else {
      disposeAll();
    }
  }

  protected abstract JComponent createContent();

  @Override
  public void dispose() {
    myAutoSelectionTimer.stop();

    super.dispose();

    PopupDispatcher.unsetShowing(this);
    PopupDispatcher.clearRootIfNeeded(this);


    if (myOwnerWindow != null && myOwnerListener != null) {
      myOwnerWindow.removeComponentListener(myOwnerListener);
    }
  }


  public void disposeChildren() {
    if (myChild != null) {
      myChild.disposeChildren();
      Disposer.dispose(myChild);
      myChild = null;
    }
  }

  @Override
  public void show(final @NotNull Component owner, final int aScreenX, final int aScreenY, final boolean considerForcedXY) {
    if (UiInterceptors.tryIntercept(this)) return;

    LOG.assertTrue (!isDisposed());
    Dimension size = getContent().getPreferredSize();
    Dimension minimumSize = getMinimumSize();
    size.width = Math.max(size.width, minimumSize.width);
    size.height = Math.max(size.height, minimumSize.height);
    Rectangle targetBounds = new Rectangle(new Point(aScreenX, aScreenY), size);

    if (getParent() != null && alignByParentBounds) {
      final Rectangle parentBounds = getParent().getBounds();
      parentBounds.x += STEP_X_PADDING;
      parentBounds.width -= STEP_X_PADDING * 2;
      ScreenUtil.moveToFit(targetBounds, ScreenUtil.getScreenRectangle(
        parentBounds.x + parentBounds.width / 2,
        parentBounds.y + parentBounds.height / 2), null);
      if (parentBounds.intersects(targetBounds)) {
        targetBounds.x = getParent().getBounds().x - targetBounds.width - STEP_X_PADDING;
      }
    } else {
      ScreenUtil.moveToFit(targetBounds, ScreenUtil.getScreenRectangle(aScreenX + 1, aScreenY + 1), null);
    }

    if (getParent() == null && myIsActiveRoot) {
      PopupDispatcher.setActiveRoot(this);
    }
    else {
      PopupDispatcher.setShowing(this);
    }

    LOG.assertTrue (!isDisposed(), "Disposed popup, parent="+getParent());
    super.show(owner, targetBounds.x, targetBounds.y, true);
  }

  @Override
  protected void afterShow() {
    super.afterShow();
    registerAutoMove();
  }

  private void registerAutoMove() {
    if (myOwner != null) {
      myOwnerWindow = SwingUtilities.getWindowAncestor(myOwner);
      if (myOwnerWindow != null) {
        myLastOwnerPoint = myOwnerWindow.getLocationOnScreen();
        myOwnerListener = new MyComponentAdapter();
        myOwnerWindow.addComponentListener(myOwnerListener);
      }
    }
  }

  private void processParentWindowMoved() {
    if (isDisposed()) return;

    final Point newOwnerPoint = myOwnerWindow.getLocationOnScreen();

    int deltaX = myLastOwnerPoint.x - newOwnerPoint.x;
    int deltaY = myLastOwnerPoint.y - newOwnerPoint.y;

    myLastOwnerPoint = newOwnerPoint;

    final Window wnd = SwingUtilities.getWindowAncestor(getContent());
    if (!wnd.isShowing()) return;

    final Point current = wnd.getLocationOnScreen();

    setLocation(new Point(current.x - deltaX, current.y - deltaY));
  }

  protected abstract JComponent getPreferredFocusableComponent();

  @Override
  public void cancel(InputEvent e) {
    super.cancel(e);
    disposeChildren();
    Disposer.dispose(this);
    getStep().canceled();
  }

  @Override
  public boolean isCancelKeyEnabled() {
    return super.isCancelKeyEnabled() && !mySpeedSearch.isHoldingFilter();
  }

  protected void disposeAllParents(InputEvent e) {
    myDisposeEvent = e;
    Disposer.dispose(this);
    if (myParent != null) {
      myParent.disposeAllParents(null);
    }
  }

  public final void registerAction(@NonNls String aActionName, int aKeyCode, @JdkConstants.InputEventMask  int aModifier, Action aAction) {
    myInputMap.put(KeyStroke.getKeyStroke(aKeyCode, aModifier), aActionName);
    myActionMap.put(aActionName, aAction);
  }

  protected String getActionForKeyStroke(final KeyStroke keyStroke) {
    return (String) myInputMap.get(keyStroke);
  }

  public final void registerAction(@NonNls String aActionName, KeyStroke keyStroke, Action aAction) {
    myInputMap.put(keyStroke, aActionName);
    myActionMap.put(aActionName, aAction);
  }

  protected abstract InputMap getInputMap();

  protected abstract ActionMap getActionMap();

  protected final void setParentValue(Object parentValue) {
    myParentValue = parentValue;
  }

  @Override
  protected @NotNull MyContentPanel createContentPanel(final boolean resizable, final @NotNull PopupBorder border, final boolean isToDrawMacCorner) {
    return new MyContainer(border);
  }

  protected boolean isResizable() {
    return false;
  }

  private static final class MyContainer extends MyContentPanel {
    private MyContainer(@NotNull PopupBorder border) {
      super(border);
      setOpaque(true);
      setFocusCycleRoot(true);
    }

    @Override
    public Dimension getPreferredSize() {
      if (isPreferredSizeSet()) {
        return super.getPreferredSize();
      }
      final Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
      Point p = null;
      if (focusOwner != null && focusOwner.isShowing()) {
        p = focusOwner.getLocationOnScreen();
      }

      return computeNotBiggerDimension(super.getPreferredSize().getSize(), p);
    }

    private static Dimension computeNotBiggerDimension(Dimension ofContent, final Point locationOnScreen) {
      int resultHeight;
      if (locationOnScreen == null) {
        resultHeight = ofContent.height > MAX_SIZE.height + 50 ? MAX_SIZE.height : ofContent.height;
      }
      else {
        Rectangle r = ScreenUtil.getScreenRectangle(locationOnScreen);
        resultHeight = Math.min(ofContent.height, r.height - (r.height / 4));
      }

      int resultWidth = Math.min(ofContent.width, MAX_SIZE.width);

      if (ofContent.height > resultHeight) {
        resultWidth += ScrollPaneFactory.createScrollPane().getVerticalScrollBar().getPreferredSize().getWidth();
      }

      return new Dimension(resultWidth, resultHeight);
    }
  }

  public WizardPopup getParent() {
    return myParent;
  }

  public void setAlignByParentBounds(boolean alignByParentBounds) {
    this.alignByParentBounds = alignByParentBounds;
  }

  public boolean isAlignByParentBounds() {
    return alignByParentBounds;
  }

  public PopupStep getStep() {
    return myStep;
  }

  public final boolean dispatch(KeyEvent event) {
    if (anyModalWindowsAbovePopup()) {
      return false; // Popups should not process key events if there's a modal dialog on top of them.
    }
    if (event.getID() == KeyEvent.KEY_PRESSED) {
      myKeyPressedReceived = true;
      final KeyStroke stroke = KeyStroke.getKeyStroke(event.getKeyCode(), event.getModifiers(), false);
      if (proceedKeyEvent(event, stroke)) return true;
    }
    else if (!myKeyPressedReceived && !(this instanceof ComboBoxPopup)) {
      // key was pressed while this popup wasn't active, ignore the event
      return false;
    }

    if (event.getID() == KeyEvent.KEY_RELEASED) {
      final KeyStroke stroke = KeyStroke.getKeyStroke(event.getKeyCode(), event.getModifiers(), true);
      return proceedKeyEvent(event, stroke);
    }

    myMnemonicsSearch.processKeyEvent(event);
    processKeyEvent(event);

    if (event.isConsumed()) return true;
    process(event);
    return event.isConsumed();
  }

  protected void processKeyEvent(@NotNull KeyEvent e) {
    mySpeedSearch.processKeyEvent(e);
  }

  private boolean proceedKeyEvent(KeyEvent event, KeyStroke stroke) {
    if (myInputMap.get(stroke) != null) {
      final Action action = myActionMap.get(myInputMap.get(stroke));
      if (action != null && action.isEnabled()) {
        WriteIntentReadAction.run(
          (Runnable)() -> action.actionPerformed(new ActionEvent(getContent(), event.getID(), "", event.getWhen(), event.getModifiers()))
        );
        event.consume();
        return true;
      }
    }
    return false;
  }

  protected void process(KeyEvent aEvent) {

  }

  public Rectangle getBounds() {
    JComponent content = isDisposed() ? null : getContent();
    return content == null ? null : new Rectangle(content.getLocationOnScreen(), content.getSize());
  }

  protected WizardPopup createPopup(WizardPopup parent, PopupStep step, Object parentValue) {
    if (step instanceof ListPopupStep) {
      return new ListPopupImpl(getProject(), parent, (ListPopupStep)step, parentValue);
    }
    else if (step instanceof TreePopupStep) {
      return new TreePopupImpl(getProject(), parent, (TreePopupStep)step, parentValue);
    }
    else {
      throw new IllegalArgumentException(step.getClass().toString());
    }
  }

  @Override
  public final void actionPerformed(ActionEvent e) {
    myAutoSelectionTimer.stop();
    if (getStep().isAutoSelectionEnabled()) {
      onAutoSelectionTimer();
    }
  }

  protected final void restartTimer() {
    if (!myAutoSelectionTimer.isRunning()) {
      myAutoSelectionTimer.start();
    }
    else {
      myAutoSelectionTimer.restart();
    }
  }

  protected final void stopTimer() {
    myAutoSelectionTimer.stop();
  }

  protected void onAutoSelectionTimer() {

  }

  @Override
  public boolean shouldBeShowing(Object value) {
    if (!myStep.isSpeedSearchEnabled()) return true;
    SpeedSearchFilter<Object> filter = myStep.getSpeedSearchFilter();
    if (filter == null) return true;
    if (!filter.canBeHidden(value)) return true;
    if (!mySpeedSearch.isHoldingFilter()) return true;
    String text = filter.getIndexedString(value);
    return mySpeedSearch.shouldBeShowing(text);
  }

  public SpeedSearch getSpeedSearch() {
    return mySpeedSearch;
  }


  protected void onSelectByMnemonic(Object value) {

  }

  protected abstract void onChildSelectedFor(Object value);

  protected final void notifyParentOnChildSelection() {
    if (myParent == null || myParentValue == null) return;
    myParent.onChildSelectedFor(myParentValue);
  }


  private final class MyComponentAdapter extends ComponentAdapter {
    @Override
    public void componentMoved(final ComponentEvent e) {
      processParentWindowMoved();
    }
  }

  public void setActiveRoot(boolean activeRoot) {
    myIsActiveRoot = activeRoot;
  }

  @Override
  public final void setFinalRunnable(@Nullable Runnable runnable) {
    if (getParent() == null) {
      super.setFinalRunnable(runnable);
    } else {
      getParent().setFinalRunnable(runnable);
    }
  }

  @Override
  public void setOk(boolean ok) {
    if (getParent() == null) {
      super.setOk(ok);
    } else {
      getParent().setOk(ok);
    }
  }

  private class ActionShortcutDelegate extends DumbAwareAction {

    private final Object myItem;

    ActionShortcutDelegate(@NotNull Object item, @NotNull ShortcutSet shortcut) {
      myItem = item;
      setShortcutSet(shortcut);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      onSelectByMnemonic(myItem);
    }

    @SuppressWarnings("HardCodedStringLiteral") // used only for debugging here
    @Override
    public String toString() {
      return "ActionShortcutDelegate{" +
             "myItem=" + myItem +
             "} " + super.toString();
    }
  }
}
