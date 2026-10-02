// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

public interface CurrentEditorProvider {
  static CurrentEditorProvider getInstance() {
    return ApplicationManager.getApplication().getService(CurrentEditorProvider.class);
  }

  /**
   * This method is obsolete. Use the overload with a project. Default implementation throws an exception.  
   */
  @ApiStatus.Obsolete
  default @Nullable FileEditor getCurrentEditor() {
    throw new UnsupportedOperationException("This method is obsolete. Use the overload with a project");
  }

  default @Nullable FileEditor getCurrentEditor(@Nullable Project project) {
    return getCurrentEditor();
  }
}
