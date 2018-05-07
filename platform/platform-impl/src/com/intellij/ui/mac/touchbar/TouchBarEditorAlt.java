// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class TouchBarEditorAlt extends TouchBarActionBase {
  TouchBarEditorAlt(@NotNull Project project, Component component) {
    super("editor_alt", project, component);

    addFlexibleSpacing();
    addAnActionButton("Terminal.OpenInTerminal", false, TBItemAnActionButton.SHOWMODE_IMAGE_TEXT);
  }
}
