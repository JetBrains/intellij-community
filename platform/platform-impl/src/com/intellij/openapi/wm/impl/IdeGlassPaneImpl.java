/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.ide.dnd.DnDAware;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Painter;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.impl.GlassPaneDialogWrapperPeer;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeGlassPane;
import com.intellij.openapi.wm.IdeGlassPaneUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.MenuDragMouseEvent;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

public class IdeGlassPaneImpl extends JPanel implements IdeGlassPaneEx, IdeEventQueue.EventDispatcher, Painter.Listener {

  private final Set<EventListener> myMouseListeners = new LinkedHashSet<EventListener>();
  private final JRootPane myRootPane;

  private final Set<Painter> myPainters = new LinkedHashSet<Painter>();
  private final Map<Painter, Component> myPainter2Component = new LinkedHashMap<Painter, Component>();

  private boolean myPaintingActive;
  private boolean myPreprocessorActive;
  private final Map<Object, Cursor> myListener2Cursor = new LinkedHashMap<Object, Cursor>();

  private Component myLastCursorComponent;
  private Cursor myLastOriginalCursor;
  private MouseEvent myPrevPressEvent;

  private JPanel myFocusProxy = new JPanel();

  public IdeGlassPaneImpl(JRootPane rootPane) {
    myRootPane = rootPane;
    setOpaque(false);
    setVisible(false);
    setLayout(null);

    myFocusProxy.setOpaque(false);
    myFocusProxy.setPreferredSize(new Dimension(0, 0));
    myFocusProxy.setFocusable(true);
    UIUtil.setFocusProxy(myFocusProxy, true);
  }

  @Override
  public void addNotify() {
    super.addNotify();

    if (myFocusProxy.getParent() != null) {
      myFocusProxy.getParent().remove(myFocusProxy);
    }

    if (myFocusProxy.getParent() != getParent()) {
      getParent().add(myFocusProxy);
      myFocusProxy.setBounds(0, 0, 0, 0);
    }
  }

  public boolean dispatch(final AWTEvent e) {
    JRootPane eventRootPane = myRootPane;

    if (e instanceof MouseEvent) {
      MouseEvent me = (MouseEvent)e;
      Window eventWindow =
        me.getComponent() instanceof Window ? (Window)me.getComponent() : SwingUtilities.getWindowAncestor(me.getComponent());

      if (isContextMenu(eventWindow)) return false;

      final Window thisGlassWindow = SwingUtilities.getWindowAncestor(myRootPane);

      if (eventWindow instanceof JWindow) {
        eventRootPane = ((JWindow)eventWindow).getRootPane();
        if (eventRootPane != null) {
          if (!(eventRootPane.getGlassPane() instanceof IdeGlassPane)) {
            final Container parentWindow = eventWindow.getParent();
            if (parentWindow instanceof Window) {
              eventWindow = (Window)parentWindow;
            }
          }
        }
      }

      if (eventWindow != thisGlassWindow) return false;
    }


    if (e.getID() == MouseEvent.MOUSE_DRAGGED) {
      if (ApplicationManager.getApplication() != null) {
        IdeTooltipManager.getInstance().hideCurrent((MouseEvent)e, null, null);
      }
    }

    boolean dispatched;
    if (e.getID() == MouseEvent.MOUSE_PRESSED || e.getID() == MouseEvent.MOUSE_RELEASED || e.getID() == MouseEvent.MOUSE_CLICKED) {
      dispatched = preprocess((MouseEvent)e, false, eventRootPane);
    }
    else if (e.getID() == MouseEvent.MOUSE_MOVED || e.getID() == MouseEvent.MOUSE_DRAGGED) {
      dispatched = preprocess((MouseEvent)e, true, eventRootPane);
    }
    else if (e.getID() == MouseEvent.MOUSE_EXITED || e.getID() == MouseEvent.MOUSE_ENTERED) {
      dispatched = preprocess((MouseEvent)e, false, eventRootPane);
    }
    else {
      return false;
    }

    MouseEvent me = (MouseEvent)e;
    final Component meComponent = me.getComponent();
    if (!dispatched && meComponent != null) {
      final Window eventWindow = meComponent instanceof Window ? (Window)meComponent : SwingUtilities.getWindowAncestor(meComponent);
      if (eventWindow != SwingUtilities.getWindowAncestor(myRootPane)) {
        return false;
      }
      int button1 = MouseEvent.BUTTON1_MASK | MouseEvent.BUTTON1_DOWN_MASK;
      final boolean pureMouse1Event = (me.getModifiersEx() | button1) == button1;
      if (pureMouse1Event && me.getClickCount() <= 1 && !me.isPopupTrigger()) {
        final Point point = SwingUtilities.convertPoint(meComponent, me.getPoint(), myRootPane.getContentPane());
        JMenuBar menuBar = myRootPane.getJMenuBar();
        point.y += menuBar != null ? menuBar.getHeight() : 0;

        final Component target =
          SwingUtilities.getDeepestComponentAt(myRootPane.getContentPane().getParent(), point.x, point.y);
        if (target instanceof DnDAware) {
          final Point targetPoint = SwingUtilities.convertPoint(myRootPane.getContentPane().getParent(), point.x, point.y, target);
          final boolean overSelection = ((DnDAware)target).isOverSelection(targetPoint);
          if (overSelection) {
            final MouseListener[] listeners = target.getListeners(MouseListener.class);
            final MouseEvent mouseEvent = convertEvent(me, target);
            switch (me.getID()) {
              case MouseEvent.MOUSE_PRESSED:
                boolean consumed = false;
                if (target.isFocusable()) target.requestFocus();
                for (final MouseListener listener : listeners) {
                  final String className = listener.getClass().getName();
                  if (className.indexOf("BasicTreeUI$") >= 0 || className.indexOf("MacTreeUI$") >= 0) continue;
                  fireMouseEvent(listener, mouseEvent);
                  if (mouseEvent.isConsumed()) {
                    consumed = true;
                    break;
                  }
                }

                if (!mouseEvent.isConsumed()) {
                  final AWTEventListener[] eventListeners = Toolkit.getDefaultToolkit().getAWTEventListeners(MouseEvent.MOUSE_EVENT_MASK);
                  if (eventListeners != null && eventListeners.length > 0) {
                    for (final AWTEventListener eventListener : eventListeners) {
                      eventListener.eventDispatched(me);
                      if (me.isConsumed()) break;
                    }

                    if (me.isConsumed()) {
                      consumed = true;
                      break;
                    }
                  }
                }

                if (!consumed) {
                  myPrevPressEvent = mouseEvent;
                }
                else {
                  me.consume();
                }

                dispatched = true;
                break;
              case MouseEvent.MOUSE_RELEASED:
                if (myPrevPressEvent != null && myPrevPressEvent.getComponent() == target) {
                  for (final MouseListener listener : listeners) {
                    final String className = listener.getClass().getName();
                    if (className.indexOf("BasicTreeUI$") >= 0 || className.indexOf("MacTreeUI$") >= 0) {
                      fireMouseEvent(listener, myPrevPressEvent);
                      fireMouseEvent(listener, mouseEvent);
                      if (mouseEvent.isConsumed()) {
                        break;
                      }
                    }

                    fireMouseEvent(listener, mouseEvent);
                    if (mouseEvent.isConsumed()) {
                      break;
                    }
                  }

                  if (mouseEvent.isConsumed()) {
                    me.consume();
                  }

                  myPrevPressEvent = null;
                  dispatched = true;
                }
                break;
              default:
                myPrevPressEvent = null;
                break;
            }
          }
        }
      }
    }


    if (isVisible() && getComponentCount() == 0) {
      boolean cursorSet = false;
      if (meComponent != null) {
        final Point point = SwingUtilities.convertPoint(meComponent, me.getPoint(), myRootPane.getContentPane());

        if (myRootPane.getMenuBar() != null && myRootPane.getMenuBar().isVisible()) {
          point.y += myRootPane.getMenuBar().getHeight();
        }

        final Component target =
          SwingUtilities.getDeepestComponentAt(myRootPane.getContentPane().getParent(), point.x, point.y);
        if (target != null) {
          setCursor(target.getCursor());
          cursorSet = true;
        }
      }

      if (!cursorSet) {
        setCursor(Cursor.getDefaultCursor());
      }
    }

    return dispatched;
  }

  private static boolean isContextMenu(Window window) {
    if (window != null) {
      for (Component component : window.getComponents()) {
        if (component instanceof JComponent
            && UIUtil.findComponentOfType((JComponent)component, JPopupMenu.class) != null) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean preprocess(final MouseEvent e, final boolean motion, JRootPane eventRootPane) {
    try {
      final MouseEvent event = convertEvent(e, eventRootPane);

      if (!IdeGlassPaneUtil.canBePreprocessed(e)) {
        return false;
      }

      for (EventListener each : myMouseListeners) {
        if (motion && each instanceof MouseMotionListener) {
          fireMouseMotion((MouseMotionListener)each, event);
        }
        else if (!motion && each instanceof MouseListener) {
          fireMouseEvent((MouseListener)each, event);
        }

        if (event.isConsumed()) {
          e.consume();
          return true;
        }
      }

      return false;
    }
    finally {
      if (eventRootPane == myRootPane) {
        Cursor cursor;
        if (!myListener2Cursor.isEmpty()) {
          cursor = myListener2Cursor.values().iterator().next();

          final Point point = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), myRootPane.getContentPane());
          Component target =
            SwingUtilities.getDeepestComponentAt(myRootPane.getContentPane().getParent(), point.x, point.y);

          if (canProcessCursorFor(target)) {
            target = getCompWithCursor(target);

            restoreLastComponent(target);

            if (target != null) {
              if (myLastCursorComponent != target) {
                myLastCursorComponent = target;
                myLastOriginalCursor = target.getCursor();
              }

              if (cursor != null && !cursor.equals(target.getCursor())) {
                target.setCursor(cursor);
              }
            }

            getRootPane().setCursor(cursor);
          }
        }
        else {
          cursor = Cursor.getDefaultCursor();
          getRootPane().setCursor(cursor);


          restoreLastComponent(null);
          myLastOriginalCursor = null;
          myLastCursorComponent = null;
        }
        myListener2Cursor.clear();
      }
    }
  }

  private boolean canProcessCursorFor(Component target) {
    if (target instanceof JMenu ||
        target instanceof JMenuItem ||
        target instanceof Splitter.Divider ||
        target instanceof JSeparator ||
        (target instanceof JEditorPane && ((JEditorPane)target).getEditorKit() instanceof HTMLEditorKit)) {
      return false;
    }
    return true;
  }

  private Component getCompWithCursor(Component c) {
    Component eachParentWithCursor = c;
    while (eachParentWithCursor != null) {
      if (eachParentWithCursor.isCursorSet()) return eachParentWithCursor;
      eachParentWithCursor = eachParentWithCursor.getParent();
    }

    return null;
  }

  private void restoreLastComponent(Component newC) {
    if (myLastCursorComponent != null && myLastCursorComponent != newC) {
      myLastCursorComponent.setCursor(myLastOriginalCursor);
    }
  }


  public void setCursor(Cursor cursor, @NotNull Object requestor) {
    if (cursor == null) {
      myListener2Cursor.remove(requestor);
    }
    else {
      myListener2Cursor.put(requestor, cursor);
    }
  }

  private static MouseEvent convertEvent(final MouseEvent e, final Component target) {
    final Point point = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), target);
    if (e instanceof MouseWheelEvent) {
      final MouseWheelEvent mwe = (MouseWheelEvent)e;
      return new MouseWheelEvent(target, mwe.getID(), mwe.getWhen(), mwe.getModifiersEx(), point.x, point.y, mwe.getClickCount(),
                                 mwe.isPopupTrigger(), mwe.getScrollType(), mwe.getScrollAmount(), mwe.getWheelRotation());
    }
    else if (e instanceof MenuDragMouseEvent) {
      final MenuDragMouseEvent de = (MenuDragMouseEvent)e;
      return new MenuDragMouseEvent(target, de.getID(), de.getWhen(), de.getModifiersEx(), point.x, point.y, e.getClickCount(),
                                    e.isPopupTrigger(), de.getPath(), de.getMenuSelectionManager());
    }
    else {
      return new MouseEvent(target, e.getID(), e.getWhen(), e.getModifiersEx(), point.x, point.y, e.getClickCount(), e.isPopupTrigger(),
                            e.getButton());
    }
  }

  private static void fireMouseEvent(final MouseListener listener, final MouseEvent event) {
    switch (event.getID()) {
      case MouseEvent.MOUSE_PRESSED:
        listener.mousePressed(event);
        break;
      case MouseEvent.MOUSE_RELEASED:
        listener.mouseReleased(event);
        break;
      case MouseEvent.MOUSE_ENTERED:
        listener.mouseEntered(event);
        break;
      case MouseEvent.MOUSE_EXITED:
        listener.mouseExited(event);
        break;
      case MouseEvent.MOUSE_CLICKED:
        listener.mouseClicked(event);
        break;
    }
  }

  private static void fireMouseMotion(MouseMotionListener listener, final MouseEvent event) {
    switch (event.getID()) {
      case MouseEvent.MOUSE_DRAGGED:
        listener.mouseDragged(event);
      case MouseEvent.MOUSE_MOVED:
        listener.mouseMoved(event);
    }
  }

  public void addMousePreprocessor(final MouseListener listener, Disposable parent) {
    _addListener(listener, parent);
  }


  public void addMouseMotionPreprocessor(final MouseMotionListener listener, final Disposable parent) {
    _addListener(listener, parent);
  }

  private void _addListener(final EventListener listener, final Disposable parent) {
    myMouseListeners.add(listener);
    activateIfNeeded();
    Disposer.register(parent, new Disposable() {
      public void dispose() {
        UIUtil.invokeLaterIfNeeded(new Runnable() {
          public void run() {
            removeListener(listener);
          }
        });
      }
    });
  }

  public void removeMousePreprocessor(final MouseListener listener) {
    removeListener(listener);
  }

  public void removeMouseMotionPreprocessor(final MouseMotionListener listener) {
    removeListener(listener);
  }

  private void removeListener(final EventListener listener) {
    myMouseListeners.remove(listener);
    deactivateIfNeeded();
  }

  private void deactivateIfNeeded() {
    if (myPaintingActive) {
      if (myPainters.isEmpty() && getComponentCount() == 0) {
        myPaintingActive = false;
      }
    }

    if (myPreprocessorActive && myMouseListeners.isEmpty()) {
      myPreprocessorActive = false;
    }

    applyActivationState();
  }

  private void activateIfNeeded() {
    if (!myPaintingActive) {
      if (!myPainters.isEmpty() || getComponentCount() > 0) {
        myPaintingActive = true;
      }
    }

    if (!myPreprocessorActive && !myMouseListeners.isEmpty()) {
      myPreprocessorActive = true;
    }

    applyActivationState();
  }

  private void applyActivationState() {
    boolean wasVisible = isVisible();

    if (wasVisible != myPaintingActive) {
      setVisible(myPaintingActive);
    }

    IdeEventQueue queue = IdeEventQueue.getInstance();
    if (!queue.containsDispatcher(this) && (myPreprocessorActive || isVisible())) {
      queue.addDispatcher(this, null);
    }
    else if (queue.containsDispatcher(this) && !myPreprocessorActive && !isVisible()) {
      queue.removeDispatcher(this);
    }

    if (wasVisible != isVisible()) {
      revalidate();
      repaint();
    }
  }

  public void addPainter(final Component component, final Painter painter, final Disposable parent) {
    myPainters.add(painter);
    myPainter2Component.put(painter, component == null ? this : component);
    painter.addListener(this);
    activateIfNeeded();
    Disposer.register(parent, new Disposable() {
      public void dispose() {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            removePainter(painter);
          }
        });
      }
    });
  }

  public void removePainter(final Painter painter) {
    myPainters.remove(painter);
    myPainter2Component.remove(painter);
    painter.removeListener(this);
    deactivateIfNeeded();
  }


  @Override
  protected void addImpl(Component comp, Object constraints, int index) {
    super.addImpl(comp, constraints, index);

    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        activateIfNeeded();
      }
    });
  }

  @Override
  public void remove(final Component comp) {
    super.remove(comp);

    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        deactivateIfNeeded();
      }
    });
  }

  public boolean isInModalContext() {
    final Component[] components = getComponents();
    for (Component component : components) {
      if (component instanceof GlassPaneDialogWrapperPeer.TransparentLayeredPane) {
        return true;
      }
    }

    return false;
  }

  protected void paintComponent(final Graphics g) {
    if (myPainters.isEmpty()) return;

    Graphics2D g2d = (Graphics2D)g;
    for (Painter painter : myPainters) {
      final Rectangle clip = g.getClipBounds();

      final Component component = myPainter2Component.get(painter);
      if (component.getParent() == null) continue;
      final Rectangle componentBounds = SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), this);

      if (!painter.needsRepaint()) {
        continue;
      }

      if (clip.contains(componentBounds) || clip.intersects(componentBounds)) {
        final Point targetPoint = SwingUtilities.convertPoint(this, 0, 0, component);
        final Rectangle targetRect = new Rectangle(targetPoint, component.getSize());
        g2d.translate(-targetRect.x, -targetRect.y);
        painter.paint(component, g2d);
        g2d.translate(targetRect.x, targetRect.y);
      }
    }
  }

  @Override
  protected void paintChildren(Graphics g) {
    super.paintChildren(g);
  }

  public boolean hasPainters() {
    return !myPainters.isEmpty();
  }

  public void onNeedsRepaint(final Painter painter, final JComponent dirtyComponent) {
    if (dirtyComponent != null && dirtyComponent.isShowing()) {
      final Rectangle rec = SwingUtilities.convertRectangle(dirtyComponent, dirtyComponent.getBounds(), this);
      if (rec != null) {
        repaint(rec);
        return;
      }
    }

    repaint();
  }

  public Component getTargetComponentFor(MouseEvent e) {
    Component candidate = findComponent(e, myRootPane.getLayeredPane());
    if (candidate != null) return candidate;
    candidate = findComponent(e, myRootPane.getContentPane());
    if (candidate != null) return candidate;
    return e.getComponent();
  }

  private static Component findComponent(final MouseEvent e, final Container container) {
    final Point lpPoint = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), container);
    return SwingUtilities.getDeepestComponentAt(container, lpPoint.x, lpPoint.y);
  }

  @Override
  public boolean isOptimizedDrawingEnabled() {
    return !hasPainters() && super.isOptimizedDrawingEnabled();
  }

  @Override
  public JComponent getProxyComponent() {
    return myFocusProxy;
  }
}
