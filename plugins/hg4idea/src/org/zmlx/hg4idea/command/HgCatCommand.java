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
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.execution.HgCommandExecutor;
import org.zmlx.hg4idea.execution.HgCommandResult;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class HgCatCommand {

  private final Project myProject;

  public HgCatCommand(Project project) {
    myProject = project;
  }

  @Nullable
  public String execute(HgFile hgFile, HgRevisionNumber vcsRevisionNumber, Charset charset) {
    final List<String> arguments = createArguments(vcsRevisionNumber, hgFile.getRelativePath());
    final HgCommandExecutor executor = new HgCommandExecutor(myProject);
    executor.setOptions(Collections.<String>emptyList());
    executor.setSilent(true);
    executor.setCharset(charset);
    final HgCommandResult result = executor.executeInCurrentThread(hgFile.getRepo(), "cat", arguments);

    if (result == null) { // in case of error
      return null;
    }
    if (result.getExitValue() == 1) { // file not found in given revision
      return null;
    }
    return result.getRawOutput();
  }

  private static List<String> createArguments(HgRevisionNumber vcsRevisionNumber, String fileName) {
    final List<String> arguments = new LinkedList<String>();
    if (vcsRevisionNumber != null) {
      arguments.add("--rev");
      if (!StringUtil.isEmptyOrSpaces(vcsRevisionNumber.getChangeset())) {
        arguments.add(vcsRevisionNumber.getChangeset());
      } else {
        arguments.add(vcsRevisionNumber.getRevision());
      }
    }
    arguments.add(fileName);
    return arguments;
  }

}
