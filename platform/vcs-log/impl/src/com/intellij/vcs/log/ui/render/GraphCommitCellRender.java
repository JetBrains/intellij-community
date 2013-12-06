/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.vcs.log.ui.render;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.data.VcsLogDataHolder;
import com.intellij.vcs.log.graph.render.GraphCellPainter;
import com.intellij.vcs.log.graph.render.GraphCommitCell;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Collection;

import static com.intellij.vcs.log.graph.render.PrintParameters.HEIGHT_CELL;
import static com.intellij.vcs.log.graph.render.PrintParameters.WIDTH_NODE;

/**
 * @author erokhins
 */
public class GraphCommitCellRender extends AbstractPaddingCellRender {

  @NotNull private final GraphCellPainter graphPainter;
  @NotNull private final VcsLogDataHolder myDataHolder;

  public GraphCommitCellRender(@NotNull GraphCellPainter graphPainter, @NotNull VcsLogDataHolder logDataHolder,
                               @NotNull VcsLogColorManager colorManager) {
    super(logDataHolder.getProject(), colorManager);
    this.graphPainter = graphPainter;
    myDataHolder = logDataHolder;
  }

  @Override
  protected int getLeftPadding(JTable table, @Nullable Object value) {
    GraphCommitCell cell = (GraphCommitCell)value;

    if (cell == null) {
      return 0;
    }

    int refPadding = calcRefsPadding(cell.getRefsToThisCommit(), (Graphics2D)table.getGraphics());

    int countCells = cell.getPrintCell().countCell();
    int graphPadding = countCells * WIDTH_NODE;

    return refPadding + graphPadding;
  }

  @NotNull
  protected String getCellText(@Nullable Object value) {
    GraphCommitCell cell = (GraphCommitCell)value;
    if (cell == null) {
      return "";
    }
    else {
      return cell.getText();
    }
  }

  @Override
  protected void additionPaint(Graphics g, @Nullable Object value) {
    GraphCommitCell cell = (GraphCommitCell)value;
    if (cell == null) {
      return;
    }

    BufferedImage image = UIUtil.createImage(1000, HEIGHT_CELL, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2 = image.createGraphics();
    g2.setBackground(new Color(0, 0, 0, 0));

    graphPainter.draw(g2, cell.getPrintCell());

    int countCells = cell.getPrintCell().countCell();
    int padding = countCells * WIDTH_NODE;
    Collection<VcsRef> refs = cell.getRefsToThisCommit();
    if (!refs.isEmpty()) {
      VirtualFile root = refs.iterator().next().getRoot(); // all refs are from the same commit => they have the same root
      refs = myDataHolder.getLogProvider(root).getReferenceManager().sort(refs);
    }
    drawRefs(g2, refs, padding);

    UIUtil.drawImage(g, image, 0, 0, null);
  }
}
