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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.execution.HgCommandExecutor;
import org.zmlx.hg4idea.execution.HgCommandResult;

import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HgBranchesCommand {

  private static final Pattern BRANCH_LINE = Pattern.compile("(.+)\\s([0-9]+):([0-9a-f]+).*");
  private static final int NAME_INDEX = 1;

  private final Project project;
  private final VirtualFile repo;

  public HgBranchesCommand(Project project, @NotNull VirtualFile repo) {
    this.project = project;
    this.repo = repo;
  }

  @Nullable
  public HgCommandResult collectBranches() {
    return new HgCommandExecutor(project).executeInCurrentThread(repo, "branches", null);
  }

  @NotNull
  public static Set<String> collectNames(@NotNull HgCommandResult result) {
    Set<String> branches = new TreeSet<>();
    for (final String line : result.getOutputLines()) {
      Matcher matcher = BRANCH_LINE.matcher(line);
      if (matcher.matches()) {
        branches.add(matcher.group(NAME_INDEX).trim());
      }
    }
    return branches;
  }
}
