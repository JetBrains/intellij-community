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

  protected Divider createDivider() {
    return new DividerImpl(){
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
