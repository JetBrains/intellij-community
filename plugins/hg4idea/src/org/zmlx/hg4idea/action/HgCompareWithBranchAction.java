/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.zmlx.hg4idea.action;

import com.intellij.dvcs.actions.DvcsCompareWithBranchAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.CurrentRevision;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.impl.HashImpl;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgFileRevision;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.log.HgBaseLogParser;
import org.zmlx.hg4idea.log.HgFileRevisionLogParser;
import org.zmlx.hg4idea.log.HgHistoryUtil;
import org.zmlx.hg4idea.provider.HgDiffFromHistoryHandler;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.repo.HgRepositoryManager;
import org.zmlx.hg4idea.util.HgChangesetUtil;
import org.zmlx.hg4idea.util.HgUtil;
import org.zmlx.hg4idea.util.HgVersion;

import java.util.*;

public class HgCompareWithBranchAction extends DvcsCompareWithBranchAction<HgRepository> {
  @Override
  protected boolean noBranchesToCompare(@NotNull HgRepository repository) {
    final Map<String, Set<Hash>> branches = repository.getBranches();
    assert !branches.isEmpty();
    String currentRevision = repository.getCurrentRevision();
    assert currentRevision != null : "Compare With Branch couldn't be performed for newly created repository";
    if (branches.keySet().size() > 1) return false;
    String branchName = branches.keySet().iterator().next();
    return branches.get(branchName).contains(HashImpl.build(repository.getCurrentRevision()));
  }

  @NotNull
  @Override
  protected List<String> getBranchNamesExceptCurrent(@NotNull HgRepository repository) {
    final Map<String, Set<Hash>> branches = repository.getBranches();
    return new ArrayList<String>(branches.keySet());
  }

  @NotNull
  @Override
  protected HgRepositoryManager getRepositoryManager(@NotNull Project project) {
    return HgUtil.getRepositoryManager(project);
  }

  @Override
  protected void showDiffWithBranch(@NotNull Project project,
                                    @NotNull VirtualFile file,
                                    @NotNull String head,
                                    @NotNull String branchToCompare) throws VcsException {
    HgRepository repository = getRepositoryManager(project).getRepositoryForFile(file);
    if (repository == null) {
      LOG.error("Couldn't find repository for " + file.getName());
      return;
    }
    final HgVcs hgVcs = HgVcs.getInstance(project);
    assert hgVcs != null;
    final HgVersion version = hgVcs.getVersion();
    String[] templates = HgBaseLogParser.constructFullTemplateArgument(true, version);
    final VirtualFile repositoryRoot = repository.getRoot();
    HgCommandResult result = HgHistoryUtil
      .getLogResult(project, repositoryRoot, version, 1, Arrays.asList("-r", branchToCompare), HgChangesetUtil.makeTemplate(templates));
    List<HgFileRevision> hgRevisions = HgHistoryUtil
      .getCommitRecords(project, result,
                        new HgFileRevisionLogParser(project, HgHistoryUtil.getOriginalHgFile(project, repositoryRoot), version), true);
    if (hgRevisions.isEmpty()) {
      fileDoesntExistInBranchError(project, file, branchToCompare);
      return;
    }
    CurrentRevision currentRevision = new CurrentRevision(file, HgRevisionNumber.getInstance("", head));
    new HgDiffFromHistoryHandler(project).showDiffForTwo(project, VcsUtil.getFilePath(file), hgRevisions.get(0), currentRevision);
  }
}
