// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class TouchBarProjectBase extends TouchBar {
  protected final @NotNull Project myProject;

  public TouchBarProjectBase(@NotNull String touchbarName, @NotNull Project project) { this(touchbarName, project, false); }

  public TouchBarProjectBase(@NotNull String touchbarName, @NotNull Project project, boolean replaceEsc) {
    super(touchbarName, replaceEsc);
    myProject = project;
  }
}
