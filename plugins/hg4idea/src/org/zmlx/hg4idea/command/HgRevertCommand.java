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
import org.apache.commons.lang.StringUtils;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.HgRevisionNumber;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class HgRevertCommand {

  private final Project project;

  public HgRevertCommand(Project project) {
    this.project = project;
  }

  public void execute(HgFile hgFile, HgRevisionNumber vcsRevisionNumber, boolean backupFile) {
    List<String> arguments = new LinkedList<String>();

    if (vcsRevisionNumber != null) {
      arguments.add("--rev");
      if (StringUtils.isNotBlank(vcsRevisionNumber.getChangeset())) {
        arguments.add(vcsRevisionNumber.getChangeset());
      } else {
        arguments.add(vcsRevisionNumber.getRevision());
      }
    }

    if (!backupFile) {
      arguments.add("--no-backup");
    }

    arguments.addAll(Arrays.asList(hgFile.getRelativePath()));

    HgCommandService.getInstance(project).execute(hgFile.getRepo(), "revert", arguments);
  }
}
