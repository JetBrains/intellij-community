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
package com.intellij.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.List;

public class BalloonLayoutImpl implements BalloonLayout {

  private final JLayeredPane myParent;
  private final Insets myInsets;

  private final List<Balloon> myBalloons = new ArrayList<Balloon>();

  private final Alarm myRelayoutAlarm = new Alarm();
  private final Runnable myRelayoutRunnable = new Runnable() {
    public void run() {
      relayout();
    }
  };

  public BalloonLayoutImpl(@NotNull JLayeredPane parent, @NotNull Insets insets) {
    myParent = parent;
    myInsets = insets;
    myParent.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        queueRelayout();
      }
    });
  }

  @Override
  public void add(final Balloon balloon) {
    myBalloons.add(balloon);
    Disposer.register(balloon, new Disposable() {
      public void dispose() {
        myBalloons.remove(balloon);
        queueRelayout();
      }
    });

    relayout();
    balloon.show(myParent);
  }


  private void queueRelayout() {
    myRelayoutAlarm.cancelAllRequests();
    myRelayoutAlarm.addRequest(myRelayoutRunnable, 200);
  }

  private void relayout() {
    final Dimension size = myParent.getSize();

    size.width -= myInsets.left + myInsets.right;
    size.height -= myInsets.top + myInsets.bottom;

    final Rectangle layoutRec = new Rectangle(new Point(myInsets.left, myInsets.top), size);

    List<ArrayList<Balloon>> columns = createColumns(layoutRec);
    while (columns.size() > 1) {
      myBalloons.remove(0);
      columns = createColumns(layoutRec);
    }
    List<Integer> columnWidths = computeWidths(columns);

    int eachCoumnX = (int)layoutRec.getMaxX(); 
    for (int i = 0; i < columns.size(); i++) {
      final ArrayList<Balloon> eachColumn = columns.get(i);
      final Integer eachWidth = columnWidths.get(i);
      eachCoumnX -= eachWidth.intValue();
      int eachY = layoutRec.y;
      for (Balloon eachBalloon : eachColumn) {
        final Rectangle eachRec = new Rectangle();
        final Dimension eachPrefSize = eachBalloon.getPreferredSize();
        eachRec.setSize(eachPrefSize);
        eachRec.setLocation(eachCoumnX + eachWidth.intValue() - eachRec.width, eachY);
        eachBalloon.setBounds(eachRec);
        eachY += eachRec.height;
      }
    }
  }



  private static List<Integer> computeWidths(List<ArrayList<Balloon>> columns) {
    List<Integer> columnWidths = new ArrayList<Integer>();
    for (ArrayList<Balloon> eachColumn : columns) {
      int maxWidth = 0;
      for (Balloon each : eachColumn) {
        maxWidth = Math.max(each.getPreferredSize().width, maxWidth);
      }
      columnWidths.add(maxWidth);
    }
    return columnWidths;
  }

  private List<ArrayList<Balloon>> createColumns(Rectangle layoutRec) {
    List<ArrayList<Balloon>> columns = new ArrayList<ArrayList<Balloon>>();

    ArrayList<Balloon> eachColumn = new ArrayList<Balloon>();
    columns.add(eachColumn);

    int eachColumnHeight = 0;
    for (Balloon each : myBalloons) {
      final Dimension eachSize = each.getPreferredSize();
      if (eachColumnHeight + eachSize.height > layoutRec.getHeight()) {
        eachColumn = new ArrayList<Balloon>();
        columns.add(eachColumn);
        eachColumnHeight = 0;
      }
      eachColumn.add(each);
      eachColumnHeight += eachSize.height;
    }
    return columns;
  }

}
