package com.intellij.vcs.log.graph.render;

import com.intellij.vcs.log.graph.elements.GraphElement;
import com.intellij.vcs.log.printmodel.GraphPrintCell;
import com.intellij.vcs.log.printmodel.SpecialPrintElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.TableColumn;
import java.awt.*;

/**
 * @author erokhins
 */
public interface GraphCellPainter {

  void draw(@NotNull Graphics2D g2, @NotNull GraphPrintCell row);

  @Nullable
  GraphElement mouseOver(@NotNull GraphPrintCell row, int x, int y);

  @Nullable
  SpecialPrintElement mouseOverArrow(@NotNull GraphPrintCell row, int x, int y);

  void setRootColumn(TableColumn column);
}

