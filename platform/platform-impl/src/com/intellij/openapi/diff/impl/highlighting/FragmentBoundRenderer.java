/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.highlighting;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.markup.LineMarkerRenderer;
import com.intellij.openapi.editor.markup.LineSeparatorRenderer;
import com.intellij.openapi.util.Pair;
import com.intellij.util.Consumer;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.UIUtil;

import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * @author irengrig
 *         Date: 7/6/11
 *         Time: 7:44 PM
 */
public class FragmentBoundRenderer implements LineMarkerRenderer, LineSeparatorRenderer {
  private final int myLineHeight;
  private final Editor myEditor;
  private final Consumer<Integer> myOffsetsConsumer;
  private final ShoeneLine myShoeneLine;
  private final Color myMainColor;

  public FragmentBoundRenderer(int lineHeight, final Editor editor, final Consumer<Integer> offsetsConsumer) {
    myLineHeight = lineHeight;
    myEditor = editor;
    myOffsetsConsumer = offsetsConsumer;
    myShoeneLine = new ShoeneLine(2);
    myMainColor = darkerBorder();
  }

  public static Color darkerBorder() {
    final Color borderColor = UIUtil.getBorderColor();
    return new Color(borderColor.getRed() + 10, borderColor.getGreen() + 10, borderColor.getBlue() + 10);
  }
  // only top

  @Override
  public void paint(Editor editor, Graphics g, Rectangle r) {
    final Graphics gr = g.create();
    try {
      ((Graphics2D) gr).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      gr.setColor(getColor());
      int y = r.y;
      final int width = ((EditorEx)editor).getGutterComponentEx().getWidth();
      final int editorWidth = editor.getScrollingModel().getVisibleArea().width;
      myShoeneLine.ensureLastX(editorWidth + width + width);

      if (((EditorImpl) editor).isMirrored()) {
        // continue
        List<Pair<Integer, Integer>> points = myShoeneLine.getPoints();
        int i = 0;
        for (; i < points.size(); i++) {
          Pair<Integer, Integer> integerIntegerPair = points.get(i);
          if (integerIntegerPair.getFirst() - width >= editorWidth) {
            break;
          }
        }
        // take previous
        i = i == 0 ? 0 : i - 1;
        points = points.subList(i, points.size());

        drawCurved(gr, 0, r.y, TornLineParams.ourDark, points, width + editorWidth, true,width);
        gr.setColor(getColor().darker());
        drawCurved(gr, 0, r.y, TornLineParams.ourLight, points, width + editorWidth, true,width);

        int j = points.size() - 1;
        final int finalX = width + editorWidth + width;
        for (; j > 0; j--) {
          if (points.get(j).getFirst() >= finalX) break;
        }
        j = j == 0 ? 0 : j - 1;
        myOffsetsConsumer.consume(points.get(j).getSecond());

      } else {
        List<Pair<Integer, Integer>> points = myShoeneLine.getPoints();
        drawCurved(gr, 0, r.y, TornLineParams.ourDark, points, 0, false,0);
        gr.setColor(getColor().darker());
        drawCurved(gr, 0, r.y, TornLineParams.ourLight, points, 0, false,0);

        int i = getLastPointInBeforeGutter(width, points);
        myOffsetsConsumer.consume(points.get(i).getSecond());
      }
    } finally {
      gr.dispose();
    }
  }

  public Color getColor() {
    return myMainColor;
  }

  @Override
  public void drawLine(Graphics g, int x1, int x2, int y) {

    final int length = x2 - x1;
    if (length == 0) return;

    final int width = ((EditorEx) myEditor).getGutterComponentEx().getWidth();

    myShoeneLine.ensureLastX(length + width);

    final Graphics gr = g.create();

    try {
      ((Graphics2D) gr).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      gr.setColor(getColor());
      List<Pair<Integer, Integer>> points = myShoeneLine.getPoints();
      int i = getLastPointInBeforeGutter(width, points);
      points = points.subList(i, points.size());
      drawCurved(gr, x1, y, TornLineParams.ourDark, points, width, false,0);
      gr.setColor(getColor().darker());
      drawCurved(gr, x1, y, TornLineParams.ourLight, points, width, false,0);
    } finally {
      gr.dispose();
    }
  }

  private int getLastPointInBeforeGutter(int width, List<Pair<Integer, Integer>> points) {
    int i = 0;
    for (; i < points.size(); i++) {
      Pair<Integer, Integer> integerIntegerPair = points.get(i);
      if (integerIntegerPair.getFirst() >= width) break;
    }
    i = i == 0 ? 0 : i - 1;
    return i;
  }

  private void drawCurved(Graphics g,
                          final int x1,
                          int y,
                          final int offset,
                          final List<Pair<Integer, Integer>> points,
                          final int subtractX,
                          final boolean mirrorX, final int mirrorSize) {
    final Iterator<Pair<Integer,Integer>> iterator = points.iterator();
    assert iterator.hasNext();
    final Convertor<Integer, Integer> c = new Convertor<Integer, Integer>() {
      @Override
      public Integer convert(Integer o) {
        final int val = x1 + o - subtractX;
        if (mirrorX) {
          return mirrorSize - val;
        }
        return val;
      }
    };
    Pair<Integer, Integer> previous = iterator.next();
    while (iterator.hasNext()) {
      final Pair<Integer, Integer> next = iterator.next();
      UIUtil.drawLine(g, c.convert(previous.getFirst()), y + offset + previous.getSecond() - myLineHeight/2, c.convert(next.getFirst()),
                      y + offset + next.getSecond() - myLineHeight/2);
      UIUtil.drawLine(g, c.convert(previous.getFirst()), y - offset + previous.getSecond() - myLineHeight/2, c.convert(next.getFirst()),
                      y - offset + next.getSecond() - myLineHeight/2);
      previous = next;
    }
  }

  private static class ShoeneLine {
    private List<Pair<Integer, Integer>> myPoints;
    private final int myYDiff;

    private ShoeneLine(final int yDiff) {
      myYDiff = yDiff;
      myPoints = new ArrayList<Pair<Integer, Integer>>();
    }
    
    public void ensureLastX(final int x) {
      if (myPoints.isEmpty() || myPoints.get(myPoints.size() - 1).getFirst() < x) {
        if (myPoints.isEmpty()) {
          myPoints.add(new Pair<Integer, Integer>(0,0));
          myPoints.addAll(generateLine(0,0,x,0,myYDiff,0));
        } else {
          final Pair<Integer, Integer> lastPoint = myPoints.get(myPoints.size() - 1);
          int finalX = (x - lastPoint.getFirst()) < 5 ? x + 10 : x;
          myPoints.addAll(generateLine(lastPoint.getFirst(),lastPoint.getSecond(),finalX,0,myYDiff,lastPoint.getSecond()));
        }
        //myPoints.set(myPoints.size() - 1, new Pair<Integer, Integer>(x, 0));
      }
    }

    public List<Pair<Integer, Integer>> getPoints() {
      return myPoints;
    }
  }
  
  private final static int xVariation = 7;

  private static List<Pair<Integer, Integer>> generateLine(final int startX, int startY, int finalX, final int yBase, final int yDiff,
                                                           final int wasPrevStep) {
    int xCurrent = startX;
    int yCurrent = startY;
    
    final List<Pair<Integer, Integer>> result = new ArrayList<Pair<Integer, Integer>>();

    final Random xRnd = new Random();
    final Random yRnd = new Random();
    int prevStep = wasPrevStep;
    while (xCurrent < finalX) {
      int yStep = prevStep;
      while (prevStep == yStep) {
        yStep = yRnd.nextInt(yDiff * 2) - yDiff;
      }
      prevStep = yStep;
      int newY = yBase + yStep;
      int newX = xCurrent + 4 + xRnd.nextInt(xVariation);
      newX = Math.min(newX, finalX);
      
      result.add(new Pair<Integer, Integer>(newX, newY));
      xCurrent = newX;
    }

    return result;
  }
}
