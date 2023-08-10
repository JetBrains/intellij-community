// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * This interface is a way to suppress creating of editors by certain valid FileEditorProvider-s in a very specific case.
 * <p>
 * PLEASE, DO NOT TRY TO USE THIS INTERFACE!
 * <p>
 * If you think you need to use it, then there is a high probability you are doing something wrong.
 */
@ApiStatus.Internal
public interface FileEditorProviderSuppressor {
  ExtensionPointName<FileEditorProviderSuppressor> EP_NAME = new ExtensionPointName<>("com.intellij.fileEditorProviderSuppressor");

  boolean isSuppressed(@NotNull Project project, @NotNull VirtualFile file, @NotNull FileEditorProvider provider);
}
