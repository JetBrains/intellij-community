// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.readOnlyHandler;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class VcsHandleTypeFactory implements HandleTypeFactory {
  private final Project myProject;

  public VcsHandleTypeFactory(final Project project) {
    myProject = project;
  }

  @Override
  @Nullable
  public HandleType createHandleType(final VirtualFile file) {
    if (! myProject.isInitialized()) return null;
    AbstractVcs vcs = ProjectLevelVcsManager.getInstance(myProject).getVcsFor(file);
    if (vcs != null) {
      boolean fileExistsInVcs = vcs.fileExistsInVcs(VcsUtil.getFilePath(file));
      if (fileExistsInVcs && vcs.getEditFileProvider() != null) {
        return new VcsHandleType(vcs);
      }
    }
    return null;
  }
}
