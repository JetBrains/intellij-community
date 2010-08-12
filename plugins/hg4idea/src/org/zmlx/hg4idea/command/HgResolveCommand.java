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
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.commons.lang.StringUtils;
import org.zmlx.hg4idea.HgFile;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class HgResolveCommand {

  private static final int ITEM_COUNT = 3;

  private final Project myProject;

  public HgResolveCommand(Project project) {
    myProject = project;
  }

  public Map<HgFile, HgResolveStatusEnum> list(VirtualFile repo) {
    if (repo == null) {
      return Collections.emptyMap();
    }
    final HgCommandResult result = HgCommandService.getInstance(myProject).execute(repo, "resolve", Arrays.asList("--list"));
    if (result == null) {
      return Collections.emptyMap();
    }

    final Map<HgFile, HgResolveStatusEnum> resolveStatus = new HashMap<HgFile, HgResolveStatusEnum>();
    for (String line : result.getOutputLines()) {
      if (StringUtils.isBlank(line) || line.length() < ITEM_COUNT) {
        continue;
      }
      final HgResolveStatusEnum status = HgResolveStatusEnum.valueOf(line.charAt(0));
      if (status != null) {
        File ioFile = new File(repo.getPath(), line.substring(2));
        resolveStatus.put(new HgFile(repo, ioFile), status);
      }
    }
    return resolveStatus;
  }

  public void markResolved(VirtualFile repo, VirtualFile path) {
    HgCommandService.getInstance(myProject).execute(repo, "resolve", Arrays.asList("--mark", path.getPath()));
  }

  public void markResolved(VirtualFile repo, FilePath path) {
    HgCommandService.getInstance(myProject).execute(repo, "resolve", Arrays.asList("--mark", path.getPath()));
  }

}