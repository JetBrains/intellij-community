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

import com.google.common.collect.Iterables;
import com.intellij.dvcs.actions.DvcsCompareWithBranchAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.history.DiffUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.impl.HashImpl;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.*;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.log.HgBaseLogParser;
import org.zmlx.hg4idea.log.HgFileRevisionLogParser;
import org.zmlx.hg4idea.log.HgHistoryUtil;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.repo.HgRepositoryManager;
import org.zmlx.hg4idea.util.HgChangesetUtil;
import org.zmlx.hg4idea.util.HgUtil;
import org.zmlx.hg4idea.util.HgVersion;

import java.util.*;

import static com.intellij.openapi.vcs.history.DiffUtil.createChangesWithCurrentContentForFile;

public class HgCompareWithBranchAction extends DvcsCompareWithBranchAction<HgRepository> {
  @Override
  protected boolean noBranchesToCompare(@NotNull HgRepository repository) {
    final Map<String, LinkedHashSet<Hash>> branches = repository.getBranches();
    if (branches.size() > 1) return false;
    final Hash currentRevisionHash = getCurrentHash(repository);
    final Collection<HgNameWithHashInfo> other_bookmarks = getOtherBookmarks(repository, currentRevisionHash);
    if (!other_bookmarks.isEmpty()) return false;
    // if only one heavy branch and no other bookmarks -> check that current revision is no "main" branch head
    return getBranchMainHash(repository, repository.getCurrentBranch()).equals(currentRevisionHash);
  }

  @NotNull
  private static Hash getCurrentHash(@NotNull HgRepository repository) {
    final String currentRevision = repository.getCurrentRevision();
    assert currentRevision != null : "Compare With Branch couldn't be performed for newly created repository";
    return HashImpl.build(repository.getCurrentRevision());
  }

  @NotNull
  private static List<HgNameWithHashInfo> getOtherBookmarks(@NotNull HgRepository repository, @NotNull final Hash currentRevisionHash) {
    return ContainerUtil.filter(repository.getBookmarks(),
                                new Condition<HgNameWithHashInfo>() {
                                  @Override
                                  public boolean value(HgNameWithHashInfo info) {
                                    return !info.getHash().equals(currentRevisionHash);
                                  }
                                });
  }

  @NotNull
  private static Hash getBranchMainHash(@NotNull HgRepository repository, @NotNull String branchName) {
    return ObjectUtils.assertNotNull(Iterables.getLast(repository.getBranches().get(branchName)));
  }


  @NotNull
  @Override
  protected List<String> getBranchNamesExceptCurrent(@NotNull HgRepository repository) {
    final List<String> namesToCompare = new ArrayList<String>(repository.getBranches().keySet());
    final String currentBranchName = repository.getCurrentBranchName();
    assert currentBranchName != null;
    Hash currentBranchHash = getBranchMainHash(repository, currentBranchName);
    if (currentBranchHash.equals(getCurrentHash(repository))) {
      namesToCompare.remove(currentBranchName);
    }
    namesToCompare.addAll(HgUtil.getNamesWithoutHashes(getOtherBookmarks(repository, currentBranchHash)));
    return namesToCompare;
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
    final FilePath filePath = VcsUtil.getFilePath(file);
    final HgVcs hgVcs = HgVcs.getInstance(project);
    assert hgVcs != null;
    final HgVersion version = hgVcs.getVersion();
    String[] templates = HgBaseLogParser.constructFullTemplateArgument(true, version);
    final VirtualFile repositoryRoot = repository.getRoot();
    HgCommandResult result = HgHistoryUtil
      .getLogResult(project, repositoryRoot, version, 1, Arrays.asList("-r", branchToCompare), HgChangesetUtil.makeTemplate(templates));
    FilePath originalFileName = HgUtil.getOriginalFileName(filePath, ChangeListManager.getInstance(project));
    final HgFile hgFile = new HgFile(repositoryRoot, originalFileName);
    List<HgFileRevision> hgRevisions =
      HgHistoryUtil.getCommitRecords(project, result, new HgFileRevisionLogParser(project, hgFile, version), true);
    if (hgRevisions.isEmpty()) {
      fileDoesntExistInBranchError(project, file, branchToCompare);
      return;
    }

    // constructing the revision with human readable name
    final HgRevisionNumber compareWithRevisionNumber =
      HgRevisionNumber.getInstance(branchToCompare, hgRevisions.get(0).getRevisionNumber().getChangeset());
    final HgRevisionNumber currentRevisionNumber =
      HgRevisionNumber.getInstance(head, ObjectUtils.assertNotNull(repository.getCurrentRevision()));

    List<Change> changes = HgUtil.getDiff(project, repositoryRoot, filePath, compareWithRevisionNumber, null);

    DiffUtil.showDiffFor(project, changes.isEmpty() && !filePath.isDirectory()
                                  ? createChangesWithCurrentContentForFile(filePath,
                                                                           HgContentRevision
                                                                             .create(project, hgFile, compareWithRevisionNumber))
                                  : changes, compareWithRevisionNumber, currentRevisionNumber, filePath);
  }
}
