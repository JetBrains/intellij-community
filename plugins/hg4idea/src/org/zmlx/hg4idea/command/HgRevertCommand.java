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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsFileUtil;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.execution.HgCommandExecutor;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class HgRevertCommand {

  private final Project project;

  public HgRevertCommand(Project project) {
    this.project = project;
  }

  public void execute(VirtualFile repo, Collection<FilePath> files, HgRevisionNumber vcsRevisionNumber, boolean backupFile) {
    final List<String> options = new LinkedList<String>();
    if (vcsRevisionNumber != null && !HgRevisionNumber.NULL_REVISION_NUMBER.equals(vcsRevisionNumber)) {
      options.add("--rev");
      if (!StringUtil.isEmptyOrSpaces(vcsRevisionNumber.getChangeset())) {
        options.add(vcsRevisionNumber.getChangeset());
      }
      else {
        options.add(vcsRevisionNumber.getRevision());
      }
    }
    if (!backupFile) {
      options.add("--no-backup");
    }

    for (List<String> chunk : VcsFileUtil.chunkPaths(repo, files)) {
      List<String> args = new LinkedList<String>();
      args.addAll(options);
      args.addAll(chunk);
      new HgCommandExecutor(project).executeInCurrentThread(repo, "revert", args);
    }
    project.getMessageBus().syncPublisher(HgVcs.BRANCH_TOPIC).update(project, null);
  }
}
