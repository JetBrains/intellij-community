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
import org.apache.commons.lang.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class HgShowConfigCommand {

  private final Project project;

  public HgShowConfigCommand(Project project) {
    this.project = project;
  }

  public String getDefaultPath(VirtualFile repo) {
    return execute(repo).get("paths.default");
  }

  public Map<String, String> execute(VirtualFile repo) {
    if (repo == null) {
      return Collections.emptyMap();
    }

    HgCommandResult result =
      HgCommandService.getInstance(project).execute(repo, "showconfig", null);

    Map<String, String> options = new HashMap<String, String>();
    for (String line : result.getOutputLines()) {
      String[] option = StringUtils.splitPreserveAllTokens(line, '=');
      if (option.length == 2) {
        options.put(option[0].trim(), option[1].trim());
      }
    }
    return options;
  }

}
