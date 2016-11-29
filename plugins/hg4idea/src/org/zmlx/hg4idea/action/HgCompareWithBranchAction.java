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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.impl.HashImpl;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgContentRevision;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.HgNameWithHashInfo;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.command.HgStatusCommand;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.repo.HgRepositoryManager;
import org.zmlx.hg4idea.util.HgUtil;

import java.util.*;

import static com.intellij.openapi.vcs.history.VcsDiffUtil.createChangesWithCurrentContentForFile;

public class HgCompareWithBranchAction extends DvcsCompareWithBranchAction<HgRepository> {
  @Override
  protected boolean noBranchesToCompare(@NotNull HgRepository repository) {
    final Map<String, LinkedHashSet<Hash>> branches = repository.getBranches();
    if (branches.size() > 1) return false;
    final Hash currentRevisionHash = getCurrentHash(repository);
    final Collection<HgNameWithHashInfo> other_bookmarks = getOtherBookmarks(repository, currentRevisionHash);
    if (!other_bookmarks.isEmpty()) return false;
    // if only one heavy branch and no other bookmarks -> check that current revision is not "main" branch head
    return currentRevisionHash.equals(getHeavyBranchMainHash(repository, repository.getCurrentBranch()));
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

  @Nullable
  private static Hash findBookmarkHashByName(@NotNull HgRepository repository, @NotNull final String bookmarkName) {
    HgNameWithHashInfo bookmarkInfo = ContainerUtil.find(repository.getBookmarks(),
                                                         new Condition<HgNameWithHashInfo>() {
                                                           @Override
                                                           public boolean value(HgNameWithHashInfo info) {
                                                             return info.getName().equals(bookmarkName);
                                                           }
                                                         });
    return bookmarkInfo != null ? bookmarkInfo.getHash() : null;
  }

  @Nullable
  private static Hash getHeavyBranchMainHash(@NotNull HgRepository repository, @NotNull String branchName) {
    // null for new branch or not heavy ref
    final LinkedHashSet<Hash> branchHashes = repository.getBranches().get(branchName);
    return branchHashes != null ? ObjectUtils.assertNotNull(Iterables.getLast(branchHashes)) : null;
  }

  @Nullable
  private static Hash detectActiveHashByName(@NotNull HgRepository repository, @NotNull String branchToCompare) {
    Hash refHashToCompare = getHeavyBranchMainHash(repository, branchToCompare);
    return refHashToCompare != null ? refHashToCompare : findBookmarkHashByName(repository, branchToCompare);
  }

  @NotNull
  @Override
  protected List<String> getBranchNamesExceptCurrent(@NotNull HgRepository repository) {
    final List<String> namesToCompare = new ArrayList<>(repository.getBranches().keySet());
    final String currentBranchName = repository.getCurrentBranchName();
    assert currentBranchName != null;
    final Hash currentHash = getCurrentHash(repository);
    if (currentHash.equals(getHeavyBranchMainHash(repository, currentBranchName))) {
      namesToCompare.remove(currentBranchName);
    }
    namesToCompare.addAll(HgUtil.getNamesWithoutHashes(getOtherBookmarks(repository, currentHash)));
    return namesToCompare;
  }

  @NotNull
  @Override
  protected HgRepositoryManager getRepositoryManager(@NotNull Project project) {
    return HgUtil.getRepositoryManager(project);
  }

  @Override
  @NotNull
  protected Collection<Change> getDiffChanges(@NotNull Project project,
                                              @NotNull VirtualFile file,
                                              @NotNull String branchToCompare) throws VcsException {
    HgRepository repository = getRepositoryManager(project).getRepositoryForFile(file);
    if (repository == null) {
      throw new VcsException("Couldn't find repository for " + file.getName());
    }
    final FilePath filePath = VcsUtil.getFilePath(file);
    final VirtualFile repositoryRoot = repository.getRoot();

    final HgFile hgFile = new HgFile(repositoryRoot, filePath);
    Hash refHashToCompare = detectActiveHashByName(repository, branchToCompare);
    if (refHashToCompare == null) {
      throw new VcsException(String.format("Couldn't detect commit related to %s name for %s.", branchToCompare, file));
    }
    final HgRevisionNumber compareWithRevisionNumber = HgRevisionNumber.getInstance(branchToCompare, refHashToCompare.toString());
    List<Change> changes = HgUtil.getDiff(project, repositoryRoot, filePath, compareWithRevisionNumber, null);
    if (changes.isEmpty() && !existInBranch(repository, filePath, compareWithRevisionNumber)) {
      throw new VcsException(fileDoesntExistInBranchError(file, branchToCompare));
    }

    return changes.isEmpty() && !filePath.isDirectory() ? createChangesWithCurrentContentForFile(filePath, HgContentRevision
      .create(project, hgFile, compareWithRevisionNumber)) : changes;
  }

  private static boolean existInBranch(@NotNull HgRepository repository,
                                       @NotNull FilePath path,
                                       @NotNull HgRevisionNumber compareWithRevisionNumber) {
    HgStatusCommand statusCommand = new HgStatusCommand.Builder(true).ignored(false).unknown(false).copySource(!path.isDirectory())
      .baseRevision(compareWithRevisionNumber).targetRevision(null).build(repository.getProject());
    statusCommand.cleanFilesOption(true);
    return !statusCommand.executeInCurrentThread(repository.getRoot(), Collections.singleton(path)).isEmpty();
  }
}
