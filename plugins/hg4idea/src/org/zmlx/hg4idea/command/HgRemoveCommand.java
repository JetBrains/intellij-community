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
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.HgUtil;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * A wrapper for the 'hg remove' command.
 */
public class HgRemoveCommand {

  private final Project myProject;

  public HgRemoveCommand(Project project) {
    myProject = project;
  }

  /**
   * Removes given files from their Mercurial repositories.
   * @param hgFiles files to be removed.
   */
  public void execute(@NotNull HgFile... hgFiles) {
    execute(Arrays.asList(hgFiles));
  }

  /**
   * Removes given files from their Mercurial repositories.
   * @param hgFiles files to be removed.
   */
  public void execute(@NotNull Collection<HgFile> hgFiles) {
    for( Map.Entry<VirtualFile, List<String>> entry : HgUtil.getRelativePathsByRepository(hgFiles).entrySet()) {
      List<String> filePaths = entry.getValue();
      filePaths.add(0, "--after");
      HgCommandService.getInstance(myProject).execute(entry.getKey(), "remove", filePaths);
    }
  }

}
