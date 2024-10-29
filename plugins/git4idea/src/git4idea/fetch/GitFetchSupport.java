// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.fetch;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * High-level API to execute the {@code git fetch} command.
 */
public interface GitFetchSupport {

  /**
   * For each given repository, fetches the "default" remote.
   * The latter is identified by {@link #getDefaultRemoteToFetch}.
   */
  @NotNull
  GitFetchResult fetchDefaultRemote(@NotNull Collection<GitRepository> repositories);

  /**
   * For each given repository, fetches all its remotes.
   */
  @NotNull
  GitFetchResult fetchAllRemotes(@NotNull Collection<GitRepository> repositories);

  /**
   * Fetches the given remote.
   */
  @NotNull
  GitFetchResult fetch(@NotNull GitRepository repository, @NotNull GitRemote remote);

  @NotNull
  GitFetchResult fetchUnshallow(@NotNull GitRepository repository, @NotNull GitRemote remote);

  /**
   * Fetches the given remotes.
   */
  @NotNull
  GitFetchResult fetchRemotes(@NotNull Collection<Pair<GitRepository, GitRemote>> remotes);

  /**
   * Fetches the given remote using provided refspec.
   */
  @NotNull
  GitFetchResult fetch(@NotNull GitRepository repository, @NotNull GitRemote remote, @NotNull String refspec);

  /**
   * Returns the default remote to fetch from, or null if there are no remotes in the repository,
   * or if it is impossible to guess which remote is default.
   */
  @Nullable
  GitRemote getDefaultRemoteToFetch(@NotNull GitRepository repository);

  /**
   * @return true if there's an active fetch process, false otherwise
   */
  @CalledInAny
  boolean isFetchRunning();

  static @NotNull GitFetchSupport fetchSupport(@NotNull Project project) {
    return project.getService(GitFetchSupport.class);
  }
}
