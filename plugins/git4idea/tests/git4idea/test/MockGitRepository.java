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
package git4idea.test;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitLocalBranch;
import git4idea.branch.GitBranchesCollection;
import git4idea.repo.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class MockGitRepository implements GitRepository {

  @NotNull private final VirtualFile myRoot;
  @NotNull private final Project myProject;

  public MockGitRepository(@NotNull Project project, @NotNull VirtualFile root) {
    myRoot = root;
    myProject = project;
  }

  @NotNull
  @Override
  public VirtualFile getGitDir() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public GitUntrackedFilesHolder getUntrackedFilesHolder() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public GitRepoInfo getInfo() {
    throw new UnsupportedOperationException();
  }

  @Nullable
  @Override
  public GitLocalBranch getCurrentBranch() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public GitBranchesCollection getBranches() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public Collection<GitRemote> getRemotes() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public Collection<GitBranchTrackInfo> getBranchTrackInfos() {
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

  @NotNull
  @Override
  public VirtualFile getRoot() {
    return myRoot;
  }

  @NotNull
  @Override
  public String getPresentableUrl() {
    return myRoot.getPresentableUrl();
  }

  @NotNull
  @Override
  public Project getProject() {
    return myProject;
  }

  @NotNull
  @Override
  public State getState() {
    throw new UnsupportedOperationException();
  }

  @Nullable
  @Override
  public String getCurrentBranchName() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public AbstractVcs getVcs() {
    throw new UnsupportedOperationException();
  }

  @Nullable
  @Override
  public String getCurrentRevision() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isFresh() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void update() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public String toLogString() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void dispose() {
  }
}
