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
package com.intellij.openapi.diff.impl.splitter;

import com.intellij.openapi.diff.impl.EditingSides;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;

import javax.swing.*;
import java.awt.*;

public class DiffDividerPaint {
  private final EditingSides mySides;
  private final FragmentSide myLeftSide;

  public DiffDividerPaint(EditingSides sides, FragmentSide leftSide) {
    mySides = sides;
    myLeftSide = leftSide;
  }

  public void paint(Graphics g, JComponent component) {
    if (!hasAllEditors()) return;
    int width = component.getWidth();
    int height = component.getHeight();
    int editorHeight = mySides.getEditor(myLeftSide).getComponent().getHeight();
    Graphics2D gg = (Graphics2D)g.create(0, height - editorHeight, width, editorHeight);
    DividerPoligon.paintPoligons(DividerPoligon.createVisiblePoligons(mySides, myLeftSide), gg, width);
    gg.dispose();
  }

  public EditingSides getSides() {
    return mySides;
  }

  public FragmentSide getLeftSide() {
    return myLeftSide;
  }

  private boolean hasAllEditors() {
    return mySides.getEditor(FragmentSide.SIDE1) != null && mySides.getEditor(FragmentSide.SIDE2) != null;
  }
}
