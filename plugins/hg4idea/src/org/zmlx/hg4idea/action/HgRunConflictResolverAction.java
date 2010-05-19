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
package org.zmlx.hg4idea.action;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.command.HgHeadsCommand;
import org.zmlx.hg4idea.command.HgTagBranchCommand;
import org.zmlx.hg4idea.provider.update.HgConflictResolver;
import org.zmlx.hg4idea.ui.HgRunConflictResolverDialog;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class HgRunConflictResolverAction extends HgAbstractGlobalAction {

  protected HgGlobalCommandBuilder getHgGlobalCommandBuilder(final Project project) {
    return new HgGlobalCommandBuilder() {
      public HgGlobalCommand build(Collection<VirtualFile> repos) {
        HgRunConflictResolverDialog dialog = new HgRunConflictResolverDialog(project);
        dialog.setRoots(repos);
        dialog.show();

        if (dialog.isOK()) {
          return buildCommand(dialog, project);
        }
        return null;
      }
    };
  }

  private HgGlobalCommand buildCommand(HgRunConflictResolverDialog dialog, final Project project) {
    final VirtualFile repository = dialog.getRepository();
    return new HgGlobalCommand() {
      public VirtualFile getRepo() {
        return repository;
      }

      public void execute() {
        String currentBranch = new HgTagBranchCommand(project, repository).getCurrentBranch();
        if (currentBranch == null) {
          return;
        }
        List<HgRevisionNumber> heads =
          new HgHeadsCommand(project, repository).execute(currentBranch);

        new HgConflictResolver(project, Collections.max(heads), Collections.min(heads))
          .resolve(repository);
      }
    };
  }

}
