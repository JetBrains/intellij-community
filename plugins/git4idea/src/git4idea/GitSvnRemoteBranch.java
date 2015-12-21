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
 * <p>The naming conventions of SVN remote branches are slightly different from the ordinary remote branches.</p>
 *
 * <p>No remote is specified: dot (".") is used as a remote.</p>
 * <p>Remote branch name has "refs/remotes/branch" format, i. e. it doesn't have a remote prefix.</p>
 *
 * <p>Because of these differences, GitSvnRemoteBranch doesn't {@link GitStandardRemoteBranch}. </p>
 *
 * @author Kirill Likhodedov
 */
public class GitSvnRemoteBranch extends GitRemoteBranch {

  public GitSvnRemoteBranch(@NotNull String fullName) {
    super(fullName);
  }

  @NotNull
  @Override
  public String getNameForRemoteOperations() {
    return getFullName();
  }

  @NotNull
  @Override
  public String getNameForLocalOperations() {
    return getFullName();
  }

  @NotNull
  @Override
  public GitRemote getRemote() {
    return GitRemote.DOT;
  }

  @NotNull
  @Override
  public String getFullName() {
    return getName();
  }

  @Override
  public boolean equals(Object o) {
    return super.equals(o);
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }

  @Override
  public String toString() {
    return super.toString();
  }

}
