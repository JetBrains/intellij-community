// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant.config.impl;

import com.intellij.icons.AllIcons;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.lang.ant.config.AntConfiguration;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.KeymapExtension;
import com.intellij.openapi.keymap.KeymapGroup;
import com.intellij.openapi.keymap.KeymapGroupFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Vladislav.Kaznacheev
*/
final class AntKeymapExtension implements KeymapExtension {
  private static final Logger LOG = Logger.getInstance(AntKeymapExtension.class);

  @Override
  public KeymapGroup createGroup(final Condition<AnAction> filtered, Project project) {
    final Map<AntBuildFile, KeymapGroup> buildFileToGroup = new HashMap<>();
    final KeymapGroup result = KeymapGroupFactory.getInstance().createGroup(AntBundle.message("ant.targets.group.title"),
                                                                            AllIcons.Nodes.KeymapAnt);

    ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
    List<String> ids = actionManager.getActionIdList(project != null ? AntConfiguration.getActionIdPrefix(project) : AntConfiguration.ACTION_ID_PREFIX);
    ids.sort(null);

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
