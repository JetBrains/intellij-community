// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.impl.ad.AdTheManagerImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
@ApiStatus.Internal
public interface AdTheManager {

  static @NotNull AdTheManager getInstance() {
    return ApplicationManager.getApplication().getService(AdTheManagerImpl.class);
  }

  @Nullable EditorModel createEditorModel(@NotNull EditorEx editor);
}
