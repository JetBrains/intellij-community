// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project;

import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import org.jetbrains.annotations.ApiStatus;

// the annotation (with a storage spec but without an actual component) is needed solely for the "export settings" action
@ApiStatus.Internal
@State(name = "ProjectManager", storages = @Storage(StoragePathMacros.PROJECT_DEFAULT_FILE))
public final class DefaultProjectFactoryImpl extends DefaultProjectFactory {
  @Override
  public Project getDefaultProject() {
    return ProjectManager.getInstance().getDefaultProject();
  }
}
