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
package com.intellij.openapi.diff.impl.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.EditingSides;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.splitter.DiffDividerPaint;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class DiffDivider extends JComponent {
  public static final int MERGE_DIVIDER_POLYGONS_OFFSET = 1;
  public static final int MERGE_SCROLL_DIVIDER_POLYGONS_OFFSET = 2;
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.util.DiffDivider");
  private final Editor[] myEditors = new Editor[2];
  private final FragmentSide myLeftSide;
  private DiffDividerPaint myPaint = null;
  private static final int ourWidth = 30;

  private final VisibleAreaListener myVisibleAreaListener = new VisibleAreaListener() {
    public void visibleAreaChanged(VisibleAreaEvent e) {
      repaint();
    }
  };

  public DiffDivider(FragmentSide leftSide) {
    myLeftSide = leftSide;
  }

  public Dimension getPreferredSize() {
    return new Dimension(ourWidth, 1);
  }

  public void paint(Graphics g) {
    super.paint(g);
    if (myPaint != null) myPaint.paint(g, this);
  }

  public DiffDividerPaint getPaint() {
    return myPaint;
  }

  public void stopListenEditors() {
    for (int i = 0; i < myEditors.length; i++) {
      Editor editor = myEditors[i];
      if (editor != null) {
        editor.getScrollingModel().removeVisibleAreaListener(myVisibleAreaListener);
        myEditors[i] = null;
      }
    }
    myPaint = null;
    myEditors[0] = null;
    myEditors[1] = null;
  }

  public void listenEditors(@NotNull EditingSides sides) {
    stopListenEditors();
    myPaint = new DiffDividerPaint(sides, myLeftSide, MERGE_DIVIDER_POLYGONS_OFFSET);
    myEditors[0] = sides.getEditor(FragmentSide.SIDE1);
    myEditors[1] = sides.getEditor(FragmentSide.SIDE2);
    if (myEditors[0] == null || myEditors[1] == null) {
      LOG.error(myEditors[1] + " " + myEditors[1]);
    }
    for (Editor editor : myEditors) {
      editor.getScrollingModel().addVisibleAreaListener(myVisibleAreaListener);
    }
  }
}
