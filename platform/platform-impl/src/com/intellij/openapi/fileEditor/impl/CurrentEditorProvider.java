// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
   * This method is obsolete. Use the overload with a project
   *
   * @return
   */
  @ApiStatus.Obsolete
  @Nullable
  default FileEditor getCurrentEditor() {
    throw new UnsupportedOperationException("This method is obsolete. Use the overload with a project");
  }

  @Nullable
  default FileEditor getCurrentEditor(@Nullable Project project) {
    return getCurrentEditor();
  }
}
