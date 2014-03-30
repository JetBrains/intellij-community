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
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.execution.HgCommandExecutor;
import org.zmlx.hg4idea.execution.HgCommandResult;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HgShowConfigCommand {

  @NotNull private final Project project;

  public HgShowConfigCommand(@NotNull Project project) {
    this.project = project;
  }

  @NotNull
  public Map<String, Map<String, String>> execute(@Nullable VirtualFile repo) {
    if (repo == null) {
      return Collections.emptyMap();
    }

    final HgCommandExecutor executor = new HgCommandExecutor(project);
    executor.setSilent(true);
    HgCommandResult result = executor.executeInCurrentThread(repo, "showconfig", null);

    if (result == null) {
      return Collections.emptyMap();
    }

    Map<String, Map<String, String>> configMap = new HashMap<String, Map<String, String>>();
    for (String line : result.getOutputLines()) {
      List<String> option = StringUtil.split(line, "=", true, false);
      if (option.size() == 2) {
        String sectionAndName = option.get(0).trim();
        String value = option.get(1).trim();
        int dotIndex = sectionAndName.indexOf('.');

        if (dotIndex > 0) {
          String sectionName = sectionAndName.substring(0, dotIndex);
          String optionName = sectionAndName.substring(dotIndex + 1, sectionAndName.length());
          if (configMap.containsKey(sectionName)) {
            configMap.get(sectionName).put(optionName, value);
          }
          else {
            HashMap<String, String> sectionMap = new HashMap<String, String>();
            sectionMap.put(optionName, value);
            configMap.put(sectionName, sectionMap);
          }
        }
      }
    }
    return configMap;
  }
}
