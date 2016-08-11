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
package com.intellij.ui;

import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.notification.EventLog;
import com.intellij.notification.Notification;
import com.intellij.notification.impl.NotificationsManagerImpl;
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
import java.util.*;
import java.util.List;

public class BalloonLayoutImpl implements BalloonLayout {
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
  private Integer myWidth;

  private final Alarm myRelayoutAlarm = new Alarm();
  private final Runnable myRelayoutRunnable = () -> {
    relayout();
    fireRelayout();
  };
  private JRootPane myParent;

  private final Runnable myCloseAll = () -> {
    for (Balloon balloon : new ArrayList<>(myBalloons)) {
      remove(balloon, true);
    }
  };
  private final Runnable myLayoutRunnable = () -> {
    calculateSize();
    relayout();
    fireRelayout();
  };

  private LafManagerListener myLafListener;

  private final List<Runnable> myListeners = new ArrayList<>();

  public BalloonLayoutImpl(@NotNull JRootPane parent, @NotNull Insets insets) {
    myParent = parent;
    myLayeredPane = parent.getLayeredPane();
    myInsets = insets;
    myLayeredPane.addComponentListener(myResizeListener);
  }

  public void dispose() {
    myLayeredPane.removeComponentListener(myResizeListener);
    if (myLafListener != null) {
      LafManager.getInstance().removeLafManagerListener(myLafListener);
      myLafListener = null;
    }
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
      if (NotificationsManagerImpl.newEnabled() &&
          getVisibleCount() > 0 &&
          layoutData instanceof BalloonLayoutData &&
          ((BalloonLayoutData)layoutData).groupId != null) {
        int index = -1;
        int count = 0;
        for (int i = 0, size = myBalloons.size(); i < size; i++) {
          BalloonLayoutData ld = myLayoutData.get(myBalloons.get(i));
          if (ld != null && ld.groupId != null) {
            if (index == -1) {
              index = i;
            }
            count++;
          }
        }

        if (count > 0 && count == getVisibleCount()) {
          remove(myBalloons.get(index));
        }
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
      public void dispose() {
        clearNMore(balloon);
        remove(balloon, false);
        queueRelayout();
      }
    });

    if (myLafListener == null && layoutData != null) {
      myLafListener = new LafManagerListener() {
        @Override
        public void lookAndFeelChanged(LafManager source) {
          for (BalloonLayoutData layoutData : myLayoutData.values()) {
            if (layoutData.lafHandler != null) {
              layoutData.lafHandler.run();
            }
          }
        }
      };
      LafManager.getInstance().addLafManagerListener(myLafListener);
    }

    calculateSize();
    relayout();
    ((BalloonImpl)balloon).traceDispose(false);
    balloon.show(myLayeredPane);
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
    if (layoutData != null && layoutData.mergeData != null) {
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

  private static int getVisibleCount() {
    return Registry.intValue("ide.new.notification.visible.count", 2);
  }

  @NotNull
  private Dimension getSize(@NotNull Balloon balloon) {
    BalloonLayoutData layoutData = myLayoutData.get(balloon);
    if (layoutData == null) {
      Dimension size = balloon.getPreferredSize();
      return myWidth == null ? size : new Dimension(myWidth, size.height);
    }
    return new Dimension(myWidth, layoutData.height);
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
    if (myLayoutData.isEmpty() && !NotificationsManagerImpl.newEnabled()) {
      return;
    }

    for (Balloon balloon : myBalloons) {
      BalloonLayoutData layoutData = myLayoutData.get(balloon);
      if (layoutData != null) {
        layoutData.height = balloon.getPreferredSize().height;
      }
    }

    myWidth = BalloonLayoutConfiguration.FixedWidth;
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
    List<Integer> columnWidths = computeWidths(columns);

    ToolWindowsPane pane = UIUtil.findComponentOfType(myParent, ToolWindowsPane.class);
    JComponent component = pane != null ? pane : myParent;
    int paneOnScreen = component.isShowing() ? component.getLocationOnScreen().y : 0;
    int layerOnScreen = myLayeredPane.isShowing() ? myLayeredPane.getLocationOnScreen().y : 0;
    int toolbarsOffset = paneOnScreen - layerOnScreen;

    JComponent layeredPane = pane != null ? pane.getMyLayeredPane() : null;
    int eachColumnX = (layeredPane == null ? myLayeredPane.getWidth() : layeredPane.getX() + layeredPane.getWidth()) - 4;

    if (NotificationsManagerImpl.newEnabled()) {
      newLayout(columns.get(0), eachColumnX + 4, (int)myLayeredPane.getBounds().getMaxY());
      return;
    }

    if (myLayoutData.isEmpty()) {
      for (int i = 0; i < columns.size(); i++) {
        final ArrayList<Balloon> eachColumn = columns.get(i);
        final Integer eachWidth = columnWidths.get(i);
        eachColumnX -= eachWidth.intValue();
        int eachY = toolbarsOffset + 2;
        for (Balloon eachBalloon : eachColumn) {
          final Rectangle eachRec = new Rectangle();
          eachRec.setSize(getSize(eachBalloon));
          if (((BalloonImpl)eachBalloon).hasShadow()) {
            final Insets shadow = ((BalloonImpl)eachBalloon).getShadowBorderInsets();
            eachRec.width += shadow.left + shadow.right;
            eachRec.height += shadow.top + shadow.bottom;
          }
          eachY += 2; // space between two notifications
          eachRec.setLocation(eachColumnX + eachWidth.intValue() - eachRec.width, eachY);
          eachBalloon.setBounds(eachRec);
          eachY += eachRec.height;
        }
      }
    }
    else {
      for (int i = 0; i < columns.size(); i++) {
        final ArrayList<Balloon> eachColumn = columns.get(i);
        final Integer eachWidth = columnWidths.get(i);
        int eachY = toolbarsOffset;
        int columnSize = eachColumn.size();

        if (columnSize > 0 && !((BalloonImpl)eachColumn.get(0)).hasShadow()) {
          eachY += 4;
        }
        eachColumnX -= eachWidth.intValue();

        boolean addShadow = false;
        for (Balloon balloon : eachColumn) {
          if (myLayoutData.get(balloon) == null) {
            addShadow = true;
            break;
          }
        }

        for (int j = 0; j < columnSize; j++) {
          BalloonImpl eachBalloon = (BalloonImpl)eachColumn.get(j);
          Rectangle eachRec = new Rectangle(getSize(eachBalloon));
          eachRec.setLocation(eachColumnX + eachWidth.intValue() - eachRec.width, eachY);

          boolean hasShadow = eachBalloon.hasShadow();
          Insets shadow = hasShadow ? eachBalloon.getShadowBorderInsets() : null;

          if (addShadow && hasShadow) {
            eachRec.width += shadow.left + shadow.right;
            eachRec.x -= shadow.left;
          }

          eachBalloon.setBounds(eachRec);
          eachY += eachRec.height;

          // space between two notifications
          if (j + 1 < columnSize) {
            BalloonImpl next = (BalloonImpl)eachColumn.get(j + 1);
            boolean hasNextShadow = next.hasShadow();
            int space = BalloonLayoutConfiguration.NotificationSpace;

            if (hasShadow == hasNextShadow) {
              if (hasShadow) {
                eachY += space - shadow.top - shadow.bottom;
              }
              else {
                eachY += space;
              }
            }
            else if (hasShadow) {
              eachY += space - shadow.top;
            }
            else {
              eachY += space - next.getShadowBorderInsets().bottom;
            }
          }
        }
      }
    }
  }

  private void newLayout(List<Balloon> balloons, int startX, int bottomY) {
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

  private List<Integer> computeWidths(List<ArrayList<Balloon>> columns) {
    List<Integer> columnWidths = new ArrayList<>();
    for (ArrayList<Balloon> eachColumn : columns) {
      int maxWidth = 0;
      for (Balloon each : eachColumn) {
        maxWidth = Math.max(getSize(each).width, maxWidth);
      }
      columnWidths.add(maxWidth);
    }
    return columnWidths;
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
}
