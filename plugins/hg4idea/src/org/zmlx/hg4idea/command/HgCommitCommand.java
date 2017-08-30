// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.command;

import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.execution.HgCommandException;
import org.zmlx.hg4idea.execution.HgCommandExecutor;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.util.HgUtil;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static org.zmlx.hg4idea.HgErrorHandler.ensureSuccess;

public class HgCommitCommand extends HgCommitTypeCommand {

  private final boolean myCloseBranch;
  private final boolean myShouldCommitWithSubrepos;

  @NotNull private List<String> mySubrepos = Collections.emptyList();

  public HgCommitCommand(@NotNull Project project,
                         @NotNull HgRepository repository,
                         @NotNull String message,
                         boolean amend,
                         boolean closeBranch,
                         boolean shouldCommitWithSubrepos) {
    super(project, repository, message, amend);
    myCloseBranch = closeBranch;
    myShouldCommitWithSubrepos = shouldCommitWithSubrepos;
  }

  public HgCommitCommand(@NotNull Project project, @NotNull HgRepository repo, @NotNull String message, boolean amend) {
    this(project, repo, message, amend, false, false);
  }

  public HgCommitCommand(Project project, @NotNull HgRepository repo, @NotNull String message) {
    this(project, repo, message, false);
  }

  protected void executeChunked(@NotNull List<List<String>> chunkedCommits) throws VcsException {
    if (chunkedCommits.isEmpty()) {
      commitChunkFiles(ContainerUtil.emptyList(), myAmend, myCloseBranch);
    }
    else {
      int size = chunkedCommits.size();
      if (myShouldCommitWithSubrepos && myRepository.hasSubrepos()) {
        mySubrepos = HgUtil.getNamesWithoutHashes(myRepository.getSubrepos());
      }
      commitChunkFiles(chunkedCommits.get(0), myAmend, !mySubrepos.isEmpty(), myCloseBranch && size == 1);
      HgVcs vcs = HgVcs.getInstance(myProject);
      boolean amendCommit = vcs != null && vcs.getVersion().isAmendSupported();
      for (int i = 1; i < size; i++) {
        List<String> chunk = chunkedCommits.get(i);
        commitChunkFiles(chunk, amendCommit, false, myCloseBranch && i == size - 1);
      }
    }
  }

  private void commitChunkFiles(@NotNull List<String> chunk, boolean amendCommit, boolean closeBranch) throws VcsException {
    commitChunkFiles(chunk, amendCommit, false, closeBranch);
  }

  private void commitChunkFiles(@NotNull List<String> chunk, boolean amendCommit, boolean withSubrepos, boolean closeBranch)
    throws VcsException {
    List<String> parameters = new LinkedList<>();
    parameters.add("--logfile");
    parameters.add(saveCommitMessage().getAbsolutePath());
    // note: for now mercurial could not perform amend commit with -S option
    if (withSubrepos) {
      parameters.add("-S");
      parameters.addAll(mySubrepos);
    }
    else if (amendCommit) {
      parameters.add("--amend");
    }
    if (closeBranch) {
      if (chunk.isEmpty() && myRepository.getState() != Repository.State.MERGING) {
        //if there are changed files but nothing selected -> need to exclude all; if merge commit then nothing excluded
        parameters.add("-X");
        parameters.add("\"**\"");
      }
      parameters.add("--close-branch");
    }
    parameters.addAll(chunk);
    HgCommandExecutor executor = new HgCommandExecutor(myProject);
    ensureSuccess(executor.executeInCurrentThread(myRepository.getRoot(), "commit", parameters));
  }
}
