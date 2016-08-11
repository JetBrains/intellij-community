/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.ant.config.impl;

import com.intellij.icons.AllIcons;
import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.lang.ant.config.AntConfiguration;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.keymap.KeymapExtension;
import com.intellij.openapi.keymap.KeymapGroup;
import com.intellij.openapi.keymap.KeymapGroupFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.util.containers.HashMap;

import java.util.Arrays;
import java.util.Map;

/**
 * @author Vladislav.Kaznacheev
*/
class AntKeymapExtension implements KeymapExtension {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.ant.config.impl.AntProjectKeymap");

  public KeymapGroup createGroup(final Condition<AnAction> filtered, Project project) {
    final Map<AntBuildFile, KeymapGroup> buildFileToGroup = new HashMap<>();
    final KeymapGroup result = KeymapGroupFactory.getInstance().createGroup(KeyMapBundle.message("ant.targets.group.title"),
                                                                            AllIcons.Nodes.KeymapAnt);

    final ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
    final String[] ids = actionManager.getActionIds(project != null? AntConfiguration.getActionIdPrefix(project) : AntConfiguration.ACTION_ID_PREFIX);
    Arrays.sort(ids);

    if (project != null) {
      final AntConfiguration antConfiguration = AntConfiguration.getInstance(project);
      ApplicationManager.getApplication().runReadAction(() -> {
        for (final String id : ids) {
          if (filtered != null && !filtered.value(actionManager.getActionOrStub(id))) {
            continue;
          }
          final AntBuildFile buildFile = antConfiguration.findBuildFileByActionId(id);
          if (buildFile != null) {
            KeymapGroup subGroup = buildFileToGroup.get(buildFile);
            if (subGroup == null) {
              subGroup = KeymapGroupFactory.getInstance().createGroup(buildFile.getPresentableName());
              buildFileToGroup.put(buildFile, subGroup);
              result.addGroup(subGroup);
            }
            subGroup.addActionId(id);
          }
          else {
            LOG.info("no buildfile found for actionId=" + id);
          }
        }
      });
    }

    return result;
  }
}
