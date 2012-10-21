/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package git4idea.repo;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * GitRepositoryManager initializes and stores {@link GitRepository GitRepositories} for Git roots defined in the project.
 * @author Kirill Likhodedov
 */
public interface GitRepositoryManager {

  /**
   * Returns the {@link GitRepository} which tracks the Git repository located in the given directory,
   * or {@code null} if the given file is not a Git root known to this {@link com.intellij.openapi.project.Project}.
   */
  @Nullable
  GitRepository getRepositoryForRoot(@Nullable VirtualFile root);

  /**
   * Returns the {@link GitRepository} which the given file belongs to, or {@code null} if the file is not under any Git repository.
   */
  @Nullable
  GitRepository getRepositoryForFile(@NotNull VirtualFile file);

  /**
   * Returns the {@link GitRepository} which the given file belongs to, or {@code null} if the file is not under any Git repository.
   */
  @Nullable
  GitRepository getRepositoryForFile(@NotNull FilePath file);

  /**
   * @return all repositories tracked by the manager.
   */
  @NotNull
  List<GitRepository> getRepositories();

  boolean moreThanOneRoot();

  /**
   * Adds the listener to all existing repositories AND all future repositories.
   * I.e. if a new GitRepository is be created via this GitRepositoryManager, the listener will be added to the repository.
   */
  void addListenerToAllRepositories(@NotNull GitRepositoryChangeListener listener);

  /**
   * Synchronously updates the specified information about Git repository under the given root.
   * @param root   root directory of the Git repository.
   *
   */
  void updateRepository(VirtualFile root);

  void updateAllRepositories();

}
