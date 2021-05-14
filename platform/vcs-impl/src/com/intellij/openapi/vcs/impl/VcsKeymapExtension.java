// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.keymap.KeymapExtension;
import com.intellij.openapi.keymap.KeymapGroup;
import com.intellij.openapi.keymap.KeymapGroupFactory;
import com.intellij.openapi.keymap.impl.ui.ActionsTreeUtil;
import com.intellij.openapi.keymap.impl.ui.Group;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.util.containers.ContainerUtil;

/**
 * @author yole
 */
public class VcsKeymapExtension implements KeymapExtension {
  @Override
  public KeymapGroup createGroup(final Condition<? super AnAction> filtered, final Project project) {
    KeymapGroup result = KeymapGroupFactory.getInstance().createGroup(KeyMapBundle.message("version.control.group.title"));

    AnAction[] versionControlsGroups = ActionsTreeUtil.getActions("VcsGroup");
    AnAction[] keymapGroups = ActionsTreeUtil.getActions("Vcs.KeymapGroup");

    for (AnAction action : ContainerUtil.concat(versionControlsGroups, keymapGroups)) {
      ActionsTreeUtil.addAction(result, action, filtered, false);
    }

    AnAction[] generalActions = ActionsTreeUtil.getActions("VcsGeneral.KeymapGroup");
    for (AnAction action : generalActions) {
      ActionsTreeUtil.addAction(result, action, filtered, true);
    }

    if (result instanceof Group) {
      ((Group)result).normalizeSeparators();
    }

    return result;
  }
}