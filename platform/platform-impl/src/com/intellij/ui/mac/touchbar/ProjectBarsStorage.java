// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.awt.event.InputEvent;
import java.util.HashMap;
import java.util.Map;

public class ProjectBarsStorage {
  public static final String GENERAL = "general";
  public static final String DEBUGGER = "debugger";
  public static final String EDITOR = "editor";

  private static final Map<Project, ProjectBarsStorage> ourInstances = new HashMap<>();

  private final @NotNull Project myProject;
  private final Map<String, BarContainer> myBars = new HashMap<>();

  ProjectBarsStorage(@NotNull Project project) { myProject = project; }

  BarContainer getBarContainer(@NotNull String type) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    BarContainer result = myBars.get(type);
    if (result == null) {
      if (type.equals(GENERAL)) {
        result = new SingleBarContainer(() -> new TouchBarGeneral(myProject));
      } else if (type.equals(DEBUGGER)) {
        final BarContainer mainDebug = new SingleBarContainer(()->new TouchBarDebugger(myProject));
        final BarContainer altDebug = new SingleBarContainer(()->new TouchBarDebuggerAlt(myProject));
        MultiBarContainer container = new MultiBarContainer(mainDebug);
        final long mask = InputEvent.ALT_DOWN_MASK;
        container.registerAltByKeyMask(mask, altDebug);
        result = container;
      } else if (type.equals(EDITOR)) {
        result = new SingleBarContainer(()->new TouchBarGeneral(myProject));
      } else
        throw new RuntimeException("unknown context of project-touchbar: " + type);

      myBars.put(type, result);
    }
    return result;
  }

  void releaseAll() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myBars.forEach((str, bc)->bc.release());
    myBars.clear();
  }

  static @NotNull ProjectBarsStorage instance(@NotNull Project project) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    ProjectBarsStorage result = ourInstances.get(project);
    if (result == null) {
        result = new ProjectBarsStorage(project);
        ourInstances.put(project, result);
    }
    return result;
  }
}
