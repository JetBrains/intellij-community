package com.intellij.vcs.log.ui.tables;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.VcsShortCommitDetails;
import com.intellij.vcs.log.data.DataPack;
import com.intellij.vcs.log.data.LoadMoreStage;
import com.intellij.vcs.log.data.VcsLogDataHolder;
import com.intellij.vcs.log.graph.render.GraphCommitCell;
import com.intellij.vcs.log.impl.VcsLogUtil;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class GraphTableModel extends AbstractVcsLogTableModel<GraphCommitCell> {

  private static final Logger LOG = Logger.getInstance(GraphTableModel.class);

  @NotNull private final DataPack myDataPack;
  @NotNull private final VcsLogDataHolder myDataHolder;

  public GraphTableModel(@NotNull DataPack dataPack, @NotNull VcsLogDataHolder dataHolder, @NotNull VcsLogUiImpl UI,
                         @NotNull LoadMoreStage loadMoreStage) {
    super(dataHolder, UI, dataPack, loadMoreStage);
    myDataPack = dataPack;
    myDataHolder = dataHolder;
  }

  @Override
  public int getRowCount() {
    return myDataPack.getGraphFacade().getVisibleCommitCount();
  }

  @Override
  public void requestToLoadMore(@NotNull Runnable onLoaded) {
    if (!myDataHolder.isFullLogShowing()) {
      myDataHolder.showFullLog(onLoaded);
    }
    else if (!myUi.getFilters().isEmpty()) {
      super.requestToLoadMore(onLoaded);
    }
  }

  @Override
  public boolean canRequestMore() {
    return !myDataHolder.isFullLogShowing() || super.canRequestMore();
  }

  @NotNull
  @Override
  public VirtualFile getRoot(int rowIndex) {
    int head = myDataPack.getGraphFacade().getInfoProvider().getRowInfo(rowIndex).getOneOfHeads();
    Collection<VcsRef> refs = myDataPack.getRefsModel().refsToCommit(head);
    if (refs.isEmpty()) {
      LOG.error("No references pointing to head " + head + " identified for commit at row " + rowIndex);
      return FAKE_ROOT;
    }
    return refs.iterator().next().getRoot();
  }

  @NotNull
  @Override
  protected GraphCommitCell getCommitColumnCell(int rowIndex, @Nullable VcsShortCommitDetails details) {
    String message = "";
    List<VcsRef> refs = Collections.emptyList();
    if (details != null) {
      message = details.getSubject();
      refs = (List<VcsRef>)myDataPack.getRefsModel().refsToCommit(details.getHash());
    }
    return new GraphCommitCell(message, refs);
  }

  @NotNull
  @Override
  protected Class<GraphCommitCell> getCommitColumnClass() {
    return GraphCommitCell.class;
  }

  @Nullable
  @Override
  public Hash getHashAtRow(int row) {
    return myDataHolder.getHash(myDataPack.getGraphFacade().getCommitAtRow(row));
  }

  @Override
  public int getRowOfCommit(@NotNull final Hash hash) {
    final int commitIndex = myDataHolder.getCommitIndex(hash);
    return ContainerUtil.indexOf(VcsLogUtil.getVisibleCommits(myDataPack.getGraphFacade()), new Condition<Integer>() {
      @Override
      public boolean value(Integer integer) {
        return integer == commitIndex;
      }
    });
  }

  @Override
  public int getRowOfCommitByPartOfHash(@NotNull String partialHash) {
    Hash hash = myDataHolder.findHashByString(partialHash);
    return hash != null ? getRowOfCommit(hash) : -1;
  }

}
