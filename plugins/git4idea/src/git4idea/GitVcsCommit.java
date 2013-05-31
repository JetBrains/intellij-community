/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.vcs.log.VcsCommit;
import git4idea.history.browser.GitCommit;
import org.jetbrains.annotations.NotNull;

/**
 * Should replace GitCommit.
 *
 * @author Kirill Likhodedov
 */
public class GitVcsCommit implements VcsCommit {

  private final GitCommit myGitCommit;

  public GitVcsCommit(GitCommit gitCommit) {
    myGitCommit = gitCommit;
  }

  @NotNull
  @Override
  public String getFullMessage() {
    return myGitCommit.getDescription();
  }

  @NotNull
  @Override
  public String getHash() {
    return myGitCommit.getHash().getValue();
  }

  @NotNull
  @Override
  public String getAuthor() {
    return myGitCommit.getAuthor();
  }

  @Override
  public long getAuthorTime() {
    return myGitCommit.getAuthorTime();
  }
}
