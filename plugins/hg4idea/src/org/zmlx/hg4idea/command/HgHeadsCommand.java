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
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgRevisionNumber;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HgHeadsCommand {

  private static final Logger LOG = Logger.getInstance(HgHeadsCommand.class.getName());

  private final Project project;
  private final VirtualFile repo;

  public HgHeadsCommand(Project project, @NotNull VirtualFile repo) {
    this.project = project;
    this.repo = repo;
  }

  public List<HgRevisionNumber> execute(String branch) {
    HgCommandService command = HgCommandService.getInstance(project);

    HgCommandResult result = command.execute(repo, "heads",
      Arrays.asList("--template", "{rev}|{node|short}\\n", branch));

    List<HgRevisionNumber> heads = new ArrayList<HgRevisionNumber>();
    for (String line : result.getOutputLines()) {
      try {
        String[] parts = StringUtils.split(line, '|');
        heads.add(HgRevisionNumber.getInstance(parts[0], parts[1]));
      } catch (NumberFormatException e) {
        LOG.warn("Unexpected head line '" + line + "'");
      }
    }
    return heads;
  }

}
