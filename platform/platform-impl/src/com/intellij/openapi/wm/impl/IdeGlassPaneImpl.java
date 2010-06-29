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
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.Painter;
import com.intellij.openapi.ui.impl.GlassPaneDialogWrapperPeer;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.MenuDragMouseEvent;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.lang.ref.WeakReference;
import java.util.*;

public class IdeGlassPaneImpl extends JPanel implements IdeGlassPaneEx, IdeEventQueue.EventDispatcher, Painter.Listener {

  private final Set<EventListener> myMouseListeners = new LinkedHashSet<EventListener>();
  private final JRootPane myRootPane;

  private final WeakReference<Component> myCurrentOverComponent = new WeakReference<Component>(null);
  private final WeakReference<Component> myMousePressedComponent = new WeakReference<Component>(null);

  private final Set<Painter> myPainters = new LinkedHashSet<Painter>();
  private final Map<Painter, Component> myPainter2Component = new LinkedHashMap<Painter, Component>();

  private boolean myPaintingActive;
  private boolean myPreprocessorActive;
  private Map<Object, Cursor> myListener2Cursor = new LinkedHashMap<Object, Cursor>();

  private Component myLastCursorComponent;
  private Cursor myLastOriginalCursor;

  public IdeGlassPaneImpl(JRootPane rootPane) {
    myRootPane = rootPane;
    setOpaque(false);
    setVisible(false);
    setLayout(null);
  }

  public boolean dispatch(final AWTEvent e) {
    boolean dispatched = false;

    if (e instanceof MouseEvent) {
      final MouseEvent me = (MouseEvent)e;
      Window eventWindow = me.getComponent() instanceof Window ? ((Window)me.getComponent()) : SwingUtilities.getWindowAncestor(me.getComponent());
      final Window thisGlassWindow = SwingUtilities.getWindowAncestor(myRootPane);
      if (eventWindow != thisGlassWindow) return false;
    }

    if (e.getID() == MouseEvent.MOUSE_PRESSED || e.getID() == MouseEvent.MOUSE_RELEASED || e.getID() == MouseEvent.MOUSE_CLICKED) {
      dispatched = preprocess((MouseEvent)e, false);
    } else if (e.getID() == MouseEvent.MOUSE_MOVED || e.getID() == MouseEvent.MOUSE_DRAGGED) {
      dispatched = preprocess((MouseEvent)e, true);
    } else if (e.getID() == MouseEvent.MOUSE_EXITED || e.getID() == MouseEvent.MOUSE_ENTERED) {
      dispatched = preprocess((MouseEvent)e, false);
    } else {
      return false;
    }

    if (isVisible() && getComponentCount() == 0) {
      boolean cursorSet = false;
      MouseEvent me = (MouseEvent)e;
      if (me.getComponent() != null) {
        final Point point = SwingUtilities.convertPoint(me.getComponent(), me.getPoint(), myRootPane.getContentPane());

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

  private boolean preprocess(final MouseEvent e, final boolean motion) {
    try {
      final MouseEvent event = convertEvent(e, myRootPane);
      for (EventListener each : myMouseListeners) {
        if (motion && each instanceof MouseMotionListener) {
          fireMouseMotion((MouseMotionListener)each, event);
        } else if (!motion && each instanceof MouseListener) {
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
      Cursor cursor;
      if (myListener2Cursor.size() > 0) {
        cursor = myListener2Cursor.values().iterator().next();

        final Point point = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), myRootPane.getContentPane());
        final Component target =
            SwingUtilities.getDeepestComponentAt(myRootPane.getContentPane().getParent(), point.x, point.y);

        restoreLastComponent(target);

        if (target != null) {
          if (myLastCursorComponent != target) {
            myLastCursorComponent = target;
            myLastOriginalCursor = target.getCursor();
          }
          target.setCursor(cursor);
        }

        getRootPane().setCursor(cursor);

      } else {
        cursor = Cursor.getDefaultCursor();
        getRootPane().setCursor(cursor);


        restoreLastComponent(null);
        myLastOriginalCursor = null;
        myLastCursorComponent = null;
      }
      myListener2Cursor.clear();
    }
  }

  private void restoreLastComponent(Component newC) {
    if (myLastCursorComponent != null && myLastCursorComponent != newC) {
      myLastCursorComponent.setCursor(myLastOriginalCursor);
    }
  }


  public void setCursor(Cursor cursor, Object requestor) {
    if (cursor == null) {
      myListener2Cursor.remove(requestor);
    } else {
      myListener2Cursor.put(requestor, cursor);
    }
  }

  private MouseEvent convertEvent(final MouseEvent e, final Component target) {
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
        SwingUtilities.invokeLater(new Runnable() {
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
      if (myPainters.size() == 0 && getComponentCount() == 0) {
        myPaintingActive = false;
      }
    }

    if (myPreprocessorActive && myMouseListeners.size() == 0) {
      myPreprocessorActive = false;
    }

    applyActivationState();
  }

  private void activateIfNeeded() {
    if (!myPaintingActive) {
      if (myPainters.size() > 0 || getComponentCount() > 0) {
        myPaintingActive = true;
      }
    }

    if (!myPreprocessorActive && myMouseListeners.size() > 0) {
      myPreprocessorActive = true;
    }

    applyActivationState();
  }

  private void applyActivationState() {
    if (isVisible() != myPaintingActive) {
      setVisible(myPaintingActive);
    }

    IdeEventQueue queue = IdeEventQueue.getInstance();
    if (!queue.containsDispatcher(this) && myPreprocessorActive) {
      queue.addDispatcher(this, null);
    } else if (queue.containsDispatcher(this) && !myPreprocessorActive) {
      queue.removeDispatcher(this);
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
  public Component add(final Component comp) {
    final Component result = super.add(comp);
    activateIfNeeded();
    return result; 
  }

  @Override
  public void remove(final Component comp) {
    super.remove(comp);
    deactivateIfNeeded();
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
    if (myPainters.size() == 0) return;

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
    return myPainters.size() > 0;
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

  private Component findComponent(final MouseEvent e, final Container container) {
    final Point lpPoint = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), container);
    final Component lpComponent = SwingUtilities.getDeepestComponentAt(container, lpPoint.x, lpPoint.y);
    return lpComponent;
  }

  @Override
  public boolean isOptimizedDrawingEnabled() {
    return hasPainters() ? false : super.isOptimizedDrawingEnabled();
  }
}
