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
    DividerPoligon.paintPoligons(DividerPoligon.createVisiblePoligons(mySides, myLeftSide),
                                 (Graphics2D)g.create(0, height - editorHeight, width, editorHeight),
                                 width);
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
