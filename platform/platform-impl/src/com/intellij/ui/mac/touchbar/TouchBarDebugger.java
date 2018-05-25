// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class TouchBarDebugger extends TouchBarActionBase {
  TouchBarDebugger(@NotNull Project project, Component component) {
    super("debugger", project, component, true);

    addButton(AllIcons.Actions.Restart, null, new PlatformAction(IdeActions.ACTION_RERUN));
    addAnActionButton("Pause");
    addAnActionButton("Resume");
    addAnActionButton("Stop", false);

    addSpacing(false);
    addAnActionButton("XDebugger.MuteBreakpoints", false);

    addSpacing(true);
    addAnActionButton("StepOver");
    addAnActionButton("StepInto");
    addAnActionButton("StepOut");

    addSpacing(true);
    addAnActionButton("EvaluateExpression");
  }
}
