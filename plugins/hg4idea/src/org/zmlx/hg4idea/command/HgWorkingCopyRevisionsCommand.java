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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ObjectsConvertor;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.execution.HgCommandExecutor;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.util.HgChangesetUtil;
import org.zmlx.hg4idea.util.HgUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Commands to get revision numbers. These are: parents, id, tip.
 */
public class HgWorkingCopyRevisionsCommand {

  private final Project myProject;
  private static final Logger LOG = Logger.getInstance(HgWorkingCopyRevisionsCommand.class);

  public HgWorkingCopyRevisionsCommand(Project project) {
    myProject = project;
  }

  /**
   * Current repository revision(s).
   * @param repo repository to work on.
   * @return List of parent's revision numbers.
   * @see #parents(com.intellij.openapi.vfs.VirtualFile, com.intellij.openapi.vfs.VirtualFile, org.zmlx.hg4idea.HgRevisionNumber)
   * TODO: return Pair
   */
  @NotNull
  public List<HgRevisionNumber> parents(@NotNull VirtualFile repo) {
    return getRevisions(repo, "parents", null, null, true);
  }

  /**
   * @see #parents(com.intellij.openapi.vfs.VirtualFile, com.intellij.openapi.vfs.VirtualFile, org.zmlx.hg4idea.HgRevisionNumber)
   */
  @NotNull
  public Couple<HgRevisionNumber> parents(@NotNull VirtualFile repo, @Nullable VirtualFile file) {
    return parents(repo, file, null);
  }

  /**
   * Parent(s) of the given revision of the given file. If there are two of them (in the case of merge) the first element of the pair
   * is the latest parent (i.e. having greater revision number), second one is the earlier parent (having smaller revision number).
   * @param repo     repository to work on.
   * @param file     file which revision's parents we are interested in. If null, the history of the whole repository is considered.
   * @param revision revision number which parent is wanted. If null, the last revision is taken.
   * @return One or two (in case of a merge commit) parents of the given revision. Or even zero in case of a fresh repository.
   *         So one should check pair elements for null.
   */
  @NotNull
  public Couple<HgRevisionNumber> parents(@NotNull VirtualFile repo, @Nullable VirtualFile file, @Nullable HgRevisionNumber revision) {
    return parents(repo, ObjectsConvertor.VIRTUAL_FILEPATH.convert(file), revision);
  }

  /**
   * @see #parents(VirtualFile, FilePath, HgRevisionNumber)
   */
  @NotNull
  public Couple<HgRevisionNumber> parents(@NotNull VirtualFile repo, @Nullable FilePath file) {
    return parents(repo, file, null);
  }

  /**
   * Parent(s) of the given revision of the given file. If there are two of them (in the case of merge) the first element of the pair
   * is the latest parent (i.e. having greater revision number), second one is the earlier parent (having smaller revision number).
   * @param repo     repository to work on.
   * @param file     filepath which revision's parents we are interested in. If null, the history of the whole repository is considered.
   * @param revision revision number which parent is wanted. If null, the last revision is taken.
   * @return One or two (in case of a merge commit) parents of the given revision. Or even zero in case of a fresh repository.
   *         So one should check pair elements for null.
   */
  @NotNull
  public Couple<HgRevisionNumber> parents(@NotNull VirtualFile repo, @Nullable FilePath file, @Nullable HgRevisionNumber revision) {
    final List<HgRevisionNumber> revisions = getRevisions(repo, "parents", file, revision, true);
    switch (revisions.size()) {
      case 1: return Couple.of(revisions.get(0), null);
      case 2: return Couple.of(revisions.get(0), revisions.get(1));
      default: return Couple.of(null, null);
    }
  }

  @Nullable
  public HgRevisionNumber firstParent(@NotNull VirtualFile repo) {
    List<HgRevisionNumber> parents = parents(repo);
    if (parents.isEmpty()) {
      //this is possible when we have a freshly initialized mercurial repository
      return HgRevisionNumber.NULL_REVISION_NUMBER;
    }
    else {
      return parents.get(0);
    }
  }

  @Nullable
  public HgRevisionNumber tip(@NotNull VirtualFile repo) {
    List<HgRevisionNumber> tips = getRevisions(repo, "tip", null, null, true);
    if (tips.size() > 1) {
      throw new IllegalStateException("There cannot be multiple tips");
    }
    if(!tips.isEmpty()) {
      return tips.get(0);
    }
    else return HgRevisionNumber.NULL_REVISION_NUMBER;
  }

  /**
   * Returns the result of 'hg id' execution, i.e. current state of the repository.
   * @return one or two revision numbers. Two revisions is the case of unresolved merge. In other cases there are only one revision.
   */
  @NotNull
  public Couple<HgRevisionNumber> identify(@NotNull VirtualFile repo) {
    HgCommandExecutor commandExecutor = new HgCommandExecutor(myProject);
    commandExecutor.setSilent(true);
    HgCommandResult result = commandExecutor.executeInCurrentThread(repo, "identify", Arrays.asList("--num", "--id"));
    if (result == null) {
      return Couple.of(HgRevisionNumber.NULL_REVISION_NUMBER, null);
    }

    final List<String> lines = result.getOutputLines();
    if (lines != null && !lines.isEmpty()) {
      List<String> parts = StringUtil.split(lines.get(0), " ");
      String changesets = parts.get(0);
      String revisions = parts.get(1);
      if (parts.size() >= 2) {
        if (changesets.indexOf('+') != changesets.lastIndexOf('+')) {
          // in the case of unresolved merge we have 2 revisions at once, both current, so with "+"
          // 9f2e6c02913c+b311eb4eb004+ 186+183+
          List<String> chsets = StringUtil.split(changesets, "+");
          List<String> revs = StringUtil.split(revisions, "+");
          return Couple.of(HgRevisionNumber.getInstance(revs.get(0) + "+", chsets.get(0) + "+"),
                           HgRevisionNumber.getInstance(revs.get(1) + "+", chsets.get(1) + "+"));
        } else {
          return Couple.of(HgRevisionNumber.getInstance(revisions, changesets), null);
        }
      }
    }
    return Couple.of(HgRevisionNumber.NULL_REVISION_NUMBER, null);
  }

  /**
   * Returns the list of revisions returned by one mercurial commands (parents, identify, tip).
   * Executed a command on the whole repository or on the given file.
   * During a merge, the returned list contains 2 revision numbers. The order of these numbers is
   * important: the first parent was the parent of the working directory from <em>before</em>
   * the merge, the second parent is the changeset that was merged in.
   * @param repo     repository to execute on.
   * @param command  command to execute.
   * @param file     file which revisions are wanted. If <code><b>null</b></code> then repository revisions are considered.
   * @param revision revision to execute on. If <code><b>null</b></code> then executed without the '-r' parameter, i.e. on the latest revision.
   * @param silent   pass true if this command shouldn't be mentioned in the VCS console.
   * @return List of revisions.
   */
  public @NotNull List<HgRevisionNumber> getRevisions(@NotNull VirtualFile repo,
                                              @NotNull String command,
                                              @Nullable FilePath file,
                                              @Nullable HgRevisionNumber revision,
                                              boolean silent) {
    final List<String> args = new LinkedList<>();
    args.add("--template");
    args.add(HgChangesetUtil.makeTemplate("{rev}", "{node}"));
    if (revision != null) {
      args.add("-r");
      args.add(revision.getChangeset());
    }
    if (file != null) { // NB: this must be the last argument
      args.add(HgUtil.getOriginalFileName(file, ChangeListManager.getInstance(myProject)).getPath());
    }
    final HgCommandExecutor executor = new HgCommandExecutor(myProject);
    executor.setSilent(silent);
    final HgCommandResult result = executor.executeInCurrentThread(repo, command, args);

    if (result == null) {
      return new ArrayList<>(0);
    }

    final List<String> lines = new ArrayList<>();
    for (String line : result.getRawOutput().split(HgChangesetUtil.CHANGESET_SEPARATOR)) {
      if (!line.trim().isEmpty()) {     // filter out empty lines
        lines.add(line);
      }
    }
    if (lines.isEmpty()) {
      return new ArrayList<>();
    }

    final List<HgRevisionNumber> revisions = new ArrayList<>(lines.size());
    for(String line: lines) {
      final List<String> parts = StringUtil.split(line, HgChangesetUtil.ITEM_SEPARATOR);
      if (parts.size() < 2) {
        LOG.error("getRevisions output parse error in line [" + line + "]\n All lines: \n" + lines);
        continue;
      }
      revisions.add(HgRevisionNumber.getInstance(parts.get(0), parts.get(1)));
    }
    return revisions;
  }
}
