// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.ide.HelpTooltip;
import com.intellij.ide.actions.ActivateToolWindowAction;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.WindowInfo;
import com.intellij.ui.MouseDragHelper;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.RelativeFont;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;

import static com.intellij.openapi.wm.impl.ToolWindowDragHelperKt.createDragImage;

/**
 * @author Eugene Belyaev
 * @author Vladimir Kondratyev
 */
public class StripeButton extends AnchoredButton implements DataProvider {
  /**
   * This is analog of Swing mnemonic. We cannot use the standard ones
   * because it causes typing of "funny" characters into the editor.
   */
  private int myMnemonic;
  private boolean myPressedWhenSelected;

  private JLayeredPane myDragPane;
  @NotNull final ToolWindowsPane pane;
  final ToolWindowImpl toolWindow;
  private JLabel myDragButtonImage;
  private Point myPressedPoint;
  private AbstractDroppableStripe myLastStripe;
  private KeyEventDispatcher myDragKeyEventDispatcher;
  private boolean myDragCancelled = false;

  StripeButton(@NotNull ToolWindowsPane pane, @NotNull ToolWindowImpl toolWindow) {
    this.pane = pane;
    this.toolWindow = toolWindow;

    setFocusable(false);

    setBorder(JBUI.Borders.empty(5, 5, 0, 5));

    addActionListener(e -> {
      String id = toolWindow.getId();
      ToolWindowManagerImpl manager = toolWindow.getToolWindowManager();
      if (myPressedWhenSelected) {
        manager.hideToolWindow(id, false, true, ToolWindowEventSource.StripeButton);
      }
      else {
        manager.activated$intellij_platform_ide_impl(toolWindow, ToolWindowEventSource.StripeButton);
      }

      myPressedWhenSelected = false;
    });
    addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(final Component component, final int x, final int y) {
        showPopup(component, x, y);
      }
    });
    setRolloverEnabled(true);
    setOpaque(false);

    enableEvents(AWTEvent.MOUSE_EVENT_MASK);
    addMouseMotionListener(new MouseMotionAdapter() {
      @Override
      public void mouseDragged(MouseEvent e) {
        if (!Registry.is("ide.new.tool.window.dnd")) processDrag(e);
      }
    });

    updateHelpTooltip();
  }

  private void updateHelpTooltip() {
    HelpTooltip.dispose(this);

    HelpTooltip tooltip = new HelpTooltip();
    tooltip.setTitle(toolWindow.getStripeTitle());
    String activateActionId = ActivateToolWindowAction.getActionIdForToolWindow(toolWindow.getId());
    tooltip.setShortcut(ActionManager.getInstance().getKeyboardShortcut(activateActionId));
    tooltip.installOn(this);
  }

  public @NotNull WindowInfo getWindowInfo() {
    return toolWindow.getWindowInfo();
  }

  @NotNull
  String getId() {
    return toolWindow.getId();
  }

  @Override
  public @Nullable Object getData(@NotNull String dataId) {
    if (PlatformDataKeys.TOOL_WINDOW.is(dataId)) {
      return toolWindow;
    }
    else if (CommonDataKeys.PROJECT.is(dataId)) {
      return toolWindow.getToolWindowManager().getProject();
    }
    return null;
  }

  /**
   * We are using the trick here: the method does all things that super method does
   * excepting firing of the MNEMONIC_CHANGED_PROPERTY event. After that mnemonic
   * doesn't work via standard Swing rules (processing of Alt keystrokes).
   */
  @Override
  public void setMnemonic(int mnemonic) {
    throw new UnsupportedOperationException("use setMnemonic2(int)");
  }

  private void setMnemonic2(int mnemonic) {
    myMnemonic = mnemonic;
    updateHelpTooltip();
    revalidate();
    repaint();
  }

  @Override
  public int getMnemonic2() {
    return myMnemonic;
  }

  @Override
  public ToolWindowAnchor getAnchor() {
    return toolWindow.getAnchor();
  }

  public boolean isFirst() {
    return is(true);
  }

  public boolean isLast() {
    return is(false);
  }

  private boolean is(boolean first) {
    Container parent = getParent();
    if (parent == null) return false;

    int max = first ? Integer.MAX_VALUE : 0;
    ToolWindowAnchor anchor = getAnchor();
    Component c = null;
    int count = parent.getComponentCount();
    for (int i = 0; i < count; i++) {
      Component component = parent.getComponent(i);
      if (!component.isVisible()) continue;
      Rectangle r = component.getBounds();
      if (anchor == ToolWindowAnchor.LEFT || anchor == ToolWindowAnchor.RIGHT) {
        if (first && max > r.y || !first && max < r.y) {
          max = r.y;
          c = component;
        }
      } else {
        if (first && max > r.x || !first && max < r.x) {
          max = r.x;
          c = component;
        }
      }
    }
    return c == this;
  }

  private void processDrag(final MouseEvent e) {
    if (myDragCancelled || !MouseDragHelper.checkModifiers(e)) {
      return;
    }

    if (!isDraggingNow()) {
      if (myPressedPoint == null || isWithinDeadZone(e)) {
        return;
      }

      myDragPane = findLayeredPane(e);
      if (myDragPane == null) {
        return;
      }

      BufferedImage image = createDragImage(this);
      if(image == null) return;

      myDragButtonImage = new JLabel(IconUtil.createImageIcon((Image)image)) {
        @Override
        public String toString() {
          return "Image for: " + StripeButton.this;
        }
      };

      myDragButtonImage.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseReleased(MouseEvent e) {
          finishDragging();
          myPressedPoint = null;
          myDragButtonImage = null;
          super.mouseReleased(e);
        }
      });
      myDragPane.add(myDragButtonImage, JLayeredPane.POPUP_LAYER);
      myDragButtonImage.setSize(myDragButtonImage.getPreferredSize());
      setVisible(false);
      pane.startDrag();
      myDragKeyEventDispatcher = new DragKeyEventDispatcher();
      KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(myDragKeyEventDispatcher);
    }

    if (!isDraggingNow()) {
      return;
    }

    Point xy = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), myDragPane);
    if (myPressedPoint != null) {
      xy.x -= myPressedPoint.x;
      xy.y -= myPressedPoint.y;
    }
    myDragButtonImage.setLocation(xy);

    SwingUtilities.convertPointToScreen(xy, myDragPane);

    var stripe = pane.getStripeFor(xy, (Stripe)getParent());
    if (stripe == null) {
      if (myLastStripe != null) {
        myLastStripe.resetDrop();
      }
    }
    else {
      if (myLastStripe != null && myLastStripe != stripe) {
        myLastStripe.resetDrop();
      }
      stripe.processDropButton(this, myDragButtonImage, xy);
    }

    myLastStripe = stripe;
  }

  public @NotNull ToolWindowImpl getToolWindow() {
    return toolWindow;
  }

  private final class DragKeyEventDispatcher implements KeyEventDispatcher {
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
      if (isDraggingNow() && e.getKeyCode() == KeyEvent.VK_ESCAPE && e.getID() == KeyEvent.KEY_PRESSED) {
        myDragCancelled = true;
        finishDragging();
        return true;
      }
      return false;
    }
  }

  private boolean isWithinDeadZone(final MouseEvent e) {
    return myPressedPoint.distance(e.getPoint()) < JBUI.scale(MouseDragHelper.DRAG_START_DEADZONE);
  }

  private static @Nullable JLayeredPane findLayeredPane(MouseEvent e) {
    if (!(e.getComponent() instanceof JComponent)) {
      return null;
    }
    JRootPane root = ((JComponent)e.getComponent()).getRootPane();
    return root.getLayeredPane();
  }

  @Override
  protected void processMouseEvent(@NotNull MouseEvent e) {
    if (e.isPopupTrigger() && e.getComponent().isShowing()) {
      super.processMouseEvent(e);
      return;
    }

    if (UIUtil.isCloseClick(e)) {
      toolWindow.getToolWindowManager().hideToolWindow(toolWindow.getId(), true);
      return;
    }

    if (e.getButton() == MouseEvent.BUTTON1) {
      if (MouseEvent.MOUSE_PRESSED == e.getID()) {
        myPressedPoint = e.getPoint();
        myPressedWhenSelected = isSelected();
        myDragCancelled = false;
      }
      else if (MouseEvent.MOUSE_RELEASED == e.getID()) {
        finishDragging();
        myPressedPoint = null;
        myDragButtonImage = null;
      }
    }

    super.processMouseEvent(e);
  }

  void apply(@NotNull WindowInfo info) {
    setSelected(info.isVisible());
    updateState(toolWindow);
  }

  private void showPopup(@Nullable Component component, int x, int y) {
    ActionGroup group = toolWindow.createPopupGroup();
    ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.TOOLWINDOW_POPUP, group);
    popupMenu.getComponent().show(component, x, y);
  }

  @Override
  public void updateUI() {
    setUI(StripeButtonUI.createUI(this));

    Font font = StartupUiUtil.getLabelFont();
    RelativeFont relativeFont = RelativeFont.NORMAL.fromResource("StripeButton.fontSizeOffset", -2, JBUIScale.scale(11f));
    setFont(relativeFont.derive(font));
  }

  void updatePresentation() {
    updateState(toolWindow);
    updateText(toolWindow);
    updateIcon(toolWindow.getIcon());
  }

  void updateIcon(@Nullable Icon icon) {
    setIcon(icon);
    setDisabledIcon(icon == null ? null : IconLoader.getDisabledIcon(icon));
  }

  private void updateText(@NotNull ToolWindowImpl toolWindow) {
    String text = toolWindow.getStripeTitle();
    if (UISettings.getInstance().getShowToolWindowsNumbers()) {
      int mnemonic = ActivateToolWindowAction.getMnemonicForToolWindow(toolWindow.getId());
      if (mnemonic != -1) {
        text = (char)mnemonic + ": " + text;
        setMnemonic2(mnemonic);
      }
      else {
        setMnemonic2(0);
      }
    }
    setText(text);
    updateHelpTooltip();
  }

  private void updateState(@NotNull ToolWindowImpl toolWindow) {
    boolean toShow = toolWindow.isAvailable() || toolWindow.isPlaceholderMode();
    setVisible(toShow && (toolWindow.isShowStripeButton() || isSelected()));
    setEnabled(toolWindow.isAvailable());
  }

  private boolean isDraggingNow() {
    return myDragButtonImage != null;
  }

  private void finishDragging() {
    if (!isDraggingNow()) {
      return;
    }
    myDragPane.remove(myDragButtonImage);
    myDragButtonImage = null;
    pane.stopDrag();
    myDragPane.repaint();
    setVisible(true);
    if (myLastStripe != null) {
      myLastStripe.finishDrop(toolWindow.getToolWindowManager());
      myLastStripe = null;
    }
    if (myDragKeyEventDispatcher != null) {
      KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(myDragKeyEventDispatcher);
      myDragKeyEventDispatcher = null;
    }
  }

  @Override
  public String toString() {
    return StringUtil.getShortName(getClass().getName()) + " text: " + getText();
  }
}
