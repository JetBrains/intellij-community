package org.hanuna.gitalk.ui.render.painters;

import org.hanuna.gitalk.graph.elements.GraphElement;
import org.hanuna.gitalk.printmodel.GraphPrintCell;
import org.hanuna.gitalk.printmodel.SpecialPrintElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
}

