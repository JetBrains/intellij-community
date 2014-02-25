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

import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.graph.PaintInfo;
import com.intellij.vcs.log.newgraph.gpaph.MutableGraph;
import com.intellij.vcs.log.newgraph.gpaph.ThickHoverController;
import com.intellij.vcs.log.newgraph.render.cell.GraphCell;
import com.intellij.vcs.log.newgraph.render.cell.GraphCellGeneratorImpl;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.image.BufferedImage;

import static com.intellij.vcs.log.graph.render.PrintParameters.HEIGHT_CELL;
import static com.intellij.vcs.log.graph.render.PrintParameters.WIDTH_NODE;

public class GraphRender {
  private boolean longEdgesHidden = true;

  @NotNull
  private final GraphCellGeneratorImpl myCellGenerator;

  @NotNull
  private final ThickHoverController myHoverController;

  @NotNull
  private final GraphCellPainter myCellPainter;

  public GraphRender(@NotNull MutableGraph mutableGraph,
                     @NotNull ThickHoverController hoverController,
                     @NotNull ElementColorManager colorManager) {
    myHoverController = hoverController;
    myCellGenerator = new GraphCellGeneratorImpl(mutableGraph);
    myCellPainter = new SimpleGraphCellPainter(myHoverController, colorManager);
  }

  @NotNull
  public PaintInfo paint(int visibleRowIndex) {
    GraphCell graphCell = myCellGenerator.getGraphCell(visibleRowIndex);

    int imageWidth = calcImageWidth(graphCell);
    int bufferWidth = imageWidth + 40;
    BufferedImage image = UIUtil.createImage(bufferWidth, HEIGHT_CELL, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2 = image.createGraphics();
    myCellPainter.draw(g2, graphCell);
    return new PaintInfoImpl(image, imageWidth);
  }

  private static int calcImageWidth(@NotNull GraphCell cell) {
    return cell.getCountElements() * WIDTH_NODE;
  }

  public boolean areLongEdgesHidden() {
    return longEdgesHidden;
  }

  public void setLongEdgesHidden(boolean longEdgesHidden) {
    this.longEdgesHidden = longEdgesHidden;
    myCellGenerator.setShowLongEdges(!longEdgesHidden);
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
