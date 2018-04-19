// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class TouchBarEditorCmdAlt extends TouchBarActionBase {
  TouchBarEditorCmdAlt(@NotNull Project project) {
    super("editor_cmd_alt", project);

    addFlexibleSpacing();
    addAnActionButton(ActionManager.getInstance().getAction("SwitchCoverage"), false, TBItemAnActionButton.SHOWMODE_TEXT_ONLY);
    addSpacing(true);
    addAnActionButton(ActionManager.getInstance().getAction("FindUsages"), false, TBItemAnActionButton.SHOWMODE_TEXT_ONLY);
  }
}
