package com.intellij.vcs.log;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Provides the information needed to build the VCS log, such as the list of most recent commits with their parents.
 */
public interface VcsLogProvider {

  /**
   * Reads the most recent correctly ordered commits from the log. <br/>
   * Commits should be at least topologically ordered, better considering commit time as well. <br/>
   * Commits will be shown in the log in this order.
   * @param requirements some limitations on commit data that should be returned.
   */
  @NotNull
  List<? extends VcsCommitMetadata> readFirstBlock(@NotNull VirtualFile root, @NotNull Requirements requirements) throws VcsException;

  /**
   * <p>Reads the whole history, but only hashes & parents.</p>
   * <p>Also reports authors/committers of this repository to the given user registry.</p>
   */
  void readAllHashes(@NotNull VirtualFile root, @NotNull Consumer<VcsUser> userRegistry,
                     @NotNull Consumer<TimedVcsCommit> commitConsumer) throws VcsException;

  /**
   * Reads those details of the given commits, which are necessary to be shown in the log table.
   */
  @NotNull
  List<? extends VcsShortCommitDetails> readShortDetails(@NotNull VirtualFile root, @NotNull List<String> hashes) throws VcsException;

  /**
   * Read full details of the given commits from the VCS.
   */
  @NotNull
  List<? extends VcsFullCommitDetails> readFullDetails(@NotNull VirtualFile root, @NotNull List<String> hashes) throws VcsException;

  /**
   * Read all references (branches, tags, etc.) for the given roots.
   */
  @NotNull
  Collection<VcsRef> readAllRefs(@NotNull VirtualFile root) throws VcsException;

  /**
   * <p>Returns the VCS which is supported by this provider.</p>
   * <p>If there will be several VcsLogProviders which support the same VCS, only one will be chosen. It is undefined, which one.</p>
   */
  @NotNull
  VcsKey getSupportedVcs();

  /**
   * Returns the {@link VcsLogRefManager} which will be used to identify positions of references in the log table, on the branches panel,
   * and on the details panel.
   */
  @NotNull
  VcsLogRefManager getReferenceManager();

  /**
   * <p>Starts listening to events from the certain VCS, which should lead to the log refresh.</p>
   * <p>It is the responsibility of the certain VcsLogProvider to carefully unsubscribe on project dispose.
   *    Using a {@link MessageBus} topic can help to avoid this task.</p>
   *
   * @param roots     VCS roots which should be listened to.
   * @param refresher The refresher which should be notified about the need of refresh.
   */
  void subscribeToRootRefreshEvents(@NotNull Collection<VirtualFile> roots, @NotNull VcsLogRefresher refresher);

  /**
   * <p>Return commits, which correspond to the given filters.</p>
   *
   * @param maxCount maximum number of commits to request from the VCS, or -1 for unlimited.
   */
  @NotNull
  List<TimedVcsCommit> getCommitsMatchingFilter(@NotNull VirtualFile root, @NotNull VcsLogFilterCollection filterCollection, int maxCount)
    throws VcsException;

  /**
   * Returns the name of current user as specified for the given root,
   * or null if user didn't configure his name in the VCS settings.
   */
  @Nullable
  VcsUser getCurrentUser(@NotNull VirtualFile root) throws VcsException;

  /**
   * Returns the list of names of branches/references which contain the given commit.
   */
  @NotNull
  Collection<String> getContainingBranches(@NotNull VirtualFile root, @NotNull Hash commitHash) throws VcsException;

  interface Requirements {

    /**
     * Returns the number of commits that should be queried from the VCS. <br/>
     * (of course it may return less commits if the repository is small)
     */
    int getCommitCount();

  }

}
