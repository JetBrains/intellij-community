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
package com.intellij.vcs.log.newgraph.render;

import com.intellij.openapi.util.Pair;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.graph.PaintInfo;
import com.intellij.vcs.log.newgraph.gpaph.MutableGraph;
import com.intellij.vcs.log.newgraph.render.cell.GraphCell;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.image.BufferedImage;

import static com.intellij.vcs.log.graph.render.PrintParameters.HEIGHT_CELL;
import static com.intellij.vcs.log.graph.render.PrintParameters.WIDTH_NODE;

public class GraphRender {

  @NotNull
  private final GraphCellGenerator myCellGenerator;

  @NotNull
  private final GraphCellPainter myCellPainter;

  public GraphRender(@NotNull MutableGraph mutableGraph, @NotNull ElementColorManager colorManager, @NotNull GraphCellGenerator cellGenerator) {
    myCellGenerator = cellGenerator;
    myCellPainter = new SimpleGraphCellPainter(mutableGraph.getThickHoverController(), colorManager);
  }

  @NotNull
  public PaintInfo paint(int visibleRowIndex) {
    Pair<Integer, Integer> imageAndBufferImageWidth = getImageAndBufferImageWidth(visibleRowIndex);
    BufferedImage image = UIUtil.createImage(imageAndBufferImageWidth.getSecond(), HEIGHT_CELL, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2 = image.createGraphics();

    GraphCell graphCell = myCellGenerator.getGraphCell(visibleRowIndex);
    myCellPainter.draw(g2, graphCell);

    return new PaintInfoImpl(image, imageAndBufferImageWidth.getFirst());
  }

  public boolean isShowLongEdges() {
    return myCellGenerator.isShowLongEdges();
  }

  public void setShowLongEdges(boolean longEdgesHidden) {
    myCellGenerator.setShowLongEdges(longEdgesHidden);
  }

  public void invalidate() {
    myCellGenerator.invalidate();
  }

  @NotNull
  public GraphCellGenerator getCellGenerator() {
    return myCellGenerator;
  }

  @NotNull
  public GraphCellPainter getCellPainter() {
    return myCellPainter;
  }

  @NotNull
  private Pair<Integer, Integer> getImageAndBufferImageWidth(int visibleRowIndex) {
    int imageWidth = calcImageWidth(visibleRowIndex);
    int bufferWidth = calcMaxImageWith(visibleRowIndex);

    if (bufferWidth > imageWidth + 30) {
      imageWidth += (bufferWidth - imageWidth) / 4;
    }
    return new Pair<Integer, Integer>(imageWidth, bufferWidth);
  }

  private int calcImageWidth(int visibleRowIndex) {
    return myCellGenerator.getGraphCell(visibleRowIndex).getCountElements() * WIDTH_NODE;
  }

  private int calcMaxImageWith(int visibleRowIndex) {
    int maxElementsCount = myCellGenerator.getGraphCell(visibleRowIndex).getCountElements();
    if (visibleRowIndex > 0) {
      maxElementsCount = Math.max(maxElementsCount, myCellGenerator.getGraphCell(visibleRowIndex - 1).getCountElements());
    }
    if (visibleRowIndex < myCellGenerator.getCountVisibleRow() - 1) {
      maxElementsCount = Math.max(maxElementsCount, myCellGenerator.getGraphCell(visibleRowIndex + 1).getCountElements());
    }
    return WIDTH_NODE * maxElementsCount;
  }

  private static class PaintInfoImpl implements PaintInfo {

    private final Image myImage;
    private final int myWidth;

    public PaintInfoImpl(Image image, int width) {
      myImage = image;
      myWidth = width;
    }

    @NotNull
    @Override
    public Image getImage() {
      return myImage;
    }

    @Override
    public int getWidth() {
      return myWidth;
    }
  }
}
