/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package git4idea.test;

import com.intellij.openapi.vfs.VirtualFile;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryChangeListener;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Kirill Likhodedov
 */
class GitTestRepositoryManager implements GitRepositoryManager {

  private final Collection<GitRepository> myRepositories = new ArrayList<GitRepository>();

  public void add(GitRepository repository) {
    myRepositories.add(repository);
  }

  @Override
  public GitRepository getRepositoryForRoot(@Nullable VirtualFile root) {
    return myRepositories.iterator().next();
  }

  @Override
  public GitRepository getRepositoryForFile(@NotNull VirtualFile file) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public List<GitRepository> getRepositories() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean moreThanOneRoot() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addListenerToAllRepositories(@NotNull GitRepositoryChangeListener listener) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void updateRepository(VirtualFile root, GitRepository.TrackedTopic... topics) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void updateAllRepositories(GitRepository.TrackedTopic... topics) {
    throw new UnsupportedOperationException();
  }
}
