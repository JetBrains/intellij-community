// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.http;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Url;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class LocalFileFinder {
  public static final ExtensionPointName<LocalFileFinder> EP_NAME = ExtensionPointName.create("com.intellij.http.localFileFinder");

  public abstract @Nullable VirtualFile findLocalFile(@NotNull Url url, @NotNull Project project);
}
