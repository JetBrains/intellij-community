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
package org.zmlx.hg4idea.util;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.*;
import org.zmlx.hg4idea.action.HgCommandResultNotifier;
import org.zmlx.hg4idea.command.HgLogCommand;
import org.zmlx.hg4idea.execution.HgCommandException;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.log.HgContentRevisionFactory;
import org.zmlx.hg4idea.provider.HgCommittedChangeList;

import java.io.File;
import java.util.*;

/**
 * @author Nadya Zabrodina
 */
public class HgHistoryUtil {

  private HgHistoryUtil() {
  }

  /**
   * <p>Get & parse hg log detailed output with commits, their parents and their changes.</p>
   * <p/>
   * <p>Warning: this is method is efficient by speed, but don't query too much, because the whole log output is retrieved at once,
   * and it can occupy too much memory. The estimate is ~600Kb for 1000 commits.</p>
   */
  @NotNull
  public static List<VcsFullCommitDetails> history(@NotNull final Project project,
                                                   @NotNull final VirtualFile root, int limit,
                                                   List<String> parameters)
    throws VcsException {
    List<HgCommittedChangeList> result = getCommittedChangeList(project, root, limit, true, parameters);
    return ContainerUtil.mapNotNull(result, new Function<HgCommittedChangeList, VcsFullCommitDetails>() {
      @Override
      public VcsFullCommitDetails fun(HgCommittedChangeList record) {
        return createCommit(project, root, record);
      }
    });
  }

  @NotNull
  private static List<HgCommittedChangeList> getCommittedChangeList(@NotNull final Project project,
                                                                    @NotNull final VirtualFile root, int limit, boolean withFiles,
                                                                    List<String> parameters) {
    HgFile hgFile = new HgFile(root, VcsUtil.getFilePath(root.getPath()));
    List<HgCommittedChangeList> result = new LinkedList<HgCommittedChangeList>();
    final List<HgFileRevision> localRevisions;
    HgLogCommand hgLogCommand = new HgLogCommand(project);
    List<String> args = new ArrayList<String>(parameters);
    hgLogCommand.setLogFile(false);
    HgVcs hgvcs = HgVcs.getInstance(project);
    assert hgvcs != null;
    try {
      if (!hgvcs.getVersion().isParentRevisionTemplateSupported()) {
        args.add("--debug");
      }
      localRevisions = hgLogCommand.execute(hgFile, limit, withFiles, args);
    }
    catch (HgCommandException e) {
      new HgCommandResultNotifier(project).notifyError(null, HgVcsMessages.message("hg4idea.error.log.command.execution"), e.getMessage());
      return Collections.emptyList();
    }
    Collections.reverse(localRevisions);
    for (HgFileRevision revision : localRevisions) {
      HgRevisionNumber vcsRevisionNumber = revision.getRevisionNumber();
      List<HgRevisionNumber> parents = vcsRevisionNumber.getParents();

      HgRevisionNumber firstParent = parents.isEmpty() ? null : parents.get(0); // can have no parents if it is a root

      List<Change> changes = new ArrayList<Change>();
      for (String file : revision.getModifiedFiles()) {
        changes.add(createChange(project, root, file, firstParent, file, vcsRevisionNumber, FileStatus.MODIFIED));
      }
      for (String file : revision.getAddedFiles()) {
        changes.add(createChange(project, root, null, null, file, vcsRevisionNumber, FileStatus.ADDED));
      }
      for (String file : revision.getDeletedFiles()) {
        changes.add(createChange(project, root, file, firstParent, null, vcsRevisionNumber, FileStatus.DELETED));
      }
      for (Map.Entry<String, String> copiedFile : revision.getCopiedFiles().entrySet()) {
        changes
          .add(createChange(project, root, copiedFile.getKey(), firstParent, copiedFile.getValue(), vcsRevisionNumber, FileStatus.ADDED));
      }

      result.add(new HgCommittedChangeList(hgvcs, vcsRevisionNumber, revision.getBranchName(), revision.getCommitMessage(),
                                           revision.getAuthor(), revision.getRevisionDate(), changes));
    }
    Collections.reverse(result);
    return result;
  }

  @NotNull
  public static List<? extends VcsShortCommitDetails> readMiniDetails(Project project, final VirtualFile root, List<String> hashes)
    throws VcsException {
    final VcsLogObjectsFactory factory = ServiceManager.getService(project, VcsLogObjectsFactory.class);
    return ContainerUtil.map(getCommittedChangeList(project, root, -1, false, prepareHashes(hashes)),
                             new Function<HgCommittedChangeList, VcsShortCommitDetails>() {
                               @Override
                               public VcsShortCommitDetails fun(HgCommittedChangeList record) {
                                 HgRevisionNumber revNumber = (HgRevisionNumber)record.getRevisionNumber();
                                 List<Hash> parents = new SmartList<Hash>();
                                 for (HgRevisionNumber parent : revNumber.getParents()) {
                                   parents.add(factory.createHash(parent.getChangeset()));
                                 }
                                 return factory.createShortDetails(factory.createHash(revNumber.getChangeset()), parents,
                                                                   record.getCommitDate().getTime(), root,
                                                                   revNumber.getSubject(), revNumber.getAuthor(), revNumber.getEmail());
                               }
                             });
  }

  @NotNull
  public static List<TimedVcsCommit> readAllHashes(@NotNull Project project, @NotNull VirtualFile root,
                                                   @NotNull final Consumer<VcsUser> userRegistry, List<String> params) throws VcsException {

    final VcsLogObjectsFactory factory = ServiceManager.getService(project, VcsLogObjectsFactory.class);
    return ContainerUtil.map(getCommittedChangeList(project, root, -1, false, params), new Function<HgCommittedChangeList, TimedVcsCommit>() {
      @Override
      public TimedVcsCommit fun(HgCommittedChangeList record) {
        HgRevisionNumber revNumber = (HgRevisionNumber)record.getRevisionNumber();
        List<Hash> parents = new SmartList<Hash>();
        for (HgRevisionNumber parent : revNumber.getParents()) {
          parents.add(factory.createHash(parent.getChangeset()));
        }
        userRegistry.consume(factory.createUser(record.getRevision().getAuthor(), revNumber.getEmail()));
        return factory.createTimedCommit(factory.createHash(revNumber.getChangeset()),
                                         parents, record.getCommitDate().getTime());
      }
    });
  }

  @NotNull
  public static Change createChange(Project project, VirtualFile root,
                                    @Nullable String fileBefore,
                                    @Nullable HgRevisionNumber revisionBefore,
                                    @Nullable String fileAfter,
                                    HgRevisionNumber revisionAfter,
                                    FileStatus aStatus) {

    HgContentRevision beforeRevision =
      fileBefore == null ? null : new HgContentRevision(project, new HgFile(root, new File(root.getPath(), fileBefore)), revisionBefore);
    HgContentRevision afterRevision =
      fileAfter == null ? null : new HgContentRevision(project, new HgFile(root, new File(root.getPath(), fileAfter)), revisionAfter);
    return new Change(beforeRevision, afterRevision, aStatus);
  }

  @NotNull
  private static VcsFullCommitDetails createCommit(@NotNull Project project, @NotNull VirtualFile root,
                                                   @NotNull HgCommittedChangeList record) {

    final VcsLogObjectsFactory factory = ServiceManager.getService(project, VcsLogObjectsFactory.class);
    HgRevisionNumber revNumber = (HgRevisionNumber)record.getRevisionNumber();

    List<Hash> parents = ContainerUtil.map(revNumber.getParents(), new Function<HgRevisionNumber, Hash>() {
      @Override
      public Hash fun(HgRevisionNumber parent) {
        return factory.createHash(parent.getChangeset());
      }
    });
    return factory.createFullDetails(factory.createHash(revNumber.getChangeset()), parents, record.getCommitDate().getTime(), root,
                                     revNumber.getSubject(),
                                     revNumber.getAuthor(), revNumber.getEmail(), revNumber.getCommitMessage(), record.getCommitterName(),
                                     "", record.getCommitDate().getTime(),
                                     ContainerUtil.newArrayList(record.getChanges()), HgContentRevisionFactory.getInstance(project));
  }

  @Nullable
  public static List<String> prepareHashes(@NotNull List<String> hashes) {
    List<String> hashArgs = new ArrayList<String>();
    for (String hash : hashes) {
      hashArgs.add("-r");
      hashArgs.add(hash);
    }
    return hashArgs;
  }

  @NotNull
  public static Collection<String> getDescendingHeadsOfBranches(@NotNull Project project, @NotNull VirtualFile root, @NotNull Hash hash)
    throws VcsException {
    //hg log -r "descendants(659db54c1b6865c97c4497fa867194bcd759ca76) and head()" --template "{branch}{bookmarks}"
    Set<String> branchHeads = new HashSet<String>();
    List<String> params = new ArrayList<String>();
    params.add("-r");
    params.add("descendants(" + hash.asString() + ") and head()");
    HgLogCommand hgLogCommand = new HgLogCommand(project);
    hgLogCommand.setLogFile(false);
    String template = HgChangesetUtil.makeTemplate("{branch}", "{bookmarks}");
    HgCommandResult logResult = hgLogCommand.execute(root, template, -1, null, params);
    if (logResult == null || logResult.getExitValue() != 0) {
      throw new VcsException("Couldn't get commit details: log command execution error.");
    }
    String output = logResult.getRawOutput();
    List<String> changeSets = StringUtil.split(output, HgChangesetUtil.CHANGESET_SEPARATOR);
    for (String line : changeSets) {
      List<String> attributes = StringUtil.split(line, HgChangesetUtil.ITEM_SEPARATOR);
      branchHeads.addAll(attributes);
    }
    return branchHeads;
  }
}
