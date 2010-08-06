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
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgRevisionNumber;

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
    final Pair<HgRevisionNumber, HgRevisionNumber> parents = parents(repo, null);
    final List<HgRevisionNumber> result = new ArrayList<HgRevisionNumber>(2);
    result.add(parents.first);
    result.add(parents.second);
    return result;
  }

  /**
   * @see #parents(com.intellij.openapi.vfs.VirtualFile, com.intellij.openapi.vfs.VirtualFile, org.zmlx.hg4idea.HgRevisionNumber)
   */
  @NotNull
  public Pair<HgRevisionNumber, HgRevisionNumber> parents(@NotNull VirtualFile repo, @Nullable VirtualFile file) {
    return parents(repo, file, null);
  }

  /**
   * Parent(s) of the given revision of the given file.
   * @param repo     repository to work on.
   * @param file     file which revision's parents we are interested in. If null, the history of the whole repository is considered.
   * @param revision revision number which parent is wanted. If null, the last revision is taken. 
   * @return One or two (in case of a merge commit) parents of the given revision. Or even zero in case of a fresh repository.
   *         So one should check pair elements for null.
   */
  @NotNull
  public Pair<HgRevisionNumber, HgRevisionNumber> parents(@NotNull VirtualFile repo, @Nullable VirtualFile file, @Nullable HgRevisionNumber revision) {
    final List<HgRevisionNumber> revisions = getRevisions(repo, "parents", file, revision);
    switch (revisions.size()) {
      case 1: return Pair.create(revisions.get(0), null);
      case 2: return Pair.create(revisions.get(1), null);
      default: return Pair.create(null, null);
    }
  }

  @Nullable
  public HgRevisionNumber firstParent(@NotNull VirtualFile repo) {
    List<HgRevisionNumber> parents = parents(repo);
    if (parents.isEmpty()) {
      //this is possible when we have a freshly initialized mercurial repository
      return null;
    }
    else {
      return parents.get(0);
    }
  }

  @Nullable
  public HgRevisionNumber tip(@NotNull VirtualFile repo) {
    List<HgRevisionNumber> tips = getRevisions(repo, "tip", null, null);
    if (tips.size() > 1) {
      throw new IllegalStateException("There cannot be multiple tips");
    }
    if(!tips.isEmpty()) {
      return tips.get(0);
    }
    else return null;
  }

  @Nullable
  public HgRevisionNumber identify(@NotNull VirtualFile repo) {
    HgCommandService commandService = HgCommandService.getInstance(myProject);
    HgCommandResult result = commandService.execute(
      repo, "identify", Arrays.asList("--num", "--id")
    );
    if (result == null) {
      return null;
    }
    final List<String> lines = result.getOutputLines();
    if (lines != null && !lines.isEmpty()) {
      String[] parts = StringUtils.split(lines.get(0), ' ');
      if (parts.length >= 2) {
        return HgRevisionNumber.getInstance(parts[1], parts[0]);
      }
    }
    return null;
  }

  /**
   * Returns the list of revisions returned by one mercurial commands (parents, identify, tip).
   * Executed a command on the whole repository or on the given file.
   * @param repo     repository to execute on.
   * @param command  command to execute.
   * @param file     file which revisions are wanted. If <code><b>null</b></code> then repository revisions are considered.
   * @param revision revision to execute on. If <code><b>null</b></code> then executed without the '-r' parameter, i.e. on the latest revision.
   * @return List of revisions.
   */
  @NotNull
  private List<HgRevisionNumber> getRevisions(@NotNull VirtualFile repo, @NotNull String command, @Nullable VirtualFile file, @Nullable HgRevisionNumber revision) {
    final List<String> args = new LinkedList<String>();
    args.add("--template");
    args.add("{rev}|{node|short}\\n");
    if (revision != null) {
      args.add("-r");
      args.add(revision.getChangeset());
    }
    if (file != null) { // NB: this must be the last argument
      args.add(file.getPath());
    }
    final HgCommandResult result = HgCommandService.getInstance(myProject).execute(repo, command, args);

    if (result == null) {
      return new ArrayList<HgRevisionNumber>(0);
    }
    final List<String> lines = result.getOutputLines();
    final List<HgRevisionNumber> revisions = new ArrayList<HgRevisionNumber>(lines.size());
    for(String line: lines) {
      final String[] parts = StringUtils.split(line, '|');
      revisions.add(HgRevisionNumber.getInstance(parts[0], parts[1]));
    }
    
    return revisions;
  }

}
