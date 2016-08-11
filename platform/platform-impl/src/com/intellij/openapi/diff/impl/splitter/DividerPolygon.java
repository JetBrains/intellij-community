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

import com.intellij.openapi.diff.impl.DiffUtil;
import com.intellij.openapi.diff.impl.EditingSides;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.util.TextDiffType;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.ui.GraphicsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Path2D;
import java.util.ArrayList;

/**
 * A polygon, which is drawn between editors in merge or diff dialogs, and which indicates the change flow from one editor to another.
 */
public class DividerPolygon {

  @Nullable private final Color myColor;
  private final int myStart1;
  private final int myStart2;
  private final int myEnd1;
  private final int myEnd2;
  private final boolean myApplied;

  public static final double CTRL_PROXIMITY_X = 0.3;

  public DividerPolygon(int start1, int start2, int end1, int end2, @Nullable Color color, boolean applied) {
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
    GraphicsUtil.setupAntialiasing(g);


    if (!myApplied) {
      Shape upperCurve = makeCurve(width, myStart1, myStart2, true);
      Shape lowerCurve = makeCurve(width, myEnd1, myEnd2, false);

      Path2D path = new Path2D.Double();
      path.append(upperCurve, true);
      path.append(lowerCurve, true);
      g.setColor(myColor);
      g.fill(path);

      g.setColor(DiffUtil.getFramingColor(myColor));
      g.draw(upperCurve);
      g.draw(lowerCurve);
    }
    else {
      g.setColor(myColor);
      g.draw(makeCurve(width, myStart1 + 1, myStart2 + 1, true));
      g.draw(makeCurve(width, myStart1 + 2, myStart2 + 2, true));
      g.draw(makeCurve(width, myEnd1 + 1, myEnd2 + 1, false));
      g.draw(makeCurve(width, myEnd1 + 2, myEnd2 + 2, false));
    }
  }

  private static Shape makeCurve(int width, int y1, int y2, boolean forward) {
    if (forward) {
      return new CubicCurve2D.Double(0, y1,
                                     width * CTRL_PROXIMITY_X, y1,
                                     width * (1.0 - CTRL_PROXIMITY_X), y2,
                                     width, y2);
    }
    else {
      return new CubicCurve2D.Double(width, y2,
                                     width * (1.0 - CTRL_PROXIMITY_X), y2,
                                     width * CTRL_PROXIMITY_X, y1,
                                     0, y1);
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

  public static ArrayList<DividerPolygon> createVisiblePolygons(@NotNull EditingSides sides,
                                                                @NotNull FragmentSide left,
                                                                int diffDividerPolygonsOffset) {
    Editor editor1 = sides.getEditor(left);
    Editor editor2 = sides.getEditor(left.otherSide());
    LineBlocks lineBlocks = sides.getLineBlocks();
    Trapezium visibleArea = new Trapezium(getVisibleInterval(editor1),
                                          getVisibleInterval(editor2));
    Interval indices = lineBlocks.getVisibleIndices(visibleArea);
    Transformation[] transformations = new Transformation[]{getTransformation(editor1),
      getTransformation(editor2)};
    ArrayList<DividerPolygon> polygons = new ArrayList<>();
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

  private static DividerPolygon createPolygon(@NotNull Transformation[] transformations,
                                              @NotNull Trapezium trapezium,
                                              @Nullable Color color,
                                              @NotNull FragmentSide left,
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
