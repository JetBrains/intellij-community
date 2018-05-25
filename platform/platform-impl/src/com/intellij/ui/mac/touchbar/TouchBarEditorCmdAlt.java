// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class TouchBarEditorCmdAlt extends TouchBarActionBase {
  TouchBarEditorCmdAlt(@NotNull Project project, Component component) {
    super("editor_cmd_alt", project, component);

    addFlexibleSpacing();
    addAnActionButton("SwitchCoverage", false, TBItemAnActionButton.SHOWMODE_TEXT_ONLY);
    addSpacing(true);
    addAnActionButton("FindUsages", false, TBItemAnActionButton.SHOWMODE_TEXT_ONLY);
  }
}
