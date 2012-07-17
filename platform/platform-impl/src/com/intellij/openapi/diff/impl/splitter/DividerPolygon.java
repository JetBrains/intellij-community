/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.diff.impl.util.TextDiffType;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.ui.UIUtil;

import java.awt.*;
import java.util.ArrayList;

/**
 * A polygon, which is drawn between editors in merge or diff dialogs, and which indicates the change flow from one editor to another.
 */
public class DividerPolygon {
  public static final int TRANSPARENCY = 150;
  public static final Color FRAMING_LINE_COLOR = Color.LIGHT_GRAY;

  private final Color myColor;
  private final int myStart1;
  private final int myStart2;
  private final int myEnd1;
  private final int myEnd2;
  private final boolean myApplied;

  public DividerPolygon(int start1, int start2, int end1, int end2, Color color, boolean applied) {
    myApplied = applied;
    myStart1 = advance(start1);
    myStart2 = advance(start2);
    myEnd1 = advance(end1);
    myEnd2 = advance(end2);
    myColor = color;
  }

  private int advance(int y) {
    return y == 0 ? y : y + 1;
  }

  private void paint(Graphics2D g, int width) {
    if (!myApplied) {
      g.setColor(myColor);
      g.fill(new Polygon(new int[]{0, 0, width, width}, new int[]{myStart1, myEnd1, myEnd2, myStart2}, 4));
      g.setColor(FRAMING_LINE_COLOR);
      UIUtil.drawLine(g, 0, myStart1, width, myStart2);
      UIUtil.drawLine(g, 0, myEnd1, width, myEnd2);
    }
    else {
      g.setColor(myColor);
      UIUtil.drawLine(g, 0, myStart1 + 1, width, myStart2 + 1);
      UIUtil.drawLine(g, 0, myStart1 + 2, width, myStart2 + 2);
      UIUtil.drawLine(g, 0, myEnd1 + 1, width, myEnd2 + 1);
      UIUtil.drawLine(g, 0, myEnd1, width, myEnd2);
    }
  }

  public int hashCode() {
    return myStart1 ^ myStart2 ^ myEnd1 ^ myEnd2;
  }

  public boolean equals(Object obj) {
    if (!(obj instanceof DividerPolygon)) return false;
    DividerPolygon other = (DividerPolygon)obj;
    return myStart1 == other.myStart1 &&
           myStart2 == other.myStart2 &&
           myEnd1 == other.myEnd1 &&
           myEnd2 == other.myEnd2 && Comparing.equal(myColor, other.myColor);
  }

  public String toString() {
    return "<" + myStart1 + ", " + myEnd1 + " : " + myStart2 + ", " + myEnd2 + "> " + myColor;
  }

  public Color getColor() {
    return myColor;
  }

  public int getTopLeftY() {
    return myStart1;
  }

  public int getTopRightY() {
    return myStart2;
  }

  public int getBottomLeftY() {
    return myEnd1;
  }

  public int getBottomRightY() {
    return myEnd2;
  }

  public boolean isApplied() {
    return myApplied;
  }

  public static void paintPolygons(ArrayList<DividerPolygon> polygons, Graphics2D g, int width) {
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    //Composite composite = g.getComposite();
    //g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, 0.4f));
    for (DividerPolygon polygon : polygons) {
      polygon.paint(g, width);
    }
    //g.setComposite(composite);
  }

  public static ArrayList<DividerPolygon> createVisiblePolygons(EditingSides sides, FragmentSide left, int diffDividerPolygonsOffset) {
    Editor editor1 = sides.getEditor(left);
    Editor editor2 = sides.getEditor(left.otherSide());
    LineBlocks lineBlocks = sides.getLineBlocks();
    Trapezium visibleArea = new Trapezium(getVisibleInterval(editor1),
                                          getVisibleInterval(editor2));
    Interval indices = lineBlocks.getVisibleIndices(visibleArea);
    Transformation[] transformations = new Transformation[]{getTransformation(editor1),
      getTransformation(editor2)};
    ArrayList<DividerPolygon> polygons = new ArrayList<DividerPolygon>();
    for (int i = indices.getStart(); i < indices.getEnd(); i++) {
      Trapezium trapezium = lineBlocks.getTrapezium(i);
      final TextDiffType type = lineBlocks.getType(i);
      Color color = type.getPolygonColor(editor1);
      polygons.add(createPolygon(transformations, trapezium, color, left, diffDividerPolygonsOffset, type.isApplied()));
    }
    return polygons;
  }

  private static Transformation getTransformation(Editor editor) {
//    return new LinearTransformation(editor.getScrollingModel().getVerticalScrollOffset(), editor.getLineHeight());
    return new FoldingTransformation(editor);
  }

  private static DividerPolygon createPolygon(Transformation[] transformations, Trapezium trapezium, Color color, FragmentSide left,
                                              int diffDividerPolygonsOffset, boolean applied) {
    Interval base1 = trapezium.getBase(left);
    Interval base2 = trapezium.getBase(left.otherSide());
    Transformation leftTransform = transformations[left.getIndex()];
    Transformation rightTransform = transformations[left.otherSide().getIndex()];
    int start1 = leftTransform.transform(base1.getStart());
    int end1 = leftTransform.transform(base1.getEnd());
    int start2 = rightTransform.transform(base2.getStart());
    int end2 = rightTransform.transform(base2.getEnd());
    return new DividerPolygon(start1 - diffDividerPolygonsOffset, start2 - diffDividerPolygonsOffset,
                              end1 - diffDividerPolygonsOffset, end2 - diffDividerPolygonsOffset,
                              color, applied);
  }

  static Interval getVisibleInterval(Editor editor) {
    int offset = editor.getScrollingModel().getVerticalScrollOffset();
    LogicalPosition logicalPosition = editor.xyToLogicalPosition(new Point(0, offset));
    int line = logicalPosition.line;
    return new Interval(line, editor.getComponent().getHeight() / editor.getLineHeight() + 1);
  }
}
