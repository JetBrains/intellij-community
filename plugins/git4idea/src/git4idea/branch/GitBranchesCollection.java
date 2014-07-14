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
package git4idea.branch;

import com.intellij.openapi.util.Condition;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitBranch;
import git4idea.GitLocalBranch;
import git4idea.GitRemoteBranch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

/**
 * <p>
 *   Storage for local, remote and current branches.
 *   The reason of creating this special collection is that
 *   in the terms of performance, they are detected by {@link git4idea.repo.GitRepositoryReader} at once;
 *   and also usually both sets of branches are needed by components, but are treated differently,
 *   so it is more convenient to have them separated, but in a single container.
 * </p> 
 * 
 * @author Kirill Likhodedov
 */
public final class GitBranchesCollection {
  
  public static final GitBranchesCollection EMPTY = new GitBranchesCollection(Collections.<GitLocalBranch>emptyList(),
                                                                              Collections.<GitRemoteBranch>emptyList());

  private final Collection<GitLocalBranch> myLocalBranches;
  private final Collection<GitRemoteBranch> myRemoteBranches;

  public GitBranchesCollection(@NotNull Collection<GitLocalBranch> localBranches, @NotNull Collection<GitRemoteBranch> remoteBranches) {
    myRemoteBranches = remoteBranches;
    myLocalBranches = localBranches;
  }

  @NotNull
  public Collection<GitLocalBranch> getLocalBranches() {
    return Collections.unmodifiableCollection(myLocalBranches);
  }

  @NotNull
  public Collection<GitRemoteBranch> getRemoteBranches() {
    return Collections.unmodifiableCollection(myRemoteBranches);
  }

  @Nullable
  public GitLocalBranch findLocalBranch(@NotNull String name) {
    return findByName(myLocalBranches, name);
  }

  @Nullable
  public GitBranch findBranchByName(@NotNull String name) {
    GitLocalBranch branch = findByName(myLocalBranches, name);
    return branch != null ? branch : findByName(myRemoteBranches, name);
  }

  @Nullable
  private static <T extends GitBranch> T findByName(Collection<T> branches, @NotNull final String name) {
    return ContainerUtil.find(branches, new Condition<T>() {
      @Override
      public boolean value(T branch) {
        return name.equals(branch.getName());
      }
    });
  }

}
