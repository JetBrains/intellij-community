// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.project.Project;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class TouchBarDebugger extends TouchBarActionBase {
  private static final Map<Project, TouchBarDebugger> ourInstances = new HashMap<>();

  private final Project myProject;

  private TBItemAnActionButton    myButtonPause;
  private TBItemAnActionButton    myButtonResume;

  private TBItemAnActionButton    myButtonStepOver;
  private TBItemAnActionButton    myButtonStepInto;
  private TBItemAnActionButton    myButtonStepOut;
  private TBItemAnActionButton    myButtonEvaluateExpression;

  private TouchBarDebugger(Project project) {
    super("debugger");
    myProject = project;

    addButton(AllIcons.Actions.Restart, null, new PlatformAction(IdeActions.ACTION_RERUN));
    myButtonPause = addAnActionButton("Pause");
    myButtonResume = addAnActionButton("Resume");
    addAnActionButton("Stop", false);

    addSpacing(false);
    addAnActionButton("XDebugger.MuteBreakpoints", false);

    addSpacing(true);
    myButtonStepOver = addAnActionButton("StepOver");
    myButtonStepInto = addAnActionButton("StepInto");
    myButtonStepOut = addAnActionButton("StepOut");

    addSpacing(true);
    myButtonEvaluateExpression = addAnActionButton("EvaluateExpression");
  }

  static @Nullable TouchBarDebugger findInstance(@NotNull Project project) {
    return ourInstances.get(project);
  }

  public static @NotNull TouchBarDebugger instance(@NotNull Project project) {
    // NOTE: called from EDT only
    TouchBarDebugger result = ourInstances.get(project);
    if (result == null) {
      final ID pool = Foundation.invoke("NSAutoreleasePool", "new");
      try {
        result = new TouchBarDebugger(project);
        ourInstances.put(project, result);
      } finally {
        Foundation.invoke(pool, "release");
      }
    }
    return result;
  }
}
