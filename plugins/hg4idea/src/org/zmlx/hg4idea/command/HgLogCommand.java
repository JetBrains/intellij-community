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
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgFile;

import java.util.LinkedList;
import java.util.List;

public class HgLogCommand extends HgRevisionsCommand {
  private boolean includeRemoved;
  private boolean followCopies;
  private boolean logFile = true;

  public HgLogCommand(Project project) {
    super(project);
  }

  public void setIncludeRemoved(boolean includeRemoved) {
    this.includeRemoved = includeRemoved;
  }

  public void setFollowCopies(boolean followCopies) {
    this.followCopies = followCopies;
  }

  public void setLogFile(boolean logFile) {
    this.logFile = logFile;
  }

  @Override
  public HgCommandResult execute(HgCommandService service, VirtualFile repo,
    String template, int limit, HgFile hgFile) {
    List<String> arguments = new LinkedList<String>();
    if (followCopies) {
      arguments.add("--follow");
    }
    if (includeRemoved) {
      // There is a bug in mercurial that causes --follow --removed <file> to cause
      // an error (http://mercurial.selenic.com/bts/issue2139). Avoid this combination
      // for now, preferring to use --follow over --removed. 
      if (!(followCopies && logFile)) {
        arguments.add("--removed");
      }
    }
    arguments.add("--template");
    arguments.add(template);
    if (limit != -1) {
      arguments.add("--limit");
      arguments.add(String.valueOf(limit));
    }
    if (logFile) {
      arguments.add(hgFile.getRelativePath());
    }
    return service.execute(repo, "log", arguments);
  }

}
