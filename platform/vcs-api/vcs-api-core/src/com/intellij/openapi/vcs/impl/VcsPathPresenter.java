// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public abstract class VcsPathPresenter {
  public static VcsPathPresenter getInstance(Project project) {
    return ServiceManager.getService(project, VcsPathPresenter.class);
  }

  /**
   * Returns the user-visible relative path from the content root under which the
   * specified file is located to the file itself, prefixed by the module name in
   * angle brackets.
   *
   * @param file the file for which the path is requested.
   * @return the relative path.
   */
  public abstract @NlsContexts.Label @NotNull String getPresentableRelativePathFor(VirtualFile file);

  public abstract @NlsContexts.Label @NotNull String getPresentableRelativePath(ContentRevision fromRevision, ContentRevision toRevision);
}
