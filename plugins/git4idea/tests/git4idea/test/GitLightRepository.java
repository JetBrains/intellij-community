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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitBranch;
import git4idea.branch.GitBranchesCollection;
import git4idea.repo.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.security.provider.SHA;

import java.util.Collection;

/**
 * Simulates Git repository.
 * Made for unit tests, not to spawn a Git process.
 * Stores information about commits and branches in memory.
 *
 * @author Kirill Likhodedov
 *
 * @deprecated Use the standard GitRepository instance returned from {@link GitRepositoryImpl.getLightInstance()}
 */
@Deprecated
public class GitLightRepository implements GitRepository {

  public static class Commit {
    @Nullable private Commit myParent;
    @NotNull private String myHash;
    @NotNull private String myCommitMessage;

    public Commit(String hash, String message, Commit parent) {
      myHash = hash;
      myCommitMessage = message;
      myParent = parent;
    }

    @NotNull
    public String getCommitMessage() {
      return myCommitMessage;
    }

    @Nullable
    public Commit getParent() {
      return myParent;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Commit commit = (Commit)o;

      if (!myCommitMessage.equals(commit.myCommitMessage)) return false;
      if (!myHash.equals(commit.myHash)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myHash.hashCode();
      result = 31 * result + myCommitMessage.hashCode();
      return result;
    }

  }

  private static class Branch {
    @NotNull private String myName;
    @Nullable private Commit myCommit;

    public Branch(String name, Commit commit) {
      myName = name;
      myCommit = commit;
    }
  }

  private Branch myHead;

  public GitLightRepository() {
    myHead = new Branch("master", null);
  }

  public Commit commit(String commitMessage) {
    Commit parent = myHead.myCommit;
    String hash = new SHA().toString();
    Commit commit = new Commit(hash, commitMessage, parent);
    myHead.myCommit = commit;
    return commit;
  }

  public Commit cherryPick(String commitMessage) {
    return commit(commitMessage);
  }

  @Nullable
  public Commit getHead() {
    return myHead.myCommit;
  }

  @NotNull
  @Override
  public VirtualFile getRoot() {
    return new GitMockVirtualFile(FileUtil.getTempDirectory());
  }

  @NotNull
  @Override
  public VirtualFile getGitDir() {
    return new GitMockVirtualFile(getRoot().getPath() + "/.git");
  }

  @NotNull
  @Override
  public String getPresentableUrl() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public Project getProject() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public GitUntrackedFilesHolder getUntrackedFilesHolder() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public State getState() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getCurrentRevision() {
    throw new UnsupportedOperationException();
  }

  @Override
  public GitBranch getCurrentBranch() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public GitBranchesCollection getBranches() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public GitConfig getConfig() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public Collection<GitRemote> getRemotes() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isMergeInProgress() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isRebaseInProgress() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isOnBranch() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isFresh() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addListener(@NotNull GitRepositoryChangeListener listener) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void update(TrackedTopic... topics) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String toLogString() {
    throw new UnsupportedOperationException();
  }

}
