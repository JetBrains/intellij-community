/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.push.PushSource;
import git4idea.GitLocalBranch;
import org.jetbrains.annotations.NotNull;

public abstract class GitPushSource implements PushSource {

  @NotNull
  public static GitPushSource create(@NotNull GitLocalBranch branch) {
    return new OnBranch(branch);
  }

  @NotNull
  public static GitPushSource create(@NotNull String revision) {
    return new DetachedHead(revision);
  }

  @NotNull
  public abstract GitLocalBranch getBranch();

  private static class OnBranch extends GitPushSource {
    @NotNull private final GitLocalBranch myBranch;

    private OnBranch(@NotNull GitLocalBranch branch) {
      myBranch = branch;
    }

    @NotNull
    @Override
    public String getPresentation() {
      return myBranch.getName();
    }

    @NotNull
    @Override
    public GitLocalBranch getBranch() {
      return myBranch;
    }
  }

  private static class DetachedHead extends GitPushSource {
    @NotNull private final String myRevision;

    public DetachedHead(@NotNull String revision) {
      myRevision = revision;
    }

    @NotNull
    @Override
    public String getPresentation() {
      return DvcsUtil.getShortHash(myRevision);
    }

    @NotNull
    @Override
    public GitLocalBranch getBranch() {
      throw new IllegalStateException("Push is not allowed from detached HEAD");
    }
  }
}
