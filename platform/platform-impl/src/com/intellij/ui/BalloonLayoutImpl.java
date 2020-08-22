// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.notification.EventLog;
import com.intellij.notification.Notification;
import com.intellij.notification.impl.NotificationCollector;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.impl.IdeRootPane;
import com.intellij.openapi.wm.impl.ToolWindowsPane;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.List;
import java.util.*;

public class BalloonLayoutImpl implements BalloonLayout, Disposable {
  private final ComponentAdapter myResizeListener = new ComponentAdapter() {
    @Override
    public void componentResized(@NotNull ComponentEvent e) {
      queueRelayout();
    }
  };

  protected JLayeredPane myLayeredPane;
  private final Insets myInsets;

  protected final List<Balloon> myBalloons = new ArrayList<>();
  private final Map<Balloon, BalloonLayoutData> myLayoutData = new HashMap<>();
  private GetInt myWidth;

  private final Alarm myRelayoutAlarm = new Alarm();
  private final Runnable myRelayoutRunnable = () -> {
    if (myLayeredPane == null) {
      return;
    }
    relayout();
    fireRelayout();
  };
  private JRootPane myParent;

  private final Runnable myCloseAll = () -> {
    for (Balloon balloon : new ArrayList<>(myBalloons)) {
      BalloonLayoutData layoutData = myLayoutData.get(balloon);
      NotificationCollector.getInstance().logNotificationBalloonClosedByUser(layoutData.id, layoutData.displayId, layoutData.groupId);
      remove(balloon, true);
    }
  };
  private final Runnable myLayoutRunnable = () -> {
    calculateSize();
    relayout();
    fireRelayout();
  };

  private final List<Runnable> myListeners = new ArrayList<>();

  public BalloonLayoutImpl(@NotNull JRootPane parent, @NotNull Insets insets) {
    myParent = parent;
    myLayeredPane = parent.getLayeredPane();
    myInsets = insets;
    myLayeredPane.addComponentListener(myResizeListener);
  }

  public void dispose() {
    myLayeredPane.removeComponentListener(myResizeListener);
    for (Balloon balloon : new ArrayList<>(myBalloons)) {
      Disposer.dispose(balloon);
    }
    myRelayoutAlarm.cancelAllRequests();
    myBalloons.clear();
    myLayoutData.clear();
    myListeners.clear();
    myLayeredPane = null;
    myParent = null;
  }

  public void addListener(Runnable listener) {
    myListeners.add(listener);
  }

  public void removeListener(Runnable listener) {
    myListeners.remove(listener);
  }

  private void fireRelayout() {
    for (Runnable listener : myListeners) {
      listener.run();
    }
  }

  @Nullable
  public Component getTopBalloonComponent() {
    BalloonImpl balloon = (BalloonImpl)ContainerUtil.getLastItem(myBalloons);
    return balloon == null ? null : balloon.getComponent();
  }

  @Override
  public void add(@NotNull Balloon balloon) {
    add(balloon, null);
  }

  @Override
  public void add(@NotNull final Balloon balloon, @Nullable Object layoutData) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    Balloon merge = merge(layoutData);
    if (merge == null) {
      if (!myBalloons.isEmpty() && myBalloons.size() == getVisibleCount()) {
        remove(myBalloons.get(0));
      }
      myBalloons.add(balloon);
    }
    else {
      int index = myBalloons.indexOf(merge);
      remove(merge);
      myBalloons.add(index, balloon);
    }
    if (layoutData instanceof BalloonLayoutData) {
      BalloonLayoutData balloonLayoutData = (BalloonLayoutData)layoutData;
      balloonLayoutData.closeAll = myCloseAll;
      balloonLayoutData.doLayout = myLayoutRunnable;
      myLayoutData.put(balloon, balloonLayoutData);
    }
    Disposer.register(balloon, new Disposable() {
      @Override
      public void dispose() {
        clearNMore(balloon);
        remove(balloon, false);
        queueRelayout();
      }
    });

    calculateSize();
    relayout();
    if (!balloon.isDisposed()) {
      balloon.show(myLayeredPane);
    }
    fireRelayout();
  }

  @Nullable
  private Balloon merge(@Nullable Object data) {
    String mergeId = null;
    if (data instanceof String) {
      mergeId = (String)data;
    }
    else if (data instanceof BalloonLayoutData) {
      mergeId = ((BalloonLayoutData)data).groupId;
    }
    if (mergeId != null) {
      for (Map.Entry<Balloon, BalloonLayoutData> e : myLayoutData.entrySet()) {
        if (mergeId.equals(e.getValue().groupId)) {
          return e.getKey();
        }
      }
    }
    return null;
  }

  @Nullable
  public BalloonLayoutData.MergeInfo preMerge(@NotNull Notification notification) {
    Balloon balloon = merge(notification.getGroupId());
    if (balloon != null) {
      BalloonLayoutData layoutData = myLayoutData.get(balloon);
      if (layoutData != null) {
        return layoutData.merge();
      }
    }
    return null;
  }

  public void remove(@NotNull Notification notification) {
    Balloon balloon = merge(notification.getGroupId());
    if (balloon != null) {
      remove(balloon, true);
    }
  }

  private void remove(@NotNull Balloon balloon) {
    remove(balloon, false);
    balloon.hide(true);
    fireRelayout();
  }

  private void clearNMore(@NotNull Balloon balloon) {
    BalloonLayoutData layoutData = myLayoutData.get(balloon);
    if (layoutData != null && layoutData.project != null && layoutData.mergeData != null) {
      EventLog.clearNMore(layoutData.project, Collections.singleton(layoutData.groupId));
    }
  }

  private void remove(@NotNull Balloon balloon, boolean hide) {
    myBalloons.remove(balloon);
    BalloonLayoutData layoutData = myLayoutData.remove(balloon);
    if (layoutData != null) {
      layoutData.mergeData = null;
    }
    if (hide) {
      balloon.hide();
      fireRelayout();
    }
  }

  public void closeAll() {
    myCloseAll.run();
  }

  public void closeFirst() {
    if (!myBalloons.isEmpty()) {
      remove(myBalloons.get(0), true);
    }
  }

  public int getBalloonCount() {
    return myBalloons.size();
  }

  private static int getVisibleCount() {
    return Registry.intValue("ide.notification.visible.count", 2);
  }

  @NotNull
  private Dimension getSize(@NotNull Balloon balloon) {
    BalloonLayoutData layoutData = myLayoutData.get(balloon);
    if (layoutData == null) {
      Dimension size = balloon.getPreferredSize();
      return myWidth == null ? size : new Dimension(myWidth.i(), size.height);
    }
    return new Dimension(myWidth.i(), layoutData.height);
  }

  public boolean isEmpty() {
    return myBalloons.isEmpty();
  }

  public void queueRelayout() {
    myRelayoutAlarm.cancelAllRequests();
    myRelayoutAlarm.addRequest(myRelayoutRunnable, 200);
  }

  private void calculateSize() {
    myWidth = null;

    for (Balloon balloon : myBalloons) {
      BalloonLayoutData layoutData = myLayoutData.get(balloon);
      if (layoutData != null) {
        layoutData.height = balloon.getPreferredSize().height;
      }
    }

    myWidth = BalloonLayoutConfiguration::FixedWidth;
  }

  private void relayout() {
    final Dimension size = myLayeredPane.getSize();

    JBInsets.removeFrom(size, myInsets);

    final Rectangle layoutRec = new Rectangle(new Point(myInsets.left, myInsets.top), size);

    List<ArrayList<Balloon>> columns = createColumns(layoutRec);
    while (columns.size() > 1) {
      remove(myBalloons.get(0), true);
      columns = createColumns(layoutRec);
    }

    ToolWindowsPane pane = UIUtil.findComponentOfType(myParent, ToolWindowsPane.class);
    JComponent layeredPane = pane != null ? pane.getLayeredPane() : null;
    int eachColumnX = (layeredPane == null ? myLayeredPane.getWidth() : layeredPane.getX() + layeredPane.getWidth()) - 4;

    doLayout(columns.get(0), eachColumnX + 4, (int)myLayeredPane.getBounds().getMaxY());
  }

  private void doLayout(List<Balloon> balloons, int startX, int bottomY) {
    int y = bottomY;
    ToolWindowsPane pane = UIUtil.findComponentOfType(myParent, ToolWindowsPane.class);
    if (pane != null) {
      y -= pane.getBottomHeight();
    }
    if (myParent instanceof IdeRootPane) {
      y -= ((IdeRootPane)myParent).getStatusBarHeight();
    }

    for (Balloon balloon : balloons) {
      Rectangle bounds = new Rectangle(getSize(balloon));
      y -= bounds.height;
      bounds.setLocation(startX - bounds.width, y);
      balloon.setBounds(bounds);
    }
  }

  private List<ArrayList<Balloon>> createColumns(Rectangle layoutRec) {
    List<ArrayList<Balloon>> columns = new ArrayList<>();

    ArrayList<Balloon> eachColumn = new ArrayList<>();
    columns.add(eachColumn);

    int eachColumnHeight = 0;
    for (Balloon each : myBalloons) {
      final Dimension eachSize = getSize(each);
      if (eachColumnHeight + eachSize.height > layoutRec.getHeight()) {
        eachColumn = new ArrayList<>();
        columns.add(eachColumn);
        eachColumnHeight = 0;
      }
      eachColumn.add(each);
      eachColumnHeight += eachSize.height;
    }
    return columns;
  }

  private interface GetInt {
    int i();
  }
}
