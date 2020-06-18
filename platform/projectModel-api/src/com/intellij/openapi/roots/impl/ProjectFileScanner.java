// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implement to get notified about files/directories traversed during "Scanning files to index" sessions, which happen
 * on project startup and on new files/directories added to project.<p/>
 * For example, during the project startup, all content files/directories are scanned.
 * Scanned files/directories are under content roots and not excluded or ignored.
 */
@ApiStatus.Experimental
public interface ProjectFileScanner {
  ExtensionPointName<ProjectFileScanner> EP_NAME = ExtensionPointName.create("com.intellij.projectFileScanner");

  /**
   * Called on a background thread when a scan session is started in the given project.
   * @param project Project the scan session is started for
   * @param singleRoot not-null value means the scan session is started for the single root only; null value - for the whole project
   */
  @NotNull ScanSession startSession(@NotNull Project project, @Nullable VirtualFile singleRoot);

  interface ScanSession {
    /**
     * Called on a background thread (possibly different from {@link ProjectFileScanner#startSession(Project, VirtualFile)}) when
     * visiting the given {@code fileOrDir}. Different files can be visited on different threads during the same scan session.
     * Please note it can be called not under read action, thus {@code fileOrDir} might be invalid or the project might be already disposed.
     * @param fileOrDir the file or directory being scanned
     */
    void visitFile(@NotNull VirtualFile fileOrDir);
  }
}
