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
package git4idea.push;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import git4idea.branch.GitBranchPair;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>A container for push specs (source and target branches) per repository.</p>
 *
 * <p>We need GitPushSpecs for two operations:
 *    <ul>
 *      <li>to collect outgoing commits (in all repositories);</li>
 *      <li>to push (in selected repositories).</li>
 *    </ul>
 *
 * <p>That said, repositories that are not pushed, will have the parameter "selected" false.</p>
 *
 * @author Kirill Likhodedov
 */
class GitPushSpecs {

  @NotNull private final Map<GitRepository, GitBranchPair> mySpecs;
  @NotNull private final Map<GitRepository, Boolean> mySelectedRepositories;

  GitPushSpecs() {
    mySpecs = new HashMap<GitRepository, GitBranchPair>();
    mySelectedRepositories = new HashMap<GitRepository, Boolean>();
  }

  static GitPushSpecs empty() {
    return new GitPushSpecs();
  }

  @NotNull
  Map<GitRepository, GitBranchPair> getAllSpecs() {
    return mySpecs;
  }

  void put(@NotNull GitRepository repository, @NotNull GitBranchPair branchPair, boolean selected) {
    mySpecs.put(repository, branchPair);
    mySelectedRepositories.put(repository, selected);
  }

  GitBranchPair get(@NotNull GitRepository repository) {
    return mySpecs.get(repository);
  }

  @NotNull
  public Collection<GitRepository> getSelectedRepositories() {
    return Collections2.filter(mySpecs.keySet(), new Predicate<GitRepository>() {
      @Override
      public boolean apply(@Nullable GitRepository input) {
        assert input != null;
        return mySelectedRepositories.get(input);
      }
    });
  }

  public boolean isSelected(@NotNull GitRepository repository) {
    return mySelectedRepositories.get(repository);
  }

}
