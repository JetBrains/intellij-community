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
import org.apache.commons.lang.StringUtils;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.command.*;
import org.zmlx.hg4idea.provider.update.HgConflictResolver;
import org.zmlx.hg4idea.ui.HgPullDialog;

import java.util.Collection;
import java.util.Map;

public class HgMqRebaseAction extends HgAbstractGlobalAction {

  protected HgGlobalCommandBuilder getHgGlobalCommandBuilder(final Project project) {
    return new HgGlobalCommandBuilder() {
      public HgGlobalCommand build(Collection<VirtualFile> repos) {
        HgPullDialog dialog = new HgPullDialog(project);
        dialog.setRoots(repos);
        dialog.show();
        if (dialog.isOK()) {
          return buildCommand(dialog, project);
        }
        return null;
      }
    };
  }

  private HgGlobalCommand buildCommand(final HgPullDialog dialog, final Project project) {
    final VirtualFile repository = dialog.getRepository();
    return new HgGlobalCommand() {
      public VirtualFile getRepo() {
        return repository;
      }

      public void execute() {
        HgMQCommand mqCommand = new HgMQCommand(project);
        boolean notFoundAppliedPatches = mqCommand.qapplied(repository).isEmpty();
        if (notFoundAppliedPatches) {
          return;
        }

        HgPullCommand pullCommand = new HgPullCommand(project, repository);
        pullCommand.setSource(dialog.getSource());
        pullCommand.setRebase(true);
        pullCommand.setUpdate(false);
        new HgCommandResultNotifier(project).process(pullCommand.execute());

        String currentBranch = new HgTagBranchCommand(project, repository).getCurrentBranch();
        if (StringUtils.isBlank(currentBranch)) {
          return;
        }

        new HgConflictResolver(project).resolve(repository);

        HgResolveCommand resolveCommand = new HgResolveCommand(project);
        Map<HgFile, HgResolveStatusEnum> status = resolveCommand.list(repository);

        if (status.containsValue(HgResolveStatusEnum.UNRESOLVED)) {
          return;
        }

        new HgRebaseCommand(project, repository).continueRebase();
      }
    };
  }

}
