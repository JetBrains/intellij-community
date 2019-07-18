// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.ide.dnd.DnDAware;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.impl.EditorComponentImpl;
import com.intellij.openapi.ui.Divider;
import com.intellij.openapi.ui.Painter;
import com.intellij.openapi.ui.impl.GlassPaneDialogWrapperPeer;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.Weighted;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeGlassPane;
import com.intellij.openapi.wm.IdeGlassPaneUtil;
import com.intellij.ui.BalloonImpl;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.DisposableWrapperList;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.ui.EmptyClipboardOwner;
import com.intellij.util.ui.MouseEventAdapter;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class IdeGlassPaneImpl extends JPanel implements IdeGlassPaneEx, IdeEventQueue.EventDispatcher {

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.wm.impl.IdeGlassPaneImpl");
  private static final String PREPROCESSED_CURSOR_KEY = "SuperCursor";

  private final DisposableWrapperList<EventListener> myMouseListeners = new DisposableWrapperList<>();
  private final Set<EventListener> mySortedMouseListeners = new TreeSet<>((o1, o2) -> {
    double weight1 = 0;
    if (o1 instanceof Weighted) {
      weight1 = ((Weighted)o1).getWeight();
    }
    double weight2 = 0;
    if (o2 instanceof Weighted) {
      weight2 = ((Weighted)o2).getWeight();
    }
    return weight1 > weight2 ? 1 : weight1 < weight2 ? -1 : myMouseListeners.indexOf(o1) - myMouseListeners.indexOf(o2);
  });
  private final JRootPane myRootPane;

  private final Map<String, PaintersHelper> myNamedPainters = FactoryMap.create(key -> new PaintersHelper(this));

  private boolean myPreprocessorActive;
  private final Map<Object, Cursor> myListener2Cursor = new LinkedHashMap<>();

  private Component myLastCursorComponent;
  private Cursor myLastOriginalCursor;
  private MouseEvent myPrevPressEvent;

  WindowShadowPainter myWindowShadowPainter;

  public IdeGlassPaneImpl(JRootPane rootPane) {
    this(rootPane, false);
  }

  public IdeGlassPaneImpl(JRootPane rootPane, boolean installPainters) {
    myRootPane = rootPane;
    setOpaque(false);
    setVisible(false);
    setLayout(null);
    if (installPainters) {
      IdeBackgroundUtil.initFramePainters(this);
      IdeBackgroundUtil.initEditorPainters(this);
    }
    if (SystemInfo.isWindows && Registry.is("ide.window.shadow.painter")) {
      myWindowShadowPainter = new WindowShadowPainter();
      getPainters().addPainter(myWindowShadowPainter, null);
    }
  }

  @Override
  public boolean dispatch(@NotNull final AWTEvent e) {
    JRootPane eventRootPane = myRootPane;

    if (e instanceof MouseEvent) {
      MouseEvent me = (MouseEvent)e;
      Window eventWindow = UIUtil.getWindow(me.getComponent());

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
        IdeTooltipManager.getInstance().hideCurrent((MouseEvent)e);
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
      final Window eventWindow = UIUtil.getWindow(meComponent);
      if (eventWindow != SwingUtilities.getWindowAncestor(myRootPane)) {
        return false;
      }
      int button1 = InputEvent.BUTTON1_MASK | InputEvent.BUTTON1_DOWN_MASK;
      final boolean pureMouse1Event = (me.getModifiersEx() | button1) == button1;
      if (pureMouse1Event && me.getClickCount() <= 1 && !me.isPopupTrigger()) {
        final Point point = SwingUtilities.convertPoint(meComponent, me.getPoint(), myRootPane.getContentPane());
        final JMenuBar menuBar = myRootPane.getJMenuBar();
        if (menuBar != null && menuBar.isVisible())
          point.y += menuBar.getHeight();

        final Component target =
          SwingUtilities.getDeepestComponentAt(myRootPane.getContentPane().getParent(), point.x, point.y);
        if (target instanceof DnDAware) {
          final Point targetPoint = SwingUtilities.convertPoint(myRootPane.getContentPane().getParent(), point.x, point.y, target);
          final boolean overSelection = ((DnDAware)target).isOverSelection(targetPoint);
          if (overSelection) {
            final MouseListener[] listeners = target.getListeners(MouseListener.class);
            final MouseEvent mouseEvent = MouseEventAdapter.convert(me, target);
            switch (me.getID()) {
              case MouseEvent.MOUSE_PRESSED:
                if (target.isFocusable()) {
                  IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(target, true));
                }
                boolean consumed = false;
                for (final MouseListener listener : listeners) {
                  final String className = listener.getClass().getName();
                  if (className.contains("BasicTreeUI$") || className.contains("MacTreeUI$")) continue;
                  fireMouseEvent(listener, mouseEvent);
                  if (mouseEvent.isConsumed()) {
                    consumed = true;
                    break;
                  }
                }

                if (!mouseEvent.isConsumed()) {
                  final AWTEventListener[] eventListeners = Toolkit.getDefaultToolkit().getAWTEventListeners(AWTEvent.MOUSE_EVENT_MASK);
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
                    if (className.contains("BasicTreeUI$") || className.contains("MacTreeUI$")) {
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
          UIUtil.setCursor(this, target.getCursor());
          cursorSet = true;
        }
      }

      if (!cursorSet) {
        UIUtil.setCursor(this, Cursor.getDefaultCursor());
      }
    }

    return dispatched;
  }

  private static boolean isContextMenu(Window window) {
    if (window instanceof JWindow) {
      JLayeredPane layeredPane = ((JWindow)window).getLayeredPane();
      for (Component component : layeredPane.getComponents()) {
        if (component instanceof JPanel
            && ContainerUtil.findInstance(((JPanel)component).getComponents(), JPopupMenu.class) != null) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean preprocess(final MouseEvent e, final boolean motion, JRootPane eventRootPane) {
    try {
      if (UIUtil.getWindow(this) != UIUtil.getWindow(e.getComponent())) return false;

      final MouseEvent event = MouseEventAdapter.convert(e, eventRootPane);
      if (event.isAltDown() && SwingUtilities.isLeftMouseButton(event) && event.getID() == MouseEvent.MOUSE_PRESSED) {
        Component c = SwingUtilities.getDeepestComponentAt(e.getComponent(), e.getX(), e.getY());
        Balloon balloon = JBPopupFactory.getInstance().getParentBalloonFor(c);
        if (balloon instanceof BalloonImpl) {
          JComponent component = ((BalloonImpl)balloon).getComponent();
          component.getToolkit().getSystemClipboard().setContents(
            new StringSelection(UIUtil.getDebugText(component)), EmptyClipboardOwner.INSTANCE);
        }
      }

      if (!IdeGlassPaneUtil.canBePreprocessed(e)) {
        return false;
      }

      for (EventListener each : mySortedMouseListeners) {
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
                setCursor(target, cursor);
              }
            }

            UIUtil.setCursor(getRootPane(), cursor);
          }
        }
        else if (!e.isConsumed() && e.getID() != MouseEvent.MOUSE_DRAGGED) {
          cursor = Cursor.getDefaultCursor();
          JRootPane rootPane = getRootPane();
          if (rootPane != null) {
            UIUtil.setCursor(rootPane, cursor);
          } else {
            LOG.warn("Root pane is null. Event: " + e);
          }
          restoreLastComponent(null);
          myLastOriginalCursor = null;
          myLastCursorComponent = null;
        }
        myListener2Cursor.clear();
      }
    }
  }

  private static void setCursor(@NotNull Component target, Cursor cursor) {
    if (target instanceof EditorComponentImpl) {
      ((EditorComponentImpl)target).getEditor().setCustomCursor(IdeGlassPaneImpl.class, cursor);
    }
    else {
      if (target instanceof JComponent) {
        savePreProcessedCursor((JComponent)target, target.getCursor());
      }
      UIUtil.setCursor(target, cursor);
    }
  }

  private static void resetCursor(@NotNull Component target, Cursor lastCursor) {
    if (target instanceof EditorComponentImpl) {
      ((EditorComponentImpl)target).getEditor().setCustomCursor(IdeGlassPaneImpl.class, null);
    }
    else {
      Cursor cursor = null;
      if (target instanceof JComponent) {
        JComponent jComponent = (JComponent)target;
        cursor = (Cursor)jComponent.getClientProperty(PREPROCESSED_CURSOR_KEY);
        jComponent.putClientProperty(PREPROCESSED_CURSOR_KEY, null);
      }
      cursor = cursor != null ? cursor : lastCursor;
      UIUtil.setCursor(target, cursor);
    }
  }

  private static boolean canProcessCursorFor(Component target) {
    return !(target instanceof JMenuItem) &&
           !(target instanceof Divider) &&
           !(target instanceof JSeparator) &&
           !(target instanceof JEditorPane && ((JEditorPane)target).getEditorKit() instanceof HTMLEditorKit);
  }

  private static Component getCompWithCursor(Component c) {
    Component eachParentWithCursor = c;
    while (eachParentWithCursor != null) {
      if (eachParentWithCursor.isCursorSet()) return eachParentWithCursor;
      eachParentWithCursor = eachParentWithCursor.getParent();
    }

    return null;
  }

  private void restoreLastComponent(Component newC) {
    if (myLastCursorComponent != null && myLastCursorComponent != newC) {
      resetCursor(myLastCursorComponent, myLastOriginalCursor);
    }
  }

  public static boolean hasPreProcessedCursor(@NotNull  JComponent component) {
    return component.getClientProperty(PREPROCESSED_CURSOR_KEY) != null;
  }

  public static boolean savePreProcessedCursor(@NotNull  JComponent component, @NotNull Cursor cursor) {
    if (hasPreProcessedCursor(component)) {
      return false;
    }

    component.putClientProperty(PREPROCESSED_CURSOR_KEY, cursor);
    return true;
  }

  @Override
  public void setCursor(Cursor cursor, @NotNull Object requestor) {
    if (cursor == null) {
      myListener2Cursor.remove(requestor);
    }
    else {
      myListener2Cursor.put(requestor, cursor);
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

  @Override
  public void addMousePreprocessor(@NotNull final MouseListener listener, @NotNull Disposable parent) {
    _addListener(listener, parent);
  }


  @Override
  public void addMouseMotionPreprocessor(@NotNull final MouseMotionListener listener, @NotNull final Disposable parent) {
    _addListener(listener, parent);
  }

  private void _addListener(@NotNull EventListener listener, @NotNull Disposable parent) {
    if (!myMouseListeners.contains(listener)) {
      Disposable listenerDisposable = myMouseListeners.add(listener, parent);
      Disposer.register(listenerDisposable, () -> UIUtil.invokeLaterIfNeeded(() -> removeListener(listener)));
      updateSortedList();
    }
    activateIfNeeded();
  }

  @Override
  public void removeMouseMotionPreprocessor(@NotNull MouseMotionListener listener) {
    removeListener(listener);
  }

  private void removeListener(@NotNull EventListener listener) {
    if (myMouseListeners.remove(listener)) {
      updateSortedList();
    }
    deactivateIfNeeded();
  }

  private void updateSortedList() {
    mySortedMouseListeners.clear();
    mySortedMouseListeners.addAll(myMouseListeners);
  }

  private void deactivateIfNeeded() {
    if (myPreprocessorActive && myMouseListeners.isEmpty()) {
      myPreprocessorActive = false;
    }

    applyActivationState();
  }

  private void activateIfNeeded() {
    if (!myPreprocessorActive && !myMouseListeners.isEmpty()) {
      myPreprocessorActive = true;
    }

    applyActivationState();
  }

  private void applyActivationState() {
    boolean wasVisible = isVisible();
    boolean hasWork = getPainters().hasPainters() || getComponentCount() > 0;

    if (wasVisible != hasWork) {
      setVisible(hasWork);
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

  @NotNull
  PaintersHelper getNamedPainters(@NotNull String name) {
    return myNamedPainters.get(name);
  }

  @NotNull
  private PaintersHelper getPainters() {
    return getNamedPainters("glass");
  }

  @Override
  public void addPainter(final Component component, @NotNull final Painter painter, @NotNull final Disposable parent) {
    getPainters().addPainter(painter, component);
    activateIfNeeded();
    Disposer.register(parent, () -> SwingUtilities.invokeLater(() -> removePainter(painter)));
  }

  private void removePainter(@NotNull Painter painter) {
    getPainters().removePainter(painter);
    deactivateIfNeeded();
  }


  @Override
  protected void addImpl(Component comp, Object constraints, int index) {
    super.addImpl(comp, constraints, index);

    SwingUtilities.invokeLater(() -> activateIfNeeded());
  }

  @Override
  public void remove(final Component comp) {
    super.remove(comp);

    SwingUtilities.invokeLater(() -> deactivateIfNeeded());
  }

  @Override
  public boolean isInModalContext() {
    final Component[] components = getComponents();
    for (Component component : components) {
      if (component instanceof GlassPaneDialogWrapperPeer.TransparentLayeredPane) {
        return true;
      }
    }

    return false;
  }

  @Override
  protected void paintComponent(final Graphics g) {
    getPainters().paint(g);
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
    return !getPainters().hasPainters() && super.isOptimizedDrawingEnabled();
  }
}
