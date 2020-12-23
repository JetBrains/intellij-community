// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.ex;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public abstract class FileEditorProviderManager {
  public static FileEditorProviderManager getInstance() {
    return ApplicationManager.getApplication().getService(FileEditorProviderManager.class);
  }

  /**
   * @return All providers that can create editor for the specified {@code file} or empty array if there are none.
   * Please note that returned array is constructed with respect to editor policies.
   */
  public abstract FileEditorProvider @NotNull [] getProviders(@NotNull Project project, @NotNull VirtualFile file);

  /**
   * @return {@code null} if no provider with specified {@code editorTypeId} exists.
   */
  @Nullable
  public abstract FileEditorProvider getProvider(@NotNull String editorTypeId);
}
