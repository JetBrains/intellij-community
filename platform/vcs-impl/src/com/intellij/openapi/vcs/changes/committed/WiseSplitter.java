// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public final class WiseSplitter implements Disposable {
  private static final Border LEFT_BORDER = IdeBorderFactory.createBorder(SideBorder.TOP | SideBorder.RIGHT);
  private static final Border MIDDLE_BORDER = IdeBorderFactory.createBorder(SideBorder.TOP | SideBorder.LEFT | SideBorder.RIGHT);

  private final Runnable myRefresher;
  private final Splitter myParentSplitter;
  private final ThreeComponentsSplitter myInnerSplitter;
  private final Map<CommittedChangesFilterKey, Integer> myInnerSplitterContents;

  public WiseSplitter(final Runnable refresher, final Splitter parentSplitter) {
    myRefresher = refresher;
    myParentSplitter = parentSplitter;

    myInnerSplitter = new ThreeComponentsSplitter(this);
    myInnerSplitter.setHonorComponentsMinimumSize(true);
    myInnerSplitterContents = new HashMap<>();
    updateBorders();
  }

  public boolean canAdd() {
    return myInnerSplitterContents.size() <= 3;
  }

  public void add(final CommittedChangesFilterKey key, final JComponent comp) {
    final int idx = myInnerSplitterContents.size();
    myInnerSplitterContents.put(key, idx);
    if (idx == 0) {
      myParentSplitter.setFirstComponent(myInnerSplitter);
      if (myParentSplitter.getProportion() < 0.05f) {
        myParentSplitter.setProportion(0.25f);
      }
      myInnerSplitter.setFirstComponent(comp);
      myInnerSplitter.setFirstSize((int)(myParentSplitter.getSize().getWidth() * myParentSplitter.getProportion()));
    }
    else if (idx == 1) {
      final Dimension dimension = myInnerSplitter.getSize();
      final double width = dimension.getWidth() / 2;
      myInnerSplitter.setInnerComponent(comp);
      myInnerSplitter.setFirstSize((int)width);
    }
    else {
      final Dimension dimension = myInnerSplitter.getSize();
      final double width = dimension.getWidth() / 3;
      myInnerSplitter.setLastComponent(comp);
      myInnerSplitter.setFirstSize((int)width);
      myInnerSplitter.setLastSize((int) width);
    }

    updateBorders();

    myRefresher.run();
  }

  private void updateBorders() {
    boolean isEmpty = myInnerSplitterContents.size() == 0;
    if (!isEmpty) {
      setBorder(myInnerSplitter.getFirstComponent(), true);
      setBorder(myInnerSplitter.getInnerComponent(), false);
    }
    setBorder(myParentSplitter.getSecondComponent(), isEmpty);
  }

  private static void setBorder(JComponent c, boolean leftMost) {
    if (c instanceof JScrollPane) {
      c.setBorder(leftMost ? LEFT_BORDER : MIDDLE_BORDER);
    }
  }

  public void remove(final CommittedChangesFilterKey key) {
    final Integer idx = myInnerSplitterContents.remove(key);
    if (idx == null) {
      return;
    }
    final Map<CommittedChangesFilterKey, Integer> tmp = new HashMap<>();
    for (Map.Entry<CommittedChangesFilterKey, Integer> entry : myInnerSplitterContents.entrySet()) {
      if (entry.getValue() < idx) {
        tmp.put(entry.getKey(), entry.getValue());
      } else {
        tmp.put(entry.getKey(), entry.getValue() - 1);
      }
    }
    myInnerSplitterContents.clear();
    myInnerSplitterContents.putAll(tmp);

    if (idx == 0) {
      final JComponent inner = myInnerSplitter.getInnerComponent();
      myInnerSplitter.setInnerComponent(null);
      myInnerSplitter.setFirstComponent(inner);
      lastToInner();
    } else if (idx == 1) {
      lastToInner();
    } else {
      myInnerSplitter.setLastComponent(null);
    }

    updateBorders();

    myRefresher.run();
  }

  private void lastToInner() {
    final JComponent last = myInnerSplitter.getLastComponent();
    myInnerSplitter.setLastComponent(null);
    myInnerSplitter.setInnerComponent(last);
  }

  @Override
  public void dispose() {
  }
}
