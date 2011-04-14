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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ObjectsConvertor;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.util.HgUtil;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.execution.HgCommandExecutor;
import org.zmlx.hg4idea.util.HgChangesetUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Commands to get revision numbers. These are: parents, id, tip.
 */
public class HgWorkingCopyRevisionsCommand {

  private final Project myProject;

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
  public Pair<HgRevisionNumber, HgRevisionNumber> parents(@NotNull VirtualFile repo, @Nullable VirtualFile file) {
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
  public Pair<HgRevisionNumber, HgRevisionNumber> parents(@NotNull VirtualFile repo, @Nullable VirtualFile file, @Nullable HgRevisionNumber revision) {
    return parents(repo, ObjectsConvertor.VIRTUAL_FILEPATH.convert(file), revision);
  }
  
  /**
   * @see #parents(VirtualFile, FilePath, HgRevisionNumber)
   */
  @NotNull
  public Pair<HgRevisionNumber, HgRevisionNumber> parents(@NotNull VirtualFile repo, @Nullable FilePath file) {
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
  public Pair<HgRevisionNumber, HgRevisionNumber> parents(@NotNull VirtualFile repo, @Nullable FilePath file, @Nullable HgRevisionNumber revision) {
    final List<HgRevisionNumber> revisions = getRevisions(repo, "parents", file, revision, true);
    switch (revisions.size()) {
      case 1: return Pair.create(revisions.get(0), null);
      case 2: return Pair.create(revisions.get(0), revisions.get(1));
      default: return Pair.create(null, null);
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

  @Nullable
  public HgRevisionNumber identify(@NotNull VirtualFile repo) {
    HgCommandExecutor commandExecutor = new HgCommandExecutor(myProject);
    commandExecutor.setSilent(true);
    HgCommandResult result = commandExecutor.executeInCurrentThread(repo, "identify", Arrays.asList("--num", "--id"));
    if (result == null) {
      return HgRevisionNumber.NULL_REVISION_NUMBER;
    }
    final List<String> lines = result.getOutputLines();
    if (lines != null && !lines.isEmpty()) {
      String[] parts = StringUtils.split(lines.get(0), ' ');
      if (parts.length >= 2) {
        return HgRevisionNumber.getInstance(parts[1], parts[0]);
      }
    }
    return HgRevisionNumber.NULL_REVISION_NUMBER;
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
  private List<HgRevisionNumber> getRevisions(@NotNull VirtualFile repo,
                                              @NotNull String command,
                                              @Nullable FilePath file,
                                              @Nullable HgRevisionNumber revision,
                                              boolean silent) {
    final List<String> args = new LinkedList<String>();
    args.add("--template");
    args.add(HgChangesetUtil.makeTemplate("{rev}", "{node|short}"));
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
      return new ArrayList<HgRevisionNumber>(0);
    }
    final List<String> lines = Arrays.asList(result.getRawOutput().split(HgChangesetUtil.CHANGESET_SEPARATOR));
    final List<HgRevisionNumber> revisions = new ArrayList<HgRevisionNumber>(lines.size());
    for(String line: lines) {
      final String[] parts = StringUtils.split(line, HgChangesetUtil.ITEM_SEPARATOR);
      revisions.add(HgRevisionNumber.getInstance(parts[0], parts[1]));
    }
    
    return revisions;
  }

}
