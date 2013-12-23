/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.ide.actions.ResizeToolWindowAction;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManagerListener;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.UIBundle;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.content.Content;
import com.intellij.util.Producer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.event.EventListenerList;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Map;

/**
 * @author Eugene Belyaev
 * @author Vladimir Kondratyev
 */
public final class InternalDecorator extends JPanel implements Queryable, TypeSafeDataProvider {

  private static final int DIVIDER_WIDTH = UIUtil.isUnderDarcula() ? 2 : 5;

  private Project myProject;
  private WindowInfoImpl myInfo;
  private final ToolWindowImpl myToolWindow;
  private final MyDivider myDivider;
  private final EventListenerList myListenerList;
  /*
   * Actions
   */
  private final TogglePinnedModeAction myToggleAutoHideModeAction;
  private final ToggleDockModeAction myToggleDockModeAction;
  private final ToggleFloatingModeAction myToggleFloatingModeAction;
  private final ToggleSideModeAction myToggleSideModeAction;
  private final ToggleContentUiTypeAction myToggleContentUiTypeAction;

  private ActionGroup myAdditionalGearActions;
  /**
   * Catches all event from tool window and modifies decorator's appearance.
   */
  private final ToolWindowHandler myToolWindowHandler;
  private final MyKeymapManagerListener myWeakKeymapManagerListener;
  @NonNls private static final String HIDE_ACTIVE_WINDOW_ACTION_ID = "HideActiveWindow";
  @NonNls public static final String TOGGLE_PINNED_MODE_ACTION_ID = "TogglePinnedMode";
  @NonNls public static final String TOGGLE_DOCK_MODE_ACTION_ID = "ToggleDockMode";
  @NonNls public static final String TOGGLE_FLOATING_MODE_ACTION_ID = "ToggleFloatingMode";
  @NonNls public static final String TOGGLE_SIDE_MODE_ACTION_ID = "ToggleSideMode";
  @NonNls private static final String TOGGLE_CONTENT_UI_TYPE_ACTION_ID = "ToggleContentUiTypeMode";

  private ToolWindowHeader myHeader;

  InternalDecorator(final Project project, final WindowInfoImpl info, final ToolWindowImpl toolWindow) {
    super(new BorderLayout());
    myProject = project;
    myToolWindow = toolWindow;
    myToolWindow.setDecorator(this);
    myDivider = new MyDivider();

    myToggleFloatingModeAction = new ToggleFloatingModeAction();
    myToggleSideModeAction = new ToggleSideModeAction();
    myToggleDockModeAction = new ToggleDockModeAction();
    myToggleAutoHideModeAction = new TogglePinnedModeAction();
    myToggleContentUiTypeAction = new ToggleContentUiTypeAction();

    myListenerList = new EventListenerList();

    myHeader = new ToolWindowHeader(toolWindow, info, new Producer<ActionGroup>() {
      @Override
      public ActionGroup produce() {
        return createGearPopupGroup();
      }
    }) {
      @Override
      protected boolean isActive() {
        return isFocused();
      }

      @Override
      protected void hideToolWindow() {
        fireHidden();
      }

      @Override
      protected void toolWindowTypeChanged(ToolWindowType type) {
        fireTypeChanged(type);
      }

      @Override
      protected void sideHidden() {
        fireHiddenSide();
      }
    };

    MyKeymapManagerListener keymapManagerListener = new MyKeymapManagerListener();
    final KeymapManagerEx keymapManager = KeymapManagerEx.getInstanceEx();
    myWeakKeymapManagerListener = keymapManagerListener;
    keymapManager.addWeakListener(keymapManagerListener);

    init();

    myToolWindowHandler = new ToolWindowHandler();
    myToolWindow.addPropertyChangeListener(myToolWindowHandler);

    apply(info);
  }

  public boolean isFocused() {
    IdeFocusManager fm = IdeFocusManager.getInstance(myProject);
    Component component = fm.getFocusedDescendantFor(myToolWindow.getComponent());
    if (component != null) return true;

    Component owner = fm.getLastFocusedFor(WindowManager.getInstance().getIdeFrame(myProject));

    return owner != null && SwingUtilities.isDescendingFrom(owner, myToolWindow.getComponent());
  }

  /**
   * Applies specified decoration.
   */
  public final void apply(final WindowInfoImpl info) {
    if (Comparing.equal(myInfo, info) || myProject == null || myProject.isDisposed()) {
      return;
    }
    myInfo = info;

    // Anchor
    final ToolWindowAnchor anchor = myInfo.getAnchor();
    if (info.isSliding()) {
      myDivider.invalidate();
      if (ToolWindowAnchor.TOP == anchor) {
        add(myDivider, BorderLayout.SOUTH);
      }
      else if (ToolWindowAnchor.LEFT == anchor) {
        add(myDivider, BorderLayout.EAST);
      }
      else if (ToolWindowAnchor.BOTTOM == anchor) {
        add(myDivider, BorderLayout.NORTH);
      }
      else if (ToolWindowAnchor.RIGHT == anchor) {
        add(myDivider, BorderLayout.WEST);
      }
      myDivider.setPreferredSize(new Dimension(DIVIDER_WIDTH, DIVIDER_WIDTH));
    }
    else { // docked and floating windows don't have divider
      remove(myDivider);
    }

    validate();
    repaint();

    //
    updateTitle();

    // Push "apply" request forward

    if (myInfo.isFloating() && myInfo.isVisible()) {
      final FloatingDecorator floatingDecorator = (FloatingDecorator)SwingUtilities.getAncestorOfClass(FloatingDecorator.class, this);
      if (floatingDecorator != null) {
        floatingDecorator.apply(myInfo);
      }
    }

    myToolWindow.getContentUI().setType(myInfo.getContentUiType());
    setBorder(new InnerPanelBorder(myToolWindow));
  }

  @Override
  public void calcData(DataKey key, DataSink sink) {
    if (PlatformDataKeys.TOOL_WINDOW.equals(key)) {
      sink.put(PlatformDataKeys.TOOL_WINDOW, myToolWindow);
    }
  }

  final void addInternalDecoratorListener(final InternalDecoratorListener l) {
    myListenerList.add(InternalDecoratorListener.class, l);
  }

  final void removeInternalDecoratorListener(final InternalDecoratorListener l) {
    myListenerList.remove(InternalDecoratorListener.class, l);
  }

  final void dispose() {
    removeAll();
    myToolWindow.removePropertyChangeListener(myToolWindowHandler);
    KeymapManagerEx.getInstanceEx().removeWeakListener(myWeakKeymapManagerListener);

    Disposer.dispose(myHeader);
    myHeader = null;
    myProject = null;
  }

  private void fireAnchorChanged(final ToolWindowAnchor anchor) {
    final InternalDecoratorListener[] listeners = myListenerList.getListeners(InternalDecoratorListener.class);
    for (InternalDecoratorListener listener : listeners) {
      listener.anchorChanged(this, anchor);
    }
  }

  private void fireAutoHideChanged(final boolean autoHide) {
    final InternalDecoratorListener[] listeners = myListenerList.getListeners(InternalDecoratorListener.class);
    for (InternalDecoratorListener listener : listeners) {
      listener.autoHideChanged(this, autoHide);
    }
  }

  /**
   * Fires event that "hide" button has been pressed.
   */
  final void fireHidden() {
    final InternalDecoratorListener[] listeners = myListenerList.getListeners(InternalDecoratorListener.class);
    for (InternalDecoratorListener listener : listeners) {
      listener.hidden(this);
    }
  }

  /**
   * Fires event that "hide" button has been pressed.
   */
  final void fireHiddenSide() {
    final InternalDecoratorListener[] listeners = myListenerList.getListeners(InternalDecoratorListener.class);
    for (InternalDecoratorListener listener : listeners) {
      listener.hiddenSide(this);
    }
  }

  /**
   * Fires event that user performed click into the title bar area.
   */
  final void fireActivated() {
    final InternalDecoratorListener[] listeners = myListenerList.getListeners(InternalDecoratorListener.class);
    for (InternalDecoratorListener listener : listeners) {
      listener.activated(this);
    }
  }

  private void fireTypeChanged(final ToolWindowType type) {
    final InternalDecoratorListener[] listeners = myListenerList.getListeners(InternalDecoratorListener.class);
    for (InternalDecoratorListener listener : listeners) {
      listener.typeChanged(this, type);
    }
  }

  final void fireResized() {
    final InternalDecoratorListener[] listeners = myListenerList.getListeners(InternalDecoratorListener.class);
    for (InternalDecoratorListener listener : listeners) {
      listener.resized(this);
    }
  }

  private void fireSideStatusChanged(boolean isSide) {
    final InternalDecoratorListener[] listeners = myListenerList.getListeners(InternalDecoratorListener.class);
    for (InternalDecoratorListener listener : listeners) {
      listener.sideStatusChanged(this, isSide);
    }
  }

  private void fireContentUiTypeChanges(ToolWindowContentUiType type) {
    final InternalDecoratorListener[] listeners = myListenerList.getListeners(InternalDecoratorListener.class);
    for (InternalDecoratorListener listener : listeners) {
      listener.contentUiTypeChanges(this, type);
    }
  }

  private void init() {
    enableEvents(AWTEvent.COMPONENT_EVENT_MASK);

    final JPanel contentPane = new JPanel(new BorderLayout());
    contentPane.add(myHeader, BorderLayout.NORTH);

    JPanel innerPanel = new JPanel(new BorderLayout());
    JComponent toolWindowComponent = myToolWindow.getComponent();
    innerPanel.add(toolWindowComponent, BorderLayout.CENTER);

    final NonOpaquePanel inner = new NonOpaquePanel(innerPanel);
    inner.setBorder(new EmptyBorder(0, 0, 0, 0));

    contentPane.add(inner, BorderLayout.CENTER);
    add(contentPane, BorderLayout.CENTER);
    if (SystemInfo.isMac) {
      setBackground(new JBColor(Gray._200, Gray._90));
    }

    // Add listeners
    registerKeyboardAction(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        ToolWindowManager.getInstance(myProject).activateEditorComponent();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
  }

  public void setTitleActions(AnAction[] actions) {
    myHeader.setAdditionalTitleActions(actions);
  }

  private class InnerPanelBorder implements Border {

    private final ToolWindow myWindow;

    private InnerPanelBorder(ToolWindow window) {
      myWindow = window;
    }

    @Override
    public void paintBorder(final Component c, final Graphics g, final int x, final int y, final int width, final int height) {
      g.setColor(UIUtil.getPanelBackground());
      doPaintBorder(c, g, x, y, width, height);
      g.setColor(new Color(0, 0, 0, 90));
      doPaintBorder(c, g, x, y, width, height);
    }

    private void doPaintBorder(Component c, Graphics g, int x, int y, int width, int height) {
      Insets insets = getBorderInsets(c);

      if (insets.top > 0) {
        UIUtil.drawLine(g, x, y + insets.top - 1, x + width - 1, y + insets.top - 1);
      }

      if (insets.left > 0) {
        UIUtil.drawLine(g, x, y + insets.top, x, y + height - 1);
      }

      if (insets.right > 0) {
        UIUtil.drawLine(g, x + width - 1, y + insets.top, x + width - 1, y + height - 1);
      }

      if (insets.bottom > 0) {
        UIUtil.drawLine(g, x, y + height - 1, x + width - 1, y + height - 1);
       }
    }

    @Override
    public Insets getBorderInsets(final Component c) {
      if (myProject == null) return new Insets(0, 0, 0, 0);
      ToolWindowManager toolWindowManager =  ToolWindowManager.getInstance(myProject);
      if (!(toolWindowManager instanceof ToolWindowManagerImpl)
          || !((ToolWindowManagerImpl)toolWindowManager).isToolWindowRegistered(myInfo.getId())
          || myWindow.getType() == ToolWindowType.FLOATING) {
        return new Insets(0, 0, 0, 0);
      }
      ToolWindowAnchor anchor = myWindow.getAnchor();
      Component component = myWindow.getComponent();
      Container parent = component.getParent();
      while(parent != null) {
        if (parent instanceof Splitter) {
          Splitter splitter = (Splitter)parent;
          boolean isFirst = splitter.getFirstComponent() == component;
          boolean isVertical = splitter.isVertical();
          return new Insets(0,
                            anchor == ToolWindowAnchor.RIGHT || (!isVertical && !isFirst) ? 1 : 0,
                            (isVertical && isFirst) ? 1 : 0,
                            anchor == ToolWindowAnchor.LEFT || (!isVertical && isFirst) ? 1 : 0);
        }
        component = parent;
        parent = component.getParent();
      }
      return new Insets(0, anchor == ToolWindowAnchor.RIGHT ? 1 : 0, 0, anchor == ToolWindowAnchor.LEFT ? 1 : 0);
    }

    @Override
    public boolean isBorderOpaque() {
      return false;
    }
  }


  public final ActionGroup createPopupGroup() {
    final DefaultActionGroup group = createGearPopupGroup();

    group.add(myToggleContentUiTypeAction);

    final DefaultActionGroup moveGroup = new DefaultActionGroup(UIBundle.message("tool.window.move.to.action.group.name"), true);
    final ToolWindowAnchor anchor = myInfo.getAnchor();
    if (anchor != ToolWindowAnchor.TOP) {
      final AnAction topAction = new ChangeAnchorAction(UIBundle.message("tool.window.move.to.top.action.name"), ToolWindowAnchor.TOP);
      moveGroup.add(topAction);
    }
    if (anchor != ToolWindowAnchor.LEFT) {
      final AnAction leftAction = new ChangeAnchorAction(UIBundle.message("tool.window.move.to.left.action.name"), ToolWindowAnchor.LEFT);
      moveGroup.add(leftAction);
    }
    if (anchor != ToolWindowAnchor.BOTTOM) {
      final AnAction bottomAction =
        new ChangeAnchorAction(UIBundle.message("tool.window.move.to.bottom.action.name"), ToolWindowAnchor.BOTTOM);
      moveGroup.add(bottomAction);
    }
    if (anchor != ToolWindowAnchor.RIGHT) {
      final AnAction rightAction =
        new ChangeAnchorAction(UIBundle.message("tool.window.move.to.right.action.name"), ToolWindowAnchor.RIGHT);
      moveGroup.add(rightAction);
    }
    group.add(moveGroup);

    DefaultActionGroup resize = new DefaultActionGroup(ActionsBundle.groupText("ResizeToolWindowGroup"), true);
    resize.add(new ResizeToolWindowAction.Left(myToolWindow, this));
    resize.add(new ResizeToolWindowAction.Right(myToolWindow, this));
    resize.add(new ResizeToolWindowAction.Up(myToolWindow, this));
    resize.add(new ResizeToolWindowAction.Down(myToolWindow, this));

    group.add(resize);

    group.addSeparator();
    group.add(new HideAction());
    return group;
  }

  private DefaultActionGroup createGearPopupGroup() {
    final DefaultActionGroup group = new DefaultActionGroup();

    if (myAdditionalGearActions != null) {
      addSorted(group, myAdditionalGearActions);
      group.addSeparator();
    }
    if (myInfo.isDocked()) {
      group.add(myToggleAutoHideModeAction);
      group.add(myToggleDockModeAction);
      group.add(myToggleFloatingModeAction);
      group.add(myToggleSideModeAction);
    }
    else if (myInfo.isFloating()) {
      group.add(myToggleAutoHideModeAction);
      group.add(myToggleFloatingModeAction);
    }
    else if (myInfo.isSliding()) {
      group.add(myToggleDockModeAction);
      group.add(myToggleFloatingModeAction);
      group.add(myToggleSideModeAction);
    }
    return group;
  }

  private static void addSorted(DefaultActionGroup main, ActionGroup group) {
    final AnAction[] children = group.getChildren(null);
    boolean hadSecondary = false;
    for (AnAction action : children) {
      if (group.isPrimary(action)) {
        main.add(action);
      } else {
        hadSecondary = true;
      }
    }
    if (hadSecondary) {
      main.addSeparator();
      for (AnAction action : children) {
        if (!group.isPrimary(action)) {
          main.addAction(action).setAsSecondary(true);
        }
      }
    }
  }

  /**
   * @return tool window associated with the decorator.
   */
  final ToolWindowImpl getToolWindow() {
    return myToolWindow;
  }

  /**
   * @return last window info applied to the decorator.
   */
  final WindowInfoImpl getWindowInfo() {
    return myInfo;
  }

  public int getHeaderHeight() {
    return myHeader.getPreferredSize().height;
  }

  @Override
  protected final void processComponentEvent(final ComponentEvent e) {
    super.processComponentEvent(e);
    if (ComponentEvent.COMPONENT_RESIZED == e.getID()) {
      fireResized();
    }
  }

  // TODO: to b removed
  private void updateTitle() {
    final StringBuffer fullTitle = new StringBuffer();
    //  Due to JDK's bug #4234645 we cannot support custom decoration on Linux platform.
    // The prblem is that Window.setLocation() doesn't work properly wjen the dialod is displayable.
    // Therefore we use native WM decoration. When the dialog has native decoration we show window ID
    // in the dialog's title and window title at the custom title panel. If the custom decoration
    // is used we show composite string at the custom title panel.
    // TODO[vova] investigate the problem under Mac OSX.
    if (SystemInfo.isWindows || !myInfo.isFloating()) {
      fullTitle.append(myInfo.getId());
      final String title = myToolWindow.getTitle();
      if (title != null && title.length() > 0) {
        fullTitle.append(" - ").append(title);
      }
    }
    else { // Unixes ans MacOSX go here when tool window is in floating mode
      final String title = myToolWindow.getTitle();
      if (title != null && title.length() > 0) {
        fullTitle.append(title);
      }
    }
  }

  private final class ChangeAnchorAction extends AnAction implements DumbAware {
    private final ToolWindowAnchor myAnchor;

    public ChangeAnchorAction(final String title, final ToolWindowAnchor anchor) {
      super(title);
      myAnchor = anchor;
    }

    @Override
    public final void actionPerformed(final AnActionEvent e) {
      fireAnchorChanged(myAnchor);
    }
  }

  private final class TogglePinnedModeAction extends ToggleAction implements DumbAware {
    public TogglePinnedModeAction() {
      copyFrom(ActionManager.getInstance().getAction(TOGGLE_PINNED_MODE_ACTION_ID));
    }

    @Override
    public final boolean isSelected(final AnActionEvent event) {
      return !myInfo.isAutoHide();
    }

    @Override
    public final void setSelected(final AnActionEvent event, final boolean flag) {
      fireAutoHideChanged(!myInfo.isAutoHide());
    }
  }

  private final class ToggleDockModeAction extends ToggleAction implements DumbAware {
    public ToggleDockModeAction() {
      copyFrom(ActionManager.getInstance().getAction(TOGGLE_DOCK_MODE_ACTION_ID));
    }

    @Override
    public final boolean isSelected(final AnActionEvent event) {
      return myInfo.isDocked();
    }

    @Override
    public final void setSelected(final AnActionEvent event, final boolean flag) {
      if (myInfo.isDocked()) {
        fireTypeChanged(ToolWindowType.SLIDING);
      }
      else if (myInfo.isSliding()) {
        fireTypeChanged(ToolWindowType.DOCKED);
      }
    }
  }

  private final class ToggleFloatingModeAction extends ToggleAction implements DumbAware {
    public ToggleFloatingModeAction() {
      copyFrom(ActionManager.getInstance().getAction(TOGGLE_FLOATING_MODE_ACTION_ID));
    }

    @Override
    public final boolean isSelected(final AnActionEvent event) {
      return myInfo.isFloating();
    }

    @Override
    public final void setSelected(final AnActionEvent event, final boolean flag) {
      if (myInfo.isFloating()) {
        fireTypeChanged(myInfo.getInternalType());
      }
      else {
        fireTypeChanged(ToolWindowType.FLOATING);
      }
    }
  }

  private final class ToggleSideModeAction extends ToggleAction implements DumbAware {
    public ToggleSideModeAction() {
      copyFrom(ActionManager.getInstance().getAction(TOGGLE_SIDE_MODE_ACTION_ID));
    }

    @Override
    public final boolean isSelected(final AnActionEvent event) {
      return myInfo.isSplit();
    }

    @Override
    public final void setSelected(final AnActionEvent event, final boolean flag) {
      fireSideStatusChanged(flag);
    }

    @Override
    public void update(final AnActionEvent e) {
      super.update(e);
    }
  }

  private final class HideAction extends AnAction implements DumbAware {
    @NonNls public static final String HIDE_ACTIVE_WINDOW_ACTION_ID = InternalDecorator.HIDE_ACTIVE_WINDOW_ACTION_ID;

    public HideAction() {
      copyFrom(ActionManager.getInstance().getAction(HIDE_ACTIVE_WINDOW_ACTION_ID));
      getTemplatePresentation().setText(UIBundle.message("tool.window.hide.action.name"));
    }

    @Override
    public final void actionPerformed(final AnActionEvent e) {
      fireHidden();
    }

    @Override
    public final void update(final AnActionEvent event) {
      final Presentation presentation = event.getPresentation();
      presentation.setEnabled(myInfo.isVisible());
    }
  }


  private final class ToggleContentUiTypeAction extends ToggleAction implements DumbAware {
    private ToggleContentUiTypeAction() {
      copyFrom(ActionManager.getInstance().getAction(TOGGLE_CONTENT_UI_TYPE_ACTION_ID));
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myInfo.getContentUiType() == ToolWindowContentUiType.TABBED;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      fireContentUiTypeChanges(state ? ToolWindowContentUiType.TABBED : ToolWindowContentUiType.COMBO);
    }
  }

  private final class MyDivider extends JPanel {
    private boolean myDragging;
    private Point myLastPoint;

    private MyDivider() {
      myDragging = false;
      enableEvents(MouseEvent.MOUSE_EVENT_MASK | MouseEvent.MOUSE_MOTION_EVENT_MASK);
      setBorder(new DividerBorder());
    }

    @Override
    protected final void processMouseMotionEvent(final MouseEvent e) {
      super.processMouseMotionEvent(e);
      if (MouseEvent.MOUSE_DRAGGED == e.getID()) {
        myDragging = true;
        final ToolWindowAnchor anchor = myInfo.getAnchor();
        final boolean isVerticalCursor = myInfo.isDocked() ? anchor.isSplitVertically() : anchor.isHorizontal();
        setCursor(isVerticalCursor ? Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR) : Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
        final Point point = e.getPoint();

        final Container windowPane = InternalDecorator.this.getParent();
        myLastPoint = SwingUtilities.convertPoint(this, point, windowPane);
        myLastPoint.x = Math.min(Math.max(myLastPoint.x, 0), windowPane.getWidth());
        myLastPoint.y = Math.min(Math.max(myLastPoint.y, 0), windowPane.getHeight());

        final Rectangle bounds = InternalDecorator.this.getBounds();
        if (anchor == ToolWindowAnchor.TOP) {
          if (myLastPoint.y < DIVIDER_WIDTH) {
            myLastPoint.y = DIVIDER_WIDTH;
          }
          InternalDecorator.this.setBounds(0, 0, bounds.width, myLastPoint.y);
        }
        else if (anchor == ToolWindowAnchor.LEFT) {
          if (myLastPoint.x < DIVIDER_WIDTH) {
            myLastPoint.x = DIVIDER_WIDTH;
          }
          InternalDecorator.this.setBounds(0, 0, myLastPoint.x, bounds.height);
        }
        else if (anchor == ToolWindowAnchor.BOTTOM) {
          if (myLastPoint.y > windowPane.getHeight() - DIVIDER_WIDTH) {
            myLastPoint.y = windowPane.getHeight() - DIVIDER_WIDTH;
          }
          InternalDecorator.this.setBounds(0, myLastPoint.y, bounds.width, windowPane.getHeight() - myLastPoint.y);
        }
        else if (anchor == ToolWindowAnchor.RIGHT) {
          if (myLastPoint.x > windowPane.getWidth() - DIVIDER_WIDTH) {
            myLastPoint.x = windowPane.getWidth() - DIVIDER_WIDTH;
          }
          InternalDecorator.this.setBounds(myLastPoint.x, 0, windowPane.getWidth() - myLastPoint.x, bounds.height);
        }
        InternalDecorator.this.validate();
      }
    }

    @Override
    protected final void processMouseEvent(final MouseEvent e) {
      super.processMouseEvent(e);
      final boolean isVerticalCursor = myInfo.isDocked() ? myInfo.getAnchor().isSplitVertically() : myInfo.getAnchor().isHorizontal();
      switch (e.getID()) {
        case MouseEvent.MOUSE_MOVED:
        default:
          break;
        case MouseEvent.MOUSE_ENTERED:
          setCursor(
            isVerticalCursor ? Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR) : Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
          break;
        case MouseEvent.MOUSE_EXITED:
          if (!myDragging) {
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
          }
          break;
        case MouseEvent.MOUSE_PRESSED:
          setCursor(
            isVerticalCursor ? Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR) : Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
          break;
        case MouseEvent.MOUSE_RELEASED:
          myDragging = false;
          myLastPoint = null;
          break;
        case MouseEvent.MOUSE_CLICKED:
          break;
      }
    }

    private final class DividerBorder implements Border {
      @Override
      public final void paintBorder(final Component c, final Graphics g, final int x, final int y, final int width, final int height) {
        final ToolWindowAnchor anchor = myInfo.getAnchor();
        final boolean isVertical = !anchor.isSplitVertically();
        final JBColor outer = new JBColor(Color.white, Color.darkGray);
        if (isVertical) {
          if (anchor == ToolWindowAnchor.TOP) {
            g.setColor(outer);
            UIUtil.drawLine(g, x, y, x + width - 1, y);
            g.setColor(Color.darkGray);
            UIUtil.drawLine(g, x, y + height - 1, x + width - 1, y + height - 1);
          }
          else {
            g.setColor(Color.darkGray);
            UIUtil.drawLine(g, x, y, x + width - 1, y);
            g.setColor(outer);
            UIUtil.drawLine(g, x, y + height - 1, x + width - 1, y + height - 1);
          }
        }
        else {
          if (anchor == ToolWindowAnchor.LEFT) {
            g.setColor(outer);
            UIUtil.drawLine(g, x, y, x, y + height - 1);
            g.setColor(Color.darkGray);
            UIUtil.drawLine(g, x + width - 1, y, x + width - 1, y + height - 1);
          }
          else {
            g.setColor(Color.darkGray);
            UIUtil.drawLine(g, x, y, x, y + height - 1);
            g.setColor(outer);
            UIUtil.drawLine(g, x + width - 1, y, x + width - 1, y + height - 1);
          }
        }
      }

      @Override
      public final Insets getBorderInsets(final Component c) {
        if (c instanceof MyDivider) {
          return new Insets(1, 1, 1, 1);
        }
        return new Insets(0, 0, 0, 0);
      }

      @Override
      public final boolean isBorderOpaque() {
        return true;
      }
    }
  }

  /**
   * Updates tooltips.
   */
  private final class MyKeymapManagerListener implements KeymapManagerListener {
    @Override
    public final void activeKeymapChanged(final Keymap keymap) {
      if (myHeader != null) {
        myHeader.updateTooltips();
      }
    }
  }

  /**
   * Synchronizes decorator with IdeToolWindow changes.
   */
  private final class ToolWindowHandler implements PropertyChangeListener {
    @Override
    public final void propertyChange(final PropertyChangeEvent e) {
      final String name = e.getPropertyName();
      if (ToolWindowEx.PROP_TITLE.equals(name)) {
        updateTitle();
        if (myHeader != null) {
          myHeader.repaint();
        }
      }
    }
  }

  @Override
  public void putInfo(@NotNull Map<String, String> info) {
    info.put("toolWindowTitle", myToolWindow.getTitle());

    final Content selection = myToolWindow.getContentManager().getSelectedContent();
    if (selection != null) {
      info.put("toolWindowTab", selection.getTabName());
    }
  }

  public void setAdditionalGearActions(@Nullable ActionGroup additionalGearActions) {
    myAdditionalGearActions = additionalGearActions;
  }
}
