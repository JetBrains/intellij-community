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
package git4idea.repo;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author Kirill Likhodedov
 */
public class GitFakeRepositoryManager implements GitRepositoryManager {
  @Nullable
  @Override
  public GitRepository getRepositoryForRoot(@Nullable VirtualFile root) {
    return null;
  }

  @Nullable
  @Override
  public GitRepository getRepositoryForFile(@NotNull VirtualFile file) {
    return null;
  }

  @Override
  public GitRepository getRepositoryForFile(@NotNull FilePath file) {
    return null;
  }

  @NotNull
  @Override
  public List<GitRepository> getRepositories() {
    return Collections.emptyList();
  }

  @Override
  public boolean moreThanOneRoot() {
    return false;
  }

  @Override
  public void updateRepository(VirtualFile root) {
  }

  @Override
  public void updateAllRepositories() {
  }
}
