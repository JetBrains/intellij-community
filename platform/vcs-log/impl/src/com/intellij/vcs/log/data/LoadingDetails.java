package com.intellij.vcs.log.data;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.impl.VcsFullCommitDetailsImpl;
import com.intellij.vcs.log.ui.tables.AbstractVcsLogTableModel;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

/**
 * Fake {@link VcsFullCommitDetailsImpl} implementation that is used to indicate that details are not ready for the moment,
 * they are being retrieved from the VCS.
 *
 * @author Kirill Likhodedov
 */
public class LoadingDetails extends VcsFullCommitDetailsImpl {

  private final long myLoadingTaskIndex;

  public LoadingDetails(@NotNull Hash hash, long loadingTaskIndex) {
    super(hash, Collections.<Hash>emptyList(), -1, AbstractVcsLogTableModel.UNKNOWN_ROOT,
          "Loading...", "", "", "", "", "", -1, Collections.<Change>emptyList());
    myLoadingTaskIndex = loadingTaskIndex;
  }

  public long getLoadingTaskIndex() {
    return myLoadingTaskIndex;
  }

}
