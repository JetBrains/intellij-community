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
import org.zmlx.hg4idea.HgFile;

import java.util.List;
import java.util.LinkedList;

public class HgLogCommand extends HgRevisionsCommand {

  private boolean followCopies;
  private boolean logFile = true;

  public HgLogCommand(Project project) {
    super(project);
  }

  public void setFollowCopies(boolean followCopies) {
    this.followCopies = followCopies;
  }

  public void setLogFile(boolean logFile) {
    this.logFile = logFile;
  }

  @Override
  protected HgCommandResult execute(HgCommandService service, VirtualFile repo,
    String template, int limit, HgFile hgFile) {
    List<String> arguments = new LinkedList<String>();
    if (followCopies) {
      arguments.add("--follow");
    }
    arguments.add("--template");
    arguments.add(template);
    arguments.add("--limit");
    arguments.add(String.valueOf(limit));
    if (logFile) {
      arguments.add(hgFile.getRelativePath());
    }
    return service.execute(repo, "log", arguments);
  }

}
