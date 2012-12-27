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
import com.intellij.vcsUtil.VcsFileUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.zmlx.hg4idea.execution.HgCommandExecutor;

import java.util.Arrays;
import java.util.Collections;

public class HgCopyCommand {

  private final Project myProject;

  public HgCopyCommand(Project project) {
    myProject = project;
  }

  public void execute(VirtualFile source, VirtualFile target) {
    VirtualFile sourceRepo = VcsUtil.getVcsRootFor(myProject, source);
    VirtualFile targetRepo = VcsUtil.getVcsRootFor(myProject, target);
    HgCommandExecutor executor = new HgCommandExecutor(myProject, VcsFileUtil.relativeOrFullPath(sourceRepo, source));
    if (sourceRepo != null && targetRepo != null && sourceRepo.equals(targetRepo)) {
      executor.execute(sourceRepo, "copy", Arrays.asList("--after",
                                                         VcsFileUtil.relativeOrFullPath(sourceRepo, source),
                                                         VcsFileUtil.relativeOrFullPath(targetRepo, target)), null);
    } else {
      // copying from one repository to another => 'hg add' in new repo
      if (targetRepo != null) {
        new HgAddCommand(myProject).execute(Collections.singleton(target));
      }
    }
  }

}
