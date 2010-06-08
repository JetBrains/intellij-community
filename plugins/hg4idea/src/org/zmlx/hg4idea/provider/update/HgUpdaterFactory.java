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
package org.zmlx.hg4idea.provider.update;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.command.HgMQCommand;

class HgUpdaterFactory {

  private final Project project;

  public HgUpdaterFactory(Project project) {
    this.project = project;
  }

  HgUpdater buildUpdater(@NotNull VirtualFile repository, @NotNull HgUpdater.UpdateConfiguration configuration) throws VcsException {
    HgMQCommand mqCommand = new HgMQCommand(project);
    boolean foundAppliedPatches = !mqCommand.qapplied(repository).isEmpty();
    if (foundAppliedPatches) {
      throw new VcsException("Cannot update with applied MQ patches, please use rebase");
    } else {
      return new HgRegularUpdater(project, repository, configuration);
    }
  }

}
