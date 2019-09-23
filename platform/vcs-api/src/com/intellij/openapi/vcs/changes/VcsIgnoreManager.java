// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public interface VcsIgnoreManager {

  boolean isRunConfigurationVcsIgnored(@NotNull String configurationName);

  void removeRunConfigurationFromVcsIgnore(@NotNull String configurationName);

  /**
   * Check if the file could be potentially ignored. However this doesn't mean that the file is ignored in VCS.
   * To check if the file ignored use {@link ChangeListManager#isIgnoredFile(VirtualFile)}
   * @param file to check
   * @return true if the file is potentially ignored
   */
  boolean isPotentiallyIgnoredFile(@NotNull VirtualFile file);

  boolean isPotentiallyIgnoredFile(@NotNull FilePath filePath);

  static VcsIgnoreManager getInstance(@NotNull Project project){
    return ServiceManager.getService(project, VcsIgnoreManager.class);
  }
}
