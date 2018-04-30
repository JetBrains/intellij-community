// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.InputEvent;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ProjectBarsStorage {
  public static final String GENERAL = "general";
  public static final String DEBUGGER = "debugger";
  public static final String EDITOR = "editor";

  private static final Map<Project, ProjectBarsStorage> ourInstances = new HashMap<>();

  private final @NotNull Project myProject;
  private final List<BarContainer> myBars = new ArrayList<>();

  ProjectBarsStorage(@NotNull Project project) { myProject = project; }

  BarContainer createBarContainer(@NotNull String type, Component component) {
    ApplicationManager.getApplication().assertIsDispatchThread  ();

    BarContainer result;

    if (type.equals(GENERAL)) {
      result = new SingleBarContainer(new TouchBarGeneral(myProject, component, "GLOBAL"));
    } else if (type.equals(DEBUGGER)) {
      MultiBarContainer container = new MultiBarContainer(new TouchBarDebugger(myProject, component));
      final long mask = InputEvent.ALT_DOWN_MASK;
      container.registerAltByKeyMask(mask, new TouchBarDebuggerAlt(myProject, component));
      result = container;
    } else if (type.equals(EDITOR)) {
      MultiBarContainer container = new MultiBarContainer(new TouchBarGeneral(myProject, component, "EDITOR"));
      container.registerAltByKeyMask(InputEvent.ALT_DOWN_MASK, new TouchBarEditorAlt(myProject, component));
      container.registerAltByKeyMask(InputEvent.META_DOWN_MASK, new TouchBarEditorCmd(myProject, component));
      container.registerAltByKeyMask(InputEvent.ALT_DOWN_MASK | InputEvent.META_DOWN_MASK, new TouchBarEditorCmdAlt(myProject, component));
      container.registerAltByKeyMask(InputEvent.SHIFT_DOWN_MASK, new TouchBarEditorShift(myProject, component));
      result = container;
    } else
      throw new RuntimeException("unknown context of project-touchbar: " + type);

    myBars.add(result);

    return result;
  }

  void releaseAll() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myBars.forEach((bc)->bc.release());
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
