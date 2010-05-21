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

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HgTagBranchCommand {

  private static final Pattern BRANCH_LINE = Pattern.compile("(.+)\\s([0-9]+):([0-9a-f]+).*");
  private static final int NAME_INDEX = 1;
  private static final int REVISION_INDEX = 2;
  private static final int CHANGESET_INDEX = 3;

  private final Project project;
  private final VirtualFile repo;

  public HgTagBranchCommand(Project project, @NotNull VirtualFile repo) {
    this.project = project;
    this.repo = repo;
  }

  @Nullable
  public String getCurrentBranch() {
    HgCommandResult result = HgCommandService.getInstance(project).execute(repo, "branch", null);
    List<String> output = result.getOutputLines();
    if (output == null || output.isEmpty()) {
      return null;
    }
    return StringUtils.trim(output.get(0));
  }

  public List<HgTagBranch> listBranches() {
    return tokenize(HgCommandService.getInstance(project).execute(repo, "branches", null));
  }

  public List<HgTagBranch> listTags() {
    return tokenize(HgCommandService.getInstance(project).execute(repo, "tags", null));
  }

  private List<HgTagBranch> tokenize(HgCommandResult result) {
    List<HgTagBranch> branches = new LinkedList<HgTagBranch>();
    for (final String line : result.getOutputLines()) {
      Matcher matcher = BRANCH_LINE.matcher(line);
      if (matcher.matches()) {
        HgRevisionNumber hgRevisionNumber = HgRevisionNumber.getInstance(
          matcher.group(REVISION_INDEX), matcher.group(CHANGESET_INDEX)
        );
        branches.add(new HgTagBranch(matcher.group(NAME_INDEX).trim(), line,  hgRevisionNumber));
      }
    }
    return branches;
  }

}
