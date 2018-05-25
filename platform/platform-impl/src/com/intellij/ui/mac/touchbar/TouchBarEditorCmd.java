// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class TouchBarEditorCmd extends TouchBarActionBase {
  TouchBarEditorCmd(@NotNull Project project, Component component) {
    super("editor_cmd", project, component);

    // Use Cmd modifier key to show Navigation buttons, Bookmark and Breakpoint toggles and to Show Usages
    // Cmd key is used, because it’s used in most of the action shortcuts: Back/Forward, Breakpoint and Find Usages.
    addAnActionButton("Back", false);
    addAnActionButton("Forward", false);
    addSpacing(true);

    addAnActionButton("ToggleBookmark", false, TBItemAnActionButton.SHOWMODE_TEXT_ONLY);       // TODO: make with custom icon (doesn't defined in template presentation)
    addAnActionButton("ToggleLineBreakpoint", false, TBItemAnActionButton.SHOWMODE_TEXT_ONLY); // TODO: make with custom icon (doesn't defined in template presentation)
    addFlexibleSpacing();

    addAnActionButton("FindUsages", false, TBItemAnActionButton.SHOWMODE_TEXT_ONLY);
  }
}
