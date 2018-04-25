// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class TouchBarEditorShift extends TouchBarActionBase {
  TouchBarEditorShift(@NotNull Project project, Component component) {
    super("editor_shift", project, component);

    // Use Shift to show Refactor actions
    addAnActionButton(ActionManager.getInstance().getAction("RenameElement"), false, TBItemAnActionButton.SHOWMODE_TEXT_ONLY);
    addAnActionButton(ActionManager.getInstance().getAction("ChangeSignature"), false, TBItemAnActionButton.SHOWMODE_TEXT_ONLY);
    addAnActionButton(ActionManager.getInstance().getAction("ChangeTypeSignature"), false, TBItemAnActionButton.SHOWMODE_TEXT_ONLY);

    addFlexibleSpacing();
    addAnActionButton(ActionManager.getInstance().getAction("CopyElement"), false, TBItemAnActionButton.SHOWMODE_TEXT_ONLY);
    addAnActionButton(ActionManager.getInstance().getAction("Move"), false, TBItemAnActionButton.SHOWMODE_TEXT_ONLY);
  }
}
