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
public class GitRemoteBranch extends GitBranch {

  @NotNull private final GitRemote myRemote;
  @NotNull private final String myNameAtRemote;

  public GitRemoteBranch(@NotNull GitRemote remote, @NotNull String nameAtRemote, @NotNull Hash hash) {
    super(formStandardName(remote, nameAtRemote), hash);
    myRemote = remote;
    myNameAtRemote = nameAtRemote;
  }

  @NotNull
  private static String formStandardName(@NotNull GitRemote remote, @NotNull String nameAtRemote) {
    return remote.getName() + "/" + nameAtRemote;
  }

  @Override
  public boolean isRemote() {
    return true;
  }

  @NotNull
  public GitRemote getRemote() {
    return myRemote;
  }

  @NotNull
  public String getNameAtRemote() {
    return myNameAtRemote;
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
