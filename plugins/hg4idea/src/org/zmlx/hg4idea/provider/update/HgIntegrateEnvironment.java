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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.update.*;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.HgVcsMessages;
import org.zmlx.hg4idea.command.HgMergeCommand;
import org.zmlx.hg4idea.command.HgTagBranch;
import org.zmlx.hg4idea.ui.HgIntegrateDialog;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class HgIntegrateEnvironment implements UpdateEnvironment {

  private final Project project;
  private HgIntegrateDialog mergeDialog;


  public HgIntegrateEnvironment(Project project) {
    this.project = project;
  }

  public void fillGroups(UpdatedFiles updatedFiles) {
  }

  @NotNull
  public UpdateSession updateDirectories(@NotNull FilePath[] contentRoots,
    final UpdatedFiles updatedFiles, ProgressIndicator progressIndicator,
    @NotNull Ref<SequentialUpdatesContext> context) {
    List<VcsException> exceptions = new LinkedList<VcsException>();
    final VirtualFile repo = mergeDialog.getRepository();
    progressIndicator.setText(
      HgVcsMessages.message("hg4idea.progress.integrating", repo.getPath())
    );

    HgMergeCommand hgMergeCommand = new HgMergeCommand(project, repo);

    HgRevisionNumber incomingRevision = null;
    HgTagBranch branch = mergeDialog.getBranch();
    if (branch != null) {
      hgMergeCommand.setBranch(branch.getName());
      incomingRevision = branch.getHead();
    }

    HgTagBranch tag = mergeDialog.getTag();
    if (tag != null) {
      hgMergeCommand.setRevision(tag.getName());
      incomingRevision = tag.getHead();
    }

    String revision = mergeDialog.getRevision();
    if (revision != null) {
      hgMergeCommand.setRevision(revision);
      incomingRevision = HgRevisionNumber.getLocalInstance(revision);
    }

    HgRevisionNumber otherHead = mergeDialog.getOtherHead();
    if (otherHead != null) {
      hgMergeCommand.setRevision(otherHead.getRevision());
      incomingRevision = otherHead;
    }

    if (incomingRevision != null) {
      try {
        progressIndicator.setText2(HgVcsMessages.message("hg4idea.progress.merging"));
        String warnings = new HgHeadMerger(project, hgMergeCommand)
          .merge(repo, updatedFiles, incomingRevision).getWarnings();

        if (!StringUtils.isBlank(warnings)) {
          //noinspection ThrowableInstanceNeverThrown
          VcsException warning = new VcsException(warnings);
          warning.setIsWarning(true);
          exceptions.add(warning);
        }

        ApplicationManager.getApplication().invokeAndWait(new Runnable() {
          public void run() {
            new HgConflictResolver(project, updatedFiles).resolve(repo);
          }
        }, ModalityState.defaultModalityState());

      } catch (VcsException e) {
        exceptions.add(e);
      }
    } else {
      //noinspection ThrowableInstanceNeverThrown
      exceptions.add(new VcsException(HgVcsMessages.message("hg4idea.error.invalidTarget")));
    }

    return new UpdateSessionAdapter(exceptions, false);
  }

  public Configurable createConfigurable(Collection<FilePath> roots) {
    mergeDialog = new HgIntegrateDialog(project, roots);
    return mergeDialog;
  }

  public boolean validateOptions(Collection<FilePath> roots) {
    return true;
  }

}
