// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.util.Key;
import com.intellij.util.MathUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.TimerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public final class JBAutoScroller implements ActionListener {
  private static final int SCROLL_UPDATE_INTERVAL = 15;
  private static final Key<ScrollDeltaProvider> SCROLL_HANDLER_KEY = Key.create("JBAutoScroller.AutoScrollHandler");
  private static final JBAutoScroller ourInstance = new JBAutoScroller();

  private final Timer myTimer = TimerUtil.createNamedTimer("JBAutoScroller", SCROLL_UPDATE_INTERVAL, this);
  private final DefaultScrollDeltaProvider myDefaultAutoScrollHandler = new DefaultScrollDeltaProvider();

  private SyntheticDragEvent myLatestDragEvent;
  private int myHorizontalScrollDelta;
  private int myVerticalScrollDelta;

  private JBAutoScroller() {
  }

  public static void installOn(@NotNull JComponent component, @Nullable AutoscrollLocker locker) {
    installOn(component, null, locker);
  }

  public static void installOn(@NotNull JComponent component, @Nullable ScrollDeltaProvider handler) {
    installOn(component, handler, null);
  }

  public static void installOn(@NotNull JComponent component, @Nullable ScrollDeltaProvider handler, @Nullable AutoscrollLocker locker) {
    ourInstance.doInstallOn(component, handler, locker);
  }

  private void doInstallOn(@NotNull JComponent component, @Nullable ScrollDeltaProvider handler, @Nullable AutoscrollLocker locker) {
    component.setAutoscrolls(false); // disable swing autoscroll

    if (handler != null) {
      component.putClientProperty(SCROLL_HANDLER_KEY, handler);
    }

    if (component instanceof JTable t) {
      new MoveTableCellEditorOnAutoscrollFix(t);
      new ScrollOnTableSelectionChangeFix(t, locker);
    }

    component.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        start();
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        stop();
      }
    });
    component.addMouseMotionListener(new MouseMotionAdapter() {
      @Override
      public void mouseDragged(MouseEvent e) {
        if (e instanceof SyntheticDragEvent) return;

        JComponent c = (JComponent)e.getComponent();
        ScrollDeltaProvider handler = (ScrollDeltaProvider)c.getClientProperty(SCROLL_HANDLER_KEY);
        handler = ObjectUtils.notNull(handler, myDefaultAutoScrollHandler);

        myVerticalScrollDelta = handler.getVerticalScrollDelta(e);
        myHorizontalScrollDelta = handler.getHorizontalScrollDelta(e);
        myLatestDragEvent = new SyntheticDragEvent(c, e.getID(), e.getWhen(), e.getModifiersEx(),
                                                   c.getX(), c.getY(), e.getXOnScreen(), e.getYOnScreen(),
                                                   e.getClickCount(), e.isPopupTrigger(), e.getButton());
      }
    });
  }

  private void start() {
    myVerticalScrollDelta = 0;
    myHorizontalScrollDelta = 0;
    myTimer.start();
  }

  private void stop() {
    myTimer.stop();
    myLatestDragEvent = null;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    if (myLatestDragEvent == null) return;

    JComponent component = (JComponent)myLatestDragEvent.getComponent();
    if (!component.isShowing()) {
      stop();
      return;
    }

    if (autoscroll()) {
      fireSyntheticDragEvent(e);
    }
  }

  private void fireSyntheticDragEvent(ActionEvent e) {
    Component component = myLatestDragEvent.getComponent();

    Point componentOnScreen = component.getLocationOnScreen();
    int xScreen = myLatestDragEvent.getXOnScreen();
    int yScreen = myLatestDragEvent.getYOnScreen();
    int x = xScreen - componentOnScreen.x;
    int y = yScreen - componentOnScreen.y;
    SyntheticDragEvent dragEvent = new SyntheticDragEvent(component,
                                                          myLatestDragEvent.getID(), e.getWhen(),
                                                          myLatestDragEvent.getModifiers(),
                                                          x, y, xScreen, yScreen,
                                                          myLatestDragEvent.getClickCount(),
                                                          myLatestDragEvent.isPopupTrigger(),
                                                          myLatestDragEvent.getButton());

    for (MouseMotionListener l : component.getMouseMotionListeners()) {
      l.mouseDragged(dragEvent);
    }
  }

  private boolean autoscroll() {
    JScrollPane scrollPane =
      ComponentUtil.getParentOfType((Class<? extends JScrollPane>)JScrollPane.class, myLatestDragEvent.getComponent());
    if (scrollPane == null) return false;

    boolean scrolled = scroll(scrollPane.getVerticalScrollBar(), myVerticalScrollDelta);
    scrolled |= scroll(scrollPane.getHorizontalScrollBar(), myHorizontalScrollDelta);
    return scrolled;
  }

  private boolean isRunningOn(@NotNull JComponent component) {
    return myLatestDragEvent != null && myLatestDragEvent.getComponent() == component;
  }

  private static boolean scroll(@Nullable JScrollBar scrollBar, int delta) {
    if (scrollBar == null || delta == 0) return false;

    int oldValue = scrollBar.getValue();
    scrollBar.setValue(scrollBar.getValue() + delta);
    return oldValue != scrollBar.getValue();
  }

  public interface ScrollDeltaProvider {
    int getHorizontalScrollDelta(MouseEvent e);
    int getVerticalScrollDelta(MouseEvent e);
  }

  public static class DefaultScrollDeltaProvider implements ScrollDeltaProvider {
    @Override
    public int getVerticalScrollDelta(MouseEvent e) {
      Rectangle visibleRect = ((JComponent)e.getComponent()).getVisibleRect();
      return getScrollDelta(visibleRect.y, visibleRect.y + visibleRect.height - 1, e.getY());
    }

    @Override
    public int getHorizontalScrollDelta(MouseEvent e) {
      Rectangle visibleRect = ((JComponent)e.getComponent()).getVisibleRect();
      return getScrollDelta(visibleRect.x, visibleRect.x + visibleRect.width - 1, e.getX());
    }

    protected int getScrollDelta(int low, int high, int value) {
      if (low > high) return low;
      return value - MathUtil.clamp(value, low, high);
    }
  }

  public static class AutoscrollLocker {
    private boolean locked;

    public boolean locked() {
      return locked;
    }

    public void runWithLock(Runnable runnable) {
      try {
        locked = true;
        runnable.run();
      } finally {
        locked = false;
      }
    }
  }

  private static class SyntheticDragEvent extends MouseEvent {
    SyntheticDragEvent(Component source, int id, long when, int modifiers,
                              int x, int y, int xAbs, int yAbs,
                              int clickCount, boolean popupTrigger, int button) {
      super(source, id, when, modifiers, x, y, xAbs, yAbs, clickCount, popupTrigger, button);
    }
  }

  // JTable's UI only updates cell editor location upon it's cell painting which doesn't occur if the cell becomes obscure.
  // Moving cell editor prevents it from being 'stuck' on autoscroll.
  private static class MoveTableCellEditorOnAutoscrollFix implements AdjustmentListener, PropertyChangeListener {
    private final JTable myTable;

    MoveTableCellEditorOnAutoscrollFix(JTable table) {
      myTable = table;

      JScrollPane scrollPane = ComponentUtil.getParentOfType((Class<? extends JScrollPane>)JScrollPane.class, (Component)myTable);
      assert scrollPane != null : "MoveTableCellEditorOnAutoscrollFix can only be applied to tables having a scrollpane as it's parent!";

      scrollPane.addPropertyChangeListener(this);
      addScrollBarListener(scrollPane.getHorizontalScrollBar());
      addScrollBarListener(scrollPane.getVerticalScrollBar());
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
      String propertyName = evt.getPropertyName();
      if ("horizontalScrollBar".equals(propertyName) || "verticalScrollBar".equals(propertyName)) {
        removeScrollBarListener(ObjectUtils.tryCast(evt.getOldValue(), JScrollBar.class));
        addScrollBarListener(ObjectUtils.tryCast(evt.getNewValue(), JScrollBar.class));
      }
    }

    private void addScrollBarListener(@Nullable JScrollBar to) {
      if (to != null) {
        to.addAdjustmentListener(this);
      }
    }

    private void removeScrollBarListener(@Nullable JScrollBar from) {
      if (from != null) {
        from.removeAdjustmentListener(this);
      }
    }

    @Override
    public void adjustmentValueChanged(AdjustmentEvent e) {
      moveCellEditor();
    }

    private void moveCellEditor() {
      int column = myTable.getEditingColumn();
      int row = myTable.getEditingRow();
      Component editor = myTable.getEditorComponent();
      if (column == -1 || row == -1 || editor == null) return;

      Rectangle cellRect = myTable.getCellRect(row, column, false);
      Rectangle visibleRect = myTable.getVisibleRect();
      if (visibleRect.intersects(cellRect)) return;

      Rectangle editorBounds = editor.getBounds();
      if (!visibleRect.intersects(editorBounds)) return;

      editorBounds.x = cellRect.x;
      editorBounds.y = cellRect.y;
      editor.setBounds(editorBounds);
    }
  }

  // Disabling swing autoscroll on a JTable leads to table not being scrolled on selection changes.
  // Particularly, scrollRectToVisible in javax.swing.JTable#changeSelection won't be called.
  private static class ScrollOnTableSelectionChangeFix implements ListSelectionListener, PropertyChangeListener {
    private final JTable myTable;
    private final AutoscrollLocker myLocker;

    ScrollOnTableSelectionChangeFix(JTable table, AutoscrollLocker locker) {
      myTable = table;
      myLocker = locker;

      myTable.addPropertyChangeListener("selectionModel", this);
      myTable.addPropertyChangeListener("columnModel", this);

      addSelectionListener(getRowSelectionModel());
      addSelectionListener(getColumnSelectionModel());
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
      if (e.getValueIsAdjusting() || ourInstance.isRunningOn(myTable) || locked()) return;

      int row = getLeadSelectionIndexIfSelectionIsNotEmpty(getRowSelectionModel());
      int col = getLeadSelectionIndexIfSelectionIsNotEmpty(getColumnSelectionModel());

      boolean validRow = (row >= 0 || myTable.getRowCount() == 0) && row < myTable.getRowCount();
      boolean validCol = (col >= 0 || myTable.getColumnCount() == 0) && col < myTable.getColumnCount();
      if (validRow && validCol) myTable.scrollRectToVisible(myTable.getCellRect(row, col, false));
    }

    private boolean locked() {
      return myLocker != null && myLocker.locked();
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
      ListSelectionModel oldSelectionModel = null;
      ListSelectionModel newSelectionModel = null;

      if ("selectionModel".equals(evt.getPropertyName())) {
        oldSelectionModel = (ListSelectionModel)evt.getOldValue();
        newSelectionModel = (ListSelectionModel)evt.getNewValue();
      }
      else if ("columnModel".equals(evt.getPropertyName())) {
        TableColumnModel oldColumnModel = (TableColumnModel)evt.getOldValue();
        oldSelectionModel = oldColumnModel != null ? oldColumnModel.getSelectionModel() : null;
        TableColumnModel newColumnModel = (TableColumnModel)evt.getNewValue();
        newSelectionModel = newColumnModel != null ? newColumnModel.getSelectionModel() : null;
      }

      removeSelectionListener(oldSelectionModel);
      addSelectionListener(newSelectionModel);
    }

    @Nullable
    private ListSelectionModel getRowSelectionModel() {
      return myTable.getSelectionModel();
    }

    @Nullable
    private ListSelectionModel getColumnSelectionModel() {
      return myTable.getColumnModel().getSelectionModel();
    }

    private void removeSelectionListener(@Nullable ListSelectionModel from) {
      if (from != null) {
        from.removeListSelectionListener(this);
      }
    }

    private void addSelectionListener(@Nullable ListSelectionModel to) {
      if (to != null) {
        to.addListSelectionListener(this);
      }
    }

    private static int getLeadSelectionIndexIfSelectionIsNotEmpty(ListSelectionModel lsm) {
      return lsm != null && !lsm.isSelectionEmpty() ? lsm.getLeadSelectionIndex() : -1;
    }
  }
}
