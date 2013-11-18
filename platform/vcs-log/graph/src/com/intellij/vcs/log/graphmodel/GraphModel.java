package com.intellij.vcs.log.graphmodel;

import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.vcs.log.GraphCommit;
import com.intellij.vcs.log.compressedlist.UpdateRequest;
import com.intellij.vcs.log.graph.Graph;
import com.intellij.vcs.log.graph.elements.Node;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author erokhins
 */
public interface GraphModel {

  @NotNull
  public Graph getGraph();

  public void appendCommitsToGraph(@NotNull List<GraphCommit> commitParentses);

  public void setVisibleBranchesNodes(@NotNull Function<Node, Boolean> isStartedNode);

  @NotNull
  public FragmentManager getFragmentManager();

  public void addUpdateListener(@NotNull Consumer<UpdateRequest> listener);

  public void removeAllListeners();
}
