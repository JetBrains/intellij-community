// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.EventObject;

public abstract class ModuleRootEvent extends EventObject {

  protected ModuleRootEvent(@NotNull Project project) {
    super(project);
  }

  public abstract boolean isCausedByFileTypesChange();

  /**
   * For more fine-grained events one may add {@link com.intellij.workspaceModel.ide.WorkspaceModelChangeListener}
   * to get incremental workspace model changes and then ignore any {@link ModuleRootEvent}
   * with {@code isCausedByWorkspaceModelChangesOnly == true}.
   */
  public abstract boolean isCausedByWorkspaceModelChangesOnly();

  public @NotNull Project getProject() {
    return (Project)getSource();
  }
}
