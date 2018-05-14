// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.InputEvent;
import java.util.*;
import java.util.List;

public class ProjectBarsStorage {
  private static final Logger LOG = Logger.getInstance(ProjectBarsStorage.class);

  public static final String GENERAL = "general";
  public static final String DEBUGGER = "debugger";
  public static final String EDITOR = "editor";

  private static final Map<Project, ProjectBarsStorage> ourInstances = new HashMap<>();

  private final @NotNull Project myProject;
  private final List<BarContainer> myBars = new ArrayList<>();

  ProjectBarsStorage(@NotNull Project project) { myProject = project; }

  @Nullable BarContainer createBarContainer(@NotNull String type, Component component) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    final String barId;
    final String touchBarName;
    final boolean replaceEsc;
    if (type.equals(GENERAL)) {
      barId = "Default";
      touchBarName = "default_global";
      replaceEsc = false;
    } else if (type.equals(DEBUGGER)) {
      barId = "Debugger";
      touchBarName = "debugger";
      replaceEsc = true;
    } else if (type.equals(EDITOR)) {
      barId = "Default";
      touchBarName = "default_editor";
      replaceEsc = false;
    } else {
      LOG.error("can't create touchbar, unknown context: " + type);
      return null;
    }

    final ActionGroup mainLayout = TouchBarActionBase.getCustomizedGroup(barId);
    if (mainLayout == null) {
      LOG.error("can't create touchbar because corresponding ActionGroup isn't defined, context: " + barId);
      return null;
    }

    final MultiBarContainer container = new MultiBarContainer(new TouchBarActionBase(touchBarName, myProject, mainLayout, component, replaceEsc));
    final Map<String, ActionGroup> alts = type.equals(GENERAL) ? null : TouchBarActionBase.getAltLayouts(mainLayout);
    if (alts != null && !alts.isEmpty()) {
      for (String modId: alts.keySet()) {
        final long mask = _str2mask(modId);
        if (mask == 0) {
          // System.out.println("ERROR: zero mask for modId="+modId);
          continue;
        }
        container.registerAltByKeyMask(mask, new TouchBarActionBase(touchBarName + "_" + modId, myProject, alts.get(modId), component, replaceEsc));
      }
    }

    myBars.add(container);
    return container;
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

  private static long _str2mask(@NotNull String modifierId) {
    if (!modifierId.contains(".")) {
      if (modifierId.equalsIgnoreCase("alt"))
        return InputEvent.ALT_DOWN_MASK;
      if (modifierId.equalsIgnoreCase("cmd"))
        return InputEvent.META_DOWN_MASK;
      if (modifierId.equalsIgnoreCase("shift"))
        return InputEvent.SHIFT_DOWN_MASK;
      return 0;
    }

    final String[] spl = modifierId.split("\\.");
    if (spl == null)
      return 0;

    long mask = 0;
    for (String sub: spl)
      mask |= _str2mask(sub);
    return mask;
  }
}
