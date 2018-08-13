// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl;

import com.intellij.openapi.diff.impl.highlighting.DiffPanelState;
import com.intellij.openapi.diff.impl.splitter.DiffDividerPaint;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.ui.Divider;
import com.intellij.openapi.ui.Splitter;

import javax.swing.*;
import java.awt.*;

class DiffSplitter extends Splitter implements DiffSplitterI {
  private final DiffDividerPaint myPaint;
  private final DiffPanelState myData;

  private final VisibleAreaListener myVisibleAreaListener = new VisibleAreaListener() {
        @Override
        public void visibleAreaChanged(VisibleAreaEvent e) {
          redrawDiffs();
        }
      };

  public DiffSplitter(JComponent component1, JComponent component2, DiffDividerPaint dividerPaint, DiffPanelState data) {
    myPaint = dividerPaint;
    myData = data;
    setDividerWidth(30);
    setFirstComponent(component1);
    setSecondComponent(component2);
    setHonorComponentsMinimumSize(false);
  }

  @Override
  protected Divider createDivider() {
    return new DividerImpl(){
      @Override
      public void paint(Graphics g) {
        super.paint(g);
        myPaint.paint(g, this);
        myData.drawOnDivider(g, this);
      }
    };
  }

  @Override
  public void redrawDiffs() {
    getDivider().repaint();
  }

  @Override
  public VisibleAreaListener getVisibleAreaListener() {
    return myVisibleAreaListener;
  }

  @Override
  public JComponent getComponent() {
    return this;
  }
}
