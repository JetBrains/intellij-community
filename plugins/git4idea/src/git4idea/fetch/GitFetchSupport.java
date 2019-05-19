/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.fetch;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
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

  /**
   * Returns the default remote to fetch from, or null if there are no remotes in the repository,
   * or if it is impossible to guess which remote is default.
   */
  @Nullable
  GitRemote getDefaultRemoteToFetch(@NotNull GitRepository repository);

  @NotNull
  static GitFetchSupport fetchSupport(@NotNull Project project) {
    return ServiceManager.getService(project, GitFetchSupport.class);
  }
}
