package com.intellij.vcs.log.printmodel.layout;

import com.intellij.vcs.log.compressedlist.CompressedList;
import com.intellij.vcs.log.compressedlist.RuntimeGenerateCompressedList;
import com.intellij.vcs.log.compressedlist.UpdateRequest;
import com.intellij.vcs.log.compressedlist.generator.Generator;
import com.intellij.vcs.log.graph.Graph;
import com.intellij.vcs.log.graph.elements.NodeRow;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author erokhins
 */
public class LayoutModel {
  private final Graph graph;
  private CompressedList<LayoutRow> layoutRowCompressedList;
  private final Generator<LayoutRow> generator;


  public LayoutModel(@NotNull Graph graph) {
    this.graph = graph;
    this.generator = new LayoutRowGenerator(graph);
    build();
  }

  private void build() {
    List<NodeRow> rows = graph.getNodeRows();
    layoutRowCompressedList = new RuntimeGenerateCompressedList<LayoutRow>(generator, rows.size(), 100);
  }


  @NotNull
  public List<LayoutRow> getLayoutRows() {
    return layoutRowCompressedList.getList();
  }

  public void recalculate(@NotNull UpdateRequest updateRequest) {
    layoutRowCompressedList.recalculate(updateRequest);
  }
}
