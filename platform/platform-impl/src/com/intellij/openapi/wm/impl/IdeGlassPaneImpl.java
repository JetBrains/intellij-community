// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.Weighted;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeGlassPaneUtil;
import com.intellij.ui.ComponentUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyClipboardOwner;
import com.intellij.util.ui.MouseEventAdapter;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.util.List;
import java.util.*;

public class IdeGlassPaneImpl extends JPanel implements IdeGlassPaneEx, IdeEventQueue.EventDispatcher {
  private static final Logger LOG = Logger.getInstance(IdeGlassPaneImpl.class);
  private static final String PREPROCESSED_CURSOR_KEY = "SuperCursor";

  private final List<EventListener> myMouseListeners = new ArrayList<>();

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

  private final Map<String, PaintersHelper> myNamedPainters = new HashMap<>();

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
    setEnabled(false);//Workaround to fix cursor when some semi-transparent 'highlighting area' overrides it to default
    setLayout(null);
    if (installPainters) {
      IdeBackgroundUtil.initFramePainters(this);
      IdeBackgroundUtil.initEditorPainters(this);
    }

    if (SystemInfo.isWindows && Registry.is("ide.window.shadow.painter", false)) {
      myWindowShadowPainter = new WindowShadowPainter();
      getPainters().addPainter(myWindowShadowPainter, null);
    }
  }

  @Override
  public boolean dispatch(@NotNull final AWTEvent e) {
    return e instanceof MouseEvent && dispatchMouseEvent((MouseEvent)e);
  }

  private boolean dispatchMouseEvent(@NotNull MouseEvent event) {
    JRootPane eventRootPane = myRootPane;

    Window eventWindow = ComponentUtil.getWindow(event.getComponent());
    if (isContextMenu(eventWindow)) {
      return false;
    }

    Window thisGlassWindow = SwingUtilities.getWindowAncestor(myRootPane);
    if (eventWindow != thisGlassWindow) {
      return false;
    }

    if (event.getID() == MouseEvent.MOUSE_DRAGGED) {
      if (ApplicationManager.getApplication() != null) {
        IdeTooltipManager.getInstance().hideCurrent(event);
      }
    }

    boolean dispatched;
    if (event.getID() == MouseEvent.MOUSE_PRESSED || event.getID() == MouseEvent.MOUSE_RELEASED || event.getID() == MouseEvent.MOUSE_CLICKED) {
      dispatched = preprocess(event, false, eventRootPane);
    }
    else if (event.getID() == MouseEvent.MOUSE_MOVED || event.getID() == MouseEvent.MOUSE_DRAGGED) {
      dispatched = preprocess(event, true, eventRootPane);
    }
    else if (event.getID() == MouseEvent.MOUSE_EXITED || event.getID() == MouseEvent.MOUSE_ENTERED) {
      dispatched = preprocess(event, false, eventRootPane);
    }
    else {
      return false;
    }

    Component meComponent = event.getComponent();
    JMenuBar menuBar = myRootPane.getJMenuBar();
    if (!dispatched && meComponent != null) {
      if (eventWindow != SwingUtilities.getWindowAncestor(myRootPane)) {
        return false;
      }

      int button1 = InputEvent.BUTTON1_MASK | InputEvent.BUTTON1_DOWN_MASK;
      boolean pureMouse1Event = (event.getModifiersEx() | button1) == button1;
      if (pureMouse1Event && event.getClickCount() <= 1 && !event.isPopupTrigger()) {
        Point point = SwingUtilities.convertPoint(meComponent, event.getPoint(), myRootPane.getContentPane());
        if (menuBar != null && menuBar.isVisible()) {
          point.y += menuBar.getHeight();
        }

        Component target = SwingUtilities.getDeepestComponentAt(myRootPane.getContentPane().getParent(), point.x, point.y);
        dispatched = target instanceof DnDAware && dispatchForDnDAware(event, point, target);
      }
    }

    if (isVisible() && getComponentCount() == 0) {
      boolean cursorSet = false;
      if (meComponent != null) {
        Point point = SwingUtilities.convertPoint(meComponent, event.getPoint(), myRootPane.getContentPane());
        if (menuBar != null && menuBar.isVisible()) {
          point.y += menuBar.getHeight();
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

  private boolean dispatchForDnDAware(@NotNull MouseEvent event, @NotNull Point point, @NotNull Component target) {
    Point targetPoint = SwingUtilities.convertPoint(myRootPane.getContentPane().getParent(), point.x, point.y, target);
    boolean overSelection = ((DnDAware)target).isOverSelection(targetPoint);
    if (!overSelection) {
      return false;
    }

    boolean dispatched = false;
    switch (event.getID()) {
      case MouseEvent.MOUSE_PRESSED:
        if (target.isFocusable()) {
          IdeFocusManager.getGlobalInstance()
            .doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(target, true));
        }

        boolean consumed = false;
        MouseEvent mouseEvent = MouseEventAdapter.convert(event, target);
        for (MouseListener listener : target.getListeners(MouseListener.class)) {
          String className = listener.getClass().getName();
          if (className.contains("BasicTreeUI$") || className.contains("MacTreeUI$")) {
            continue;
          }

          fireMouseEvent(listener, mouseEvent);
          if (mouseEvent.isConsumed()) {
            consumed = true;
            break;
          }
        }

        if (!mouseEvent.isConsumed()) {
          AWTEventListener[] eventListeners = Toolkit.getDefaultToolkit().getAWTEventListeners(AWTEvent.MOUSE_EVENT_MASK);
          if (eventListeners != null && eventListeners.length > 0) {
            for (AWTEventListener eventListener : eventListeners) {
              eventListener.eventDispatched(event);
              if (event.isConsumed()) {
                break;
              }
            }

            if (event.isConsumed()) {
              break;
            }
          }
        }

        if (consumed) {
          event.consume();
        }
        else {
          myPrevPressEvent = mouseEvent;
        }

        dispatched = true;
        break;

      case MouseEvent.MOUSE_RELEASED:
        return dispatchMouseReleased(event, target);

      default:
        myPrevPressEvent = null;
        break;
    }
    return dispatched;
  }

  private boolean dispatchMouseReleased(@NotNull MouseEvent event, @NotNull Component target) {
    MouseEvent mouseEvent = MouseEventAdapter.convert(event, target);
    if (myPrevPressEvent == null || myPrevPressEvent.getComponent() != target) {
      return false;
    }

    for (MouseListener listener : target.getListeners(MouseListener.class)) {
      String className = listener.getClass().getName();
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
      event.consume();
    }

    myPrevPressEvent = null;
    return true;
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

  private boolean preprocess(@NotNull MouseEvent e, boolean motion, JRootPane eventRootPane) {
    try {
      if (ComponentUtil.getWindow(this) != ComponentUtil.getWindow(e.getComponent())) {
        return false;
      }

      MouseEvent event = MouseEventAdapter.convert(e, eventRootPane);
      if (event.isAltDown() && SwingUtilities.isLeftMouseButton(event) && event.getID() == MouseEvent.MOUSE_PRESSED) {
        Component c = SwingUtilities.getDeepestComponentAt(e.getComponent(), e.getX(), e.getY());
        Component component =
          ComponentUtil.findParentByCondition(c, comp -> UIUtil.isClientPropertyTrue(comp, UIUtil.TEXT_COPY_ROOT));
        if (component != null) {
          component.getToolkit().getSystemClipboard()
            .setContents(new StringSelection(UIUtil.getDebugText(component)), EmptyClipboardOwner.INSTANCE);
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
      cursor = cursor == null ? lastCursor : cursor;
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

  private static void fireMouseMotion(@NotNull MouseMotionListener listener, @NotNull MouseEvent event) {
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
  public void addMouseMotionPreprocessor(@NotNull MouseMotionListener listener, @NotNull Disposable parent) {
    _addListener(listener, parent);
  }

  private void _addListener(@NotNull EventListener listener, @NotNull Disposable parent) {
    myMouseListeners.add(listener);
    Disposer.register(parent, () -> {
      UIUtil.invokeLaterIfNeeded(() -> removeListener(listener));
    });
    updateSortedList();

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
    return myNamedPainters.computeIfAbsent(name, key -> new PaintersHelper(this));
  }

  @NotNull
  private PaintersHelper getPainters() {
    return getNamedPainters("glass");
  }

  @Override
  public void addPainter(@Nullable Component component, @NotNull Painter painter, @NotNull Disposable parent) {
    getPainters().addPainter(painter, component);
    activateIfNeeded();
    Disposer.register(parent, () -> {
      SwingUtilities.invokeLater(() -> removePainter(painter));
    });
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
    for (Component component : getComponents()) {
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
    if (candidate != null) {
      return candidate;
    }
    candidate = findComponent(e, myRootPane.getContentPane());
    if (candidate != null) {
      return candidate;
    }
    return e.getComponent();
  }

  private static Component findComponent(final MouseEvent e, final Container container) {
    Point lpPoint = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), container);
    return SwingUtilities.getDeepestComponentAt(container, lpPoint.x, lpPoint.y);
  }

  @Override
  public boolean isOptimizedDrawingEnabled() {
    return !getPainters().hasPainters() && super.isOptimizedDrawingEnabled();
  }
}
