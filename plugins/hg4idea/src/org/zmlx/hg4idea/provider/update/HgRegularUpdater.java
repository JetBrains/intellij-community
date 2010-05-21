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

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.commons.lang.StringUtils;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.HgVcsMessages;
import org.zmlx.hg4idea.command.HgHeadsCommand;
import org.zmlx.hg4idea.command.HgMergeCommand;
import org.zmlx.hg4idea.command.HgPullCommand;
import org.zmlx.hg4idea.command.HgShowConfigCommand;
import org.zmlx.hg4idea.command.HgTagBranchCommand;

import java.util.List;

class HgRegularUpdater implements HgUpdater {

  private final Project project;
  private final VirtualFile repository;

  public HgRegularUpdater(Project project, VirtualFile repository) {
    this.project = project;
    this.repository = repository;
  }

  public void update(UpdatedFiles updatedFiles, ProgressIndicator indicator)
    throws VcsException {
    indicator.setText(
      HgVcsMessages.message("hg4idea.progress.updating", repository.getPath())
    );

    HgShowConfigCommand configCommand = new HgShowConfigCommand(project);
    String defaultPath = configCommand.getDefaultPath(repository);

    if (StringUtils.isBlank(defaultPath)) {
      VcsException e = new VcsException(
        HgVcsMessages.message("hg4idea.warning.no-default-update-path", repository.getPath())
      );
      e.setIsWarning(true);
      throw e;
    }

    pull(repository, indicator);

    String currentBranch = new HgTagBranchCommand(project, repository).getCurrentBranch();
    if (StringUtils.isBlank(currentBranch)) {
      throw new VcsException(
        HgVcsMessages.message("hg4idea.update.error.currentBranch")
      );
    }

    //count heads in repository
    List<HgRevisionNumber> heads = new HgHeadsCommand(project, repository).execute(currentBranch);
    indicator.setText2(HgVcsMessages.message("hg4idea.progress.countingHeads"));
    if (heads.size() < 2) {
      return;
    }

    if (heads.size() > 2) {
      throw new VcsException(
        HgVcsMessages.message("hg4idea.update.error.manyHeads", heads.size())
      );
    }

    new HgHeadMerger(project, new HgMergeCommand(project, repository))
      .merge(repository, updatedFiles, indicator, heads.get(heads.size() - 1));
  }

  private void pull(VirtualFile repo, ProgressIndicator indicator)
    throws VcsException {
    indicator.setText2(HgVcsMessages.message("hg4idea.progress.pull.with.update"));
    HgPullCommand hgPullCommand = new HgPullCommand(project, repo);
    hgPullCommand.setSource(new HgShowConfigCommand(project).getDefaultPath(repo));
    hgPullCommand.setUpdate(true);
    hgPullCommand.setRebase(false);
    hgPullCommand.execute();
  }

}
