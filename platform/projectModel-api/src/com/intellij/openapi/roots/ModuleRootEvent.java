// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.EventObject;

public abstract class ModuleRootEvent extends EventObject {

  protected ModuleRootEvent(@NotNull Project project) {
    super(project);
  }

  public abstract boolean isCausedByFileTypesChange();

  public @NotNull Project getProject() {
    return (Project)getSource();
  }
}
