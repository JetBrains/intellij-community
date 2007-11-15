package com.intellij.lang.ant.config.impl;

import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.lang.ant.config.AntConfiguration;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.keymap.KeymapExtension;
import com.intellij.openapi.keymap.KeymapGroup;
import com.intellij.openapi.keymap.KeymapGroupFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.containers.HashMap;

import javax.swing.*;
import java.util.Arrays;
import java.util.Map;

/**
 * @author Vladislav.Kaznacheev
*/
class AntKeymapExtension implements KeymapExtension {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.ant.config.impl.AntProjectKeymap");

  private static final Icon ANT_ICON = IconLoader.getIcon("/nodes/keymapAnt.png");
  private static final Icon ANT_OPEN_ICON = IconLoader.getIcon("/nodes/keymapAntOpen.png");

  public KeymapGroup createGroup(Condition<AnAction> filtered, Project project) {
    final Map<AntBuildFile, KeymapGroup> buildFileToGroup = new HashMap<AntBuildFile, KeymapGroup>();
    final KeymapGroup result = KeymapGroupFactory.getInstance().createGroup(KeyMapBundle.message("ant.targets.group.title"),
                                                                            ANT_ICON, ANT_OPEN_ICON);

    final ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
    String[] ids = actionManager.getActionIds(project != null? AntConfiguration.getActionIdPrefix(project) : AntConfiguration.ACTION_ID_PREFIX);
    Arrays.sort(ids);

    if (project != null) {
      final AntConfiguration antConfiguration = AntConfiguration.getInstance(project);
      if (antConfiguration != null) {
        for (final String id : ids) {
          if (filtered != null && !filtered.value(actionManager.getActionOrStub(id))) continue;
          final AntBuildFile buildFile = antConfiguration.findBuildFileByActionId(id);
          if (buildFile == null) {
            LOG.info("no buildfile found for actionId=" + id);
            continue;
          }
          KeymapGroup subGroup = buildFileToGroup.get(buildFile);
          if (subGroup == null) {
            subGroup = KeymapGroupFactory.getInstance().createGroup(buildFile.getPresentableName());
            buildFileToGroup.put(buildFile, subGroup);
            result.addGroup(subGroup);
          }
          subGroup.addActionId(id);
        }
      }
    }

    return result;
  }
}
