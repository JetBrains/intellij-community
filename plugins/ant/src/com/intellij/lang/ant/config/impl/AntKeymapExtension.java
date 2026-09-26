// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.config.impl;

import com.intellij.icons.AllIcons;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.AntConfiguration;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.keymap.KeymapExtension;
import com.intellij.openapi.keymap.KeymapGroup;
import com.intellij.openapi.keymap.KeymapGroupFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.util.containers.ContainerUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class AntKeymapExtension implements KeymapExtension {

  @Override
  public KeymapGroup createGroup(final Condition<? super AnAction> filtered, Project project) {
    final KeymapGroup result = KeymapGroupFactory.getInstance().createGroup(AntBundle.message("ant.targets.group.title"), AllIcons.Nodes.KeymapAnt);
    if (project != null) {
      final Map<String, KeymapGroup> buildFileNameToGroup = new HashMap<>();
      ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
      final String actionIdPrefix = AntConfiguration.getActionIdPrefix(project);
      List<String> ids = actionManager.getActionIdList(actionIdPrefix);
      for (final String id : ContainerUtil.sorted(ids)) {
        if (filtered != null && !filtered.value(actionManager.getActionOrStub(id))) {
          continue;
        }
        String buildFileName = AntBuildTargetImpl.parseBuildFileName(project, id);
        if (buildFileName != null) {
          KeymapGroup subGroup = buildFileNameToGroup.get(buildFileName);
          if (subGroup == null) {
            subGroup = KeymapGroupFactory.getInstance().createGroup(buildFileName);
            buildFileNameToGroup.put(buildFileName, subGroup);
            result.addGroup(subGroup);
          }
          subGroup.addActionId(id);
        }
      }
    }
    return result;
  }
}
