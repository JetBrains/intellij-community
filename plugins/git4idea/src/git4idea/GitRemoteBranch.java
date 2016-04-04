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
package git4idea;

import git4idea.repo.GitRemote;
import org.jetbrains.annotations.NotNull;

/**
 * @author Kirill Likhodedov
 */
public abstract class GitRemoteBranch extends GitBranch {

  protected GitRemoteBranch(@NotNull String name) {
    super(name);
  }

  /**
   * Returns the name of this remote branch to be used in remote operations: fetch, push, pull.
   * It is the name of this branch how it is defined on the remote.
   * For example, "master".
   * @see #getNameForLocalOperations()
   */
  @NotNull
  public abstract String getNameForRemoteOperations();

  /**
   * Returns the name of this remote branch to be used in local operations: checkout, merge, rebase.
   * It is the name of this branch how it is references in this local repository.
   * For example, "origin/master".
   */
  @NotNull
  public abstract String getNameForLocalOperations();

  @NotNull
  public abstract GitRemote getRemote();

  @Override
  public boolean isRemote() {
    return true;
  }

}
