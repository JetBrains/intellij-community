// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface AdditionalLibraryRootsListenerHelper {

  static AdditionalLibraryRootsListenerHelper getInstance() {
    return ApplicationManager.getApplication().getService(AdditionalLibraryRootsListenerHelper.class);
  }

  void handleAdditionalLibraryRootsChanged(@NotNull Project project,
                                           @Nullable @Nls String presentableLibraryName,
                                           @NotNull Collection<? extends VirtualFile> oldRoots,
                                           @NotNull Collection<? extends VirtualFile> newRoots,
                                           @NotNull String libraryNameForDebug);
}
