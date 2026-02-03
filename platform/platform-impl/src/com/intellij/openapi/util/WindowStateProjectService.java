// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

@State(name = "WindowStateProjectService", storages = {
  @Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE),
  @Storage(deprecated = true, value = StoragePathMacros.WORKSPACE_FILE),
})
final class WindowStateProjectService extends WindowStateServiceImpl {
  WindowStateProjectService(@NotNull Project project) {
    super(project);
  }
}
