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
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgRevisionNumber;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HgWorkingCopyRevisionsCommand {

  private final Project project;

  public HgWorkingCopyRevisionsCommand(Project project) {
    this.project = project;
  }

  public List<HgRevisionNumber> parents(@NotNull VirtualFile repo) {
    return getRevisions(repo, "parents");
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

  public HgRevisionNumber tip(@NotNull VirtualFile repo) {
    List<HgRevisionNumber> tips = getRevisions(repo, "tip");
    if (tips.size() > 1) {
      throw new IllegalStateException("There cannot be multiple tips");
    }
    if(!tips.isEmpty()) {
      return tips.get(0);
    }
    else return null;
  }

  public HgRevisionNumber identify(@NotNull VirtualFile repo) {
    HgCommandService commandService = HgCommandService.getInstance(project);
    HgCommandResult result = commandService.execute(
      repo, "identify", Arrays.asList("--num", "--id")
    );
    List<String> lines = result.getOutputLines();
    if (!lines.isEmpty()) {
      String[] parts = StringUtils.split(lines.get(0), ' ');
      if (parts.length >= 2) {
        return HgRevisionNumber.getInstance(parts[1], parts[0]);
      }
    }
    return null;
  }

  private List<HgRevisionNumber> getRevisions(VirtualFile repo, String command) {
    HgCommandService commandService = HgCommandService.getInstance(project);
    HgCommandResult result = commandService.execute(
      repo, command, Arrays.asList("--template", "{rev}|{node|short}\\n")
    );
    List<String> lines = result.getOutputLines();
    List<HgRevisionNumber> revisions = new ArrayList<HgRevisionNumber>(lines.size());
    
    for(String line: lines) {
      String[] parts = StringUtils.split(line, '|');
      revisions.add(HgRevisionNumber.getInstance(parts[0], parts[1]));
    }
    
    return revisions;
  }

}
