package com.intellij.vcs.log.graphmodel;

import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsCommit;
import com.intellij.vcs.log.compressedlist.UpdateRequest;
import com.intellij.vcs.log.graph.Graph;
import com.intellij.vcs.log.graph.elements.Node;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author erokhins
 */
public interface GraphModel {

  @NotNull
  public Graph getGraph();

  public void appendCommitsToGraph(@NotNull List<? extends VcsCommit> commitParentses);

  public void setVisibleBranchesNodes(@NotNull Function<Node, Boolean> isStartedNode);

  /**
   * Checks if the node of the given hash is visible, and returns it if so; otherwise return null.
   */
  @Nullable
  boolean isNodeOfHashVisible(@NotNull Hash hash);

  @NotNull
  public FragmentManager getFragmentManager();

  public void addUpdateListener(@NotNull Consumer<UpdateRequest> listener);

  public void removeAllListeners();
}
