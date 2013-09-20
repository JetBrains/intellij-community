package org.hanuna.gitalk.printmodel;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author erokhins
 */
public interface GraphPrintCell {
  public int countCell();

  @NotNull
  public List<ShortEdge> getUpEdges();

  @NotNull
  public List<ShortEdge> getDownEdges();

  @NotNull
  public List<SpecialPrintElement> getSpecialPrintElements();
}
