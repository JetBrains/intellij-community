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

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.project.Project;
import org.zmlx.hg4idea.command.HgTagCreateCommand;
import org.zmlx.hg4idea.command.HgCommandException;
import org.zmlx.hg4idea.command.HgCommandResult;
import org.zmlx.hg4idea.ui.HgTagDialog;

import java.util.Collection;

public class HgCreateTagAction extends HgAbstractGlobalAction {

  protected HgGlobalCommandBuilder getHgGlobalCommandBuilder(final Project project) {
    return new HgGlobalCommandBuilder() {
      public HgGlobalCommand build(Collection<VirtualFile> repos) {
        HgTagDialog dialog = new HgTagDialog(project);
        dialog.setRoots(repos);
        dialog.show();
        if (dialog.isOK()) {
          return buildCommand(dialog, project);
        }
        return null;
      }
    };
  }

  private HgGlobalCommand buildCommand(final HgTagDialog dialog, final Project project) {
    return new HgGlobalCommand() {
      public VirtualFile getRepo() {
        return dialog.getRepository();
      }

      public void execute() throws HgCommandException {
        HgCommandResult result =
          new HgTagCreateCommand(project, dialog.getRepository(), dialog.getTagName()).execute();

        new HgCommandResultNotifier(project).process(result);
      }
    };
  }

}
