/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ui.popup;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.PopupBorder;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.ui.popup.tree.TreePopupImpl;
import com.intellij.ui.popup.util.MnemonicsSearch;
import com.intellij.ui.speedSearch.ElementFilter;
import com.intellij.ui.speedSearch.SpeedSearch;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Collections;

public abstract class WizardPopup extends AbstractPopup implements ActionListener, ElementFilter {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.popup.WizardPopup");

  private static final Dimension MAX_SIZE = new Dimension(Integer.MAX_VALUE, 600);

  protected static final int STEP_X_PADDING = 2;

  private final WizardPopup myParent;

  protected final PopupStep<Object> myStep;
  protected WizardPopup myChild;

  private final Timer myAutoSelectionTimer = UIUtil.createNamedTimer(
    "Wizard auto-selection", Registry.intValue("ide.popup.auto.delay", 500), this);

  private final MnemonicsSearch myMnemonicsSearch;
  private Object myParentValue;

  private Point myLastOwnerPoint;
  private Window myOwnerWindow;
  private MyComponentAdapter myOwnerListener;

  private final ActionMap myActionMap = new ActionMap();
  private final InputMap myInputMap = new InputMap();

  public WizardPopup(@NotNull PopupStep<Object> aStep) {
    this(null, aStep);
  }

  public WizardPopup(@Nullable JBPopup aParent, @NotNull PopupStep<Object> aStep) {
    myParent = (WizardPopup) aParent;
    myStep = aStep;

    mySpeedSearch.setEnabled(myStep.isSpeedSearchEnabled());

    final JComponent content = createContent();

    JScrollPane scrollPane = createScrollPane(content);
    scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scrollPane.getHorizontalScrollBar().setBorder(null);

    scrollPane.getActionMap().get("unitScrollLeft").setEnabled(false);
    scrollPane.getActionMap().get("unitScrollRight").setEnabled(false);

    scrollPane.setBorder(null);

    final Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext());
    init(project, scrollPane, getPreferredFocusableComponent(), true, true, true, null,
         isResizable(), aStep.getTitle(), null, true, null, false, null, null, null, false, null, true, false, true, null, 0f,
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



  }

  @NotNull
  protected JScrollPane createScrollPane(JComponent content) {
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
    super.dispose();

    myAutoSelectionTimer.stop();

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
  public void show(final Component owner, final int aScreenX, final int aScreenY, final boolean considerForcedXY) {
    LOG.assertTrue (!isDisposed());

    Rectangle targetBounds = new Rectangle(new Point(aScreenX, aScreenY), getContent().getPreferredSize());

    if (getParent() != null) {
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

    if (getParent() == null) {
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

    if (!myFocusTrackback.isMustBeShown()) {
      cancel();
    }
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
  @NotNull
  protected MyContentPanel createContentPanel(final boolean resizable, final PopupBorder border, final boolean isToDrawMacCorner) {
    return new MyContainer(resizable, border, isToDrawMacCorner);
  }

  protected boolean isResizable() {
    return false;
  }

  private class MyContainer extends MyContentPanel {
    private MyContainer(final boolean resizable, final PopupBorder border, final boolean drawMacCorner) {
      super(resizable, border, drawMacCorner);
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

    private Dimension computeNotBiggerDimension(Dimension ofContent, final Point locationOnScreen) {
      int resultHeight = ofContent.height > MAX_SIZE.height + 50 ? MAX_SIZE.height : ofContent.height;
      if (locationOnScreen != null) {
        final Rectangle r = ScreenUtil.getScreenRectangle(locationOnScreen);
        resultHeight = ofContent.height > r.height - (r.height / 4) ? r.height - (r.height / 4) : ofContent.height;
      }

      int resultWidth = ofContent.width > MAX_SIZE.width ? MAX_SIZE.width : ofContent.width;

      if (ofContent.height > MAX_SIZE.height) {
        resultWidth += ScrollPaneFactory.createScrollPane().getVerticalScrollBar().getPreferredSize().getWidth();
      }

      return new Dimension(resultWidth, resultHeight);
    }
  }

  public WizardPopup getParent() {
    return myParent;
  }

  public PopupStep getStep() {
    return myStep;
  }

  public final boolean dispatch(KeyEvent event) {
    if (event.getID() != KeyEvent.KEY_PRESSED && event.getID() != KeyEvent.KEY_RELEASED) {
      // do not dispatch these events to Swing
      event.consume();
      return true;
    }

    if (event.getID() == KeyEvent.KEY_PRESSED) {
      final KeyStroke stroke = KeyStroke.getKeyStroke(event.getKeyCode(), event.getModifiers(), false);
      if (proceedKeyEvent(event, stroke)) return false;
    }

    if (event.getID() == KeyEvent.KEY_RELEASED) {
      final KeyStroke stroke = KeyStroke.getKeyStroke(event.getKeyCode(), event.getModifiers(), true);
      return proceedKeyEvent(event, stroke);
    }

    myMnemonicsSearch.process(event);
    mySpeedSearch.process(event);

    if (event.isConsumed()) return true;
    process(event);
    return event.isConsumed();
  }

  private boolean proceedKeyEvent(KeyEvent event, KeyStroke stroke) {
    if (myInputMap.get(stroke) != null) {
      final Action action = myActionMap.get(myInputMap.get(stroke));
      if (action != null && action.isEnabled()) {
        action.actionPerformed(new ActionEvent(getContent(), event.getID(), "", event.getWhen(), event.getModifiers()));
        return true;
      }
    }
    return false;
  }

  protected void process(KeyEvent aEvent) {

  }

  public Rectangle getBounds() {
    return new Rectangle(getContent().getLocationOnScreen(), getContent().getSize());
  }

  protected WizardPopup createPopup(WizardPopup parent, PopupStep step, Object parentValue) {
    if (step instanceof ListPopupStep) {
      return new ListPopupImpl(parent, (ListPopupStep)step, parentValue);
    }
    else if (step instanceof TreePopupStep) {
      return new TreePopupImpl(parent, (TreePopupStep)step, parentValue);
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
    if (!filter.canBeHidden(value)) return true;
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


  private class MyComponentAdapter extends ComponentAdapter {
    @Override
    public void componentMoved(final ComponentEvent e) {
      processParentWindowMoved();
    }
  }

  @Override
  public final void setFinalRunnable(Runnable runnable) {
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
}
