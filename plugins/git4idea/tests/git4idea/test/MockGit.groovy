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
package git4idea.test

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import git4idea.commands.Git
import git4idea.commands.GitCommandResult
import git4idea.commands.GitLineHandlerListener
import git4idea.push.GitPushSpec
import git4idea.repo.GitRepository

/**
 * 
 * @author Kirill Likhodedov
 */
class MockGit implements Git {

  @Override
  void init(Project project, VirtualFile root) {
    new File(root.path, ".git").mkdir()
  }

  @Override
  Set<VirtualFile> untrackedFiles(Project project, VirtualFile root, Collection<VirtualFile> files) {
    throw new UnsupportedOperationException()
  }

  @Override
  Collection<VirtualFile> untrackedFilesNoChunk(Project project, VirtualFile root, List<String> relativePaths) {
    throw new UnsupportedOperationException()
  }

  @Override
  GitCommandResult clone(Project project, File parentDirectory, String url, String clonedDirectoryName) {
    throw new UnsupportedOperationException()
  }

  @Override
  GitCommandResult merge(GitRepository repository, String branchToMerge, GitLineHandlerListener... listeners) {
    throw new UnsupportedOperationException()
  }

  @Override
  GitCommandResult checkout(GitRepository repository, String reference, String newBranch, boolean force, GitLineHandlerListener... listeners) {
    throw new UnsupportedOperationException()
  }

  @Override
  GitCommandResult checkoutNewBranch(GitRepository repository, String branchName, GitLineHandlerListener listener) {
    throw new UnsupportedOperationException()
  }

  @Override
  GitCommandResult createNewTag(GitRepository repository, String tagName, GitLineHandlerListener listener, String reference) {
    throw new UnsupportedOperationException()
  }

  @Override
  GitCommandResult branchDelete(GitRepository repository, String branchName, boolean force, GitLineHandlerListener... listeners) {
    throw new UnsupportedOperationException()
  }

  @Override
  GitCommandResult branchContains(GitRepository repository, String commit) {
    throw new UnsupportedOperationException()
  }

  @Override
  GitCommandResult branchCreate(GitRepository repository, String branchName) {
    throw new UnsupportedOperationException()
  }

  @Override
  GitCommandResult resetHard(GitRepository repository, String revision) {
    throw new UnsupportedOperationException()
  }

  @Override
  GitCommandResult resetMerge(GitRepository repository, String revision) {
    throw new UnsupportedOperationException()
  }

  @Override
  GitCommandResult tip(GitRepository repository, String branchName) {
    throw new UnsupportedOperationException()
  }

  @Override
  GitCommandResult push(GitRepository repository, String remote, String spec, GitLineHandlerListener... listeners) {
    throw new UnsupportedOperationException()
  }

  @Override
  GitCommandResult push(GitRepository repository, GitPushSpec pushSpec, GitLineHandlerListener... listeners) {
    throw new UnsupportedOperationException()
  }
}
