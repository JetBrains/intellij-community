package org.hanuna.gitalk.graphmodel;

import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.vcs.log.CommitParents;
import org.hanuna.gitalk.common.compressedlist.UpdateRequest;
import org.hanuna.gitalk.graph.Graph;
import org.hanuna.gitalk.graph.elements.Node;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author erokhins
 */
public interface GraphModel {

  @NotNull
  public Graph getGraph();

  public void appendCommitsToGraph(@NotNull List<? extends CommitParents> commitParentses);

  public void setVisibleBranchesNodes(@NotNull Function<Node, Boolean> isStartedNode);

  @NotNull
  public FragmentManager getFragmentManager();

  public void addUpdateListener(@NotNull Consumer<UpdateRequest> listener);

  public void removeAllListeners();
}
