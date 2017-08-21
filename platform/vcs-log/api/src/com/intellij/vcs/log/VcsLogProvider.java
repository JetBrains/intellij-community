package com.intellij.vcs.log;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Provides the information needed to build the VCS log, such as the list of most recent commits with their parents.
 */
public interface VcsLogProvider {
  ExtensionPointName<VcsLogProvider> LOG_PROVIDER_EP = ExtensionPointName.create("com.intellij.logProvider");

  /**
   * Reads the most recent commits from the log together with all repository references.<br/>
   * Commits should be at least topologically ordered, better considering commit time as well: they will be shown in the log in this order.
   * <p/>
   * This method is called both on the startup and on refresh.
   *
   * @param requirements some limitations on commit data that should be returned, e.g. the number of commits.
   * @return given amount of ordered commits and <b>all</b> references in the repository.
   */
  @NotNull
  DetailedLogData readFirstBlock(@NotNull VirtualFile root, @NotNull Requirements requirements) throws VcsException;

  /**
   * Reads the whole history.
   * <p/>
   * Reports commits to the consumer to avoid creation & even temporary storage of a too large commits collection.
   *
   * @return all references and all authors in the repository.
   */
  @NotNull
  LogData readAllHashes(@NotNull VirtualFile root, @NotNull Consumer<TimedVcsCommit> commitConsumer) throws VcsException;

  /**
   * Reads full details of all commits in the repository.
   * <p/>
   * Reports commits to the consumer to avoid creation & even temporary storage of a too large commits collection.
   */
  void readAllFullDetails(@NotNull VirtualFile root, @NotNull Consumer<VcsFullCommitDetails> commitConsumer) throws VcsException;

  /**
   * Reads full details for specified commits in the repository.
   * <p/>
   * Reports commits to the consumer to avoid creation & even temporary storage of a too large commits collection.
   */
  default void readFullDetails(@NotNull VirtualFile root,
                               @NotNull List<String> hashes,
                               @NotNull Consumer<VcsFullCommitDetails> commitConsumer)
    throws VcsException {
    readFullDetails(root, hashes, commitConsumer, false);
  }

  /**
   * Reads full details for specified commits in the repository.
   * Reports commits to the consumer to avoid creation & even temporary storage of a too large commits collection.
   * Allows to skip full rename detection to make things faster. For git, for example, this would be adding diff.renameLimit=x to the command.
   */
  void readFullDetails(@NotNull VirtualFile root,
                       @NotNull List<String> hashes,
                       @NotNull Consumer<VcsFullCommitDetails> commitConsumer,
                       boolean fast)
    throws VcsException;

  /**
   * Reads those details of the given commits, which are necessary to be shown in the log table.
   */
  @NotNull
  List<? extends VcsShortCommitDetails> readShortDetails(@NotNull VirtualFile root, @NotNull List<String> hashes) throws VcsException;

  /**
   * Read full details of the given commits from the VCS.
   * <p>
   * Replaced with readFullDetails(VirtualFile root, List<String>, Consumer<VcsFullCommitDetails>) method.
   * <p>
   * To be removed after 2017.1 release.
   */
  @NotNull
  @Deprecated
  default List<? extends VcsFullCommitDetails> readFullDetails(@NotNull VirtualFile root, @NotNull List<String> hashes)
    throws VcsException {
    List<VcsFullCommitDetails> result = ContainerUtil.newArrayList();
    readFullDetails(root, hashes, result::add);
    return result;
  }

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
   * <p>Returns disposable that unsubscribes from events.
   * Using a {@link MessageBus} topic can help to accomplish that.</p>
   *
   * @param roots     VCS roots which should be listened to.
   * @param refresher The refresher which should be notified about the need of refresh.
   * @return Disposable that unsubscribes from events on dispose.
   */
  @NotNull
  Disposable subscribeToRootRefreshEvents(@NotNull Collection<VirtualFile> roots, @NotNull VcsLogRefresher refresher);

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

  /**
   * In order to tune log for it's VCS, provider may set value to one of the properties specified in {@link com.intellij.vcs.log.VcsLogProperties}.
   *
   * @param property Property instance to return value for.
   * @param <T>      Type of property value.
   * @return Property value or null if unset.
   */
  @Nullable
  <T> T getPropertyValue(VcsLogProperties.VcsLogProperty<T> property);

  /**
   * Returns currently checked out branch in given root, or null if not on any branch or provided root is not under version control.
   *
   * @param root root for which branch is requested.
   * @return branch that is currently checked out in the specified root.
   */
  @Nullable
  String getCurrentBranch(@NotNull VirtualFile root);

  /**
   * Returns {@link VcsLogDiffHandler} for this provider in order to support comparing commits and with local version from log-based file history.
   *
   * @return diff handler or null if unsupported.
   */
  @Nullable
  VcsLogDiffHandler getDiffHandler();

  interface Requirements {

    /**
     * Returns the number of commits that should be queried from the VCS. <br/>
     * (of course it may return less commits if the repository is small)
     */
    int getCommitCount();
  }

  /**
   * Container for references and users.
   */
  interface LogData {
    @NotNull
    Set<VcsRef> getRefs();

    @NotNull
    Set<VcsUser> getUsers();
  }

  /**
   * Container for the ordered list of commits together with their details, and references.
   */
  interface DetailedLogData {
    @NotNull
    List<VcsCommitMetadata> getCommits();

    @NotNull
    Set<VcsRef> getRefs();
  }
}
