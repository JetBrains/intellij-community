package com.intellij.vcs.log.data;

import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.impl.VcsUserImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Fake {@link com.intellij.vcs.log.impl.VcsCommitMetadataImpl} implementation that is used to indicate that details are not ready for the moment,
 * they are being retrieved from the VCS.
 *
 * @author Kirill Likhodedov
 */
public class LoadingDetails implements VcsFullCommitDetails {
  private static final VcsUserImpl STUB_USER = new VcsUserImpl("", "");
  private static final String LOADING = "Loading...";

  @NotNull private final Computable<CommitId> myCommitIdComputable;
  private final long myLoadingTaskIndex;
  @Nullable private volatile CommitId myCommitId;

  public LoadingDetails(@NotNull Computable<CommitId> commitIdComputable, long loadingTaskIndex) {
    myCommitIdComputable = commitIdComputable;
    myLoadingTaskIndex = loadingTaskIndex;
  }


  protected CommitId getCommitId() {
    if (myCommitId == null) {
      myCommitId = myCommitIdComputable.compute();
    }
    return myCommitId;
  }

  public long getLoadingTaskIndex() {
    return myLoadingTaskIndex;
  }

  @NotNull
  @Override
  public Collection<Change> getChanges() {
    return ContainerUtil.emptyList();
  }

  @NotNull
  @Override
  public Collection<Change> getChanges(int parent) {
    return ContainerUtil.emptyList();
  }

  @NotNull
  @Override
  public String getFullMessage() {
    return "";
  }

  @NotNull
  @Override
  public VirtualFile getRoot() {
    return getCommitId().getRoot();
  }

  @NotNull
  @Override
  public String getSubject() {
    return LOADING;
  }

  @NotNull
  @Override
  public VcsUser getAuthor() {
    return STUB_USER;
  }

  @NotNull
  @Override
  public VcsUser getCommitter() {
    return STUB_USER;
  }

  @Override
  public long getAuthorTime() {
    return -1;
  }

  @Override
  public long getCommitTime() {
    return -1;
  }

  @NotNull
  @Override
  public Hash getId() {
    return getCommitId().getHash();
  }

  @NotNull
  @Override
  public List<Hash> getParents() {
    return ContainerUtil.emptyList();
  }

  @Override
  public long getTimestamp() {
    return -1;
  }
}
