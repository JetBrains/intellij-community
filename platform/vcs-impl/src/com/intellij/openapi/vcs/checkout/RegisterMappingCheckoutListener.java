// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.checkout;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.VcsKey;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Collections;

final class RegisterMappingCheckoutListener implements VcsAwareCheckoutListener {
  @Override
  public boolean processCheckedOutDirectory(Project currentProject, @NotNull Path directory, VcsKey vcsKey) {
    Project project = CompositeCheckoutListener.findProjectByBaseDirLocation(directory);
    if (project == null) {
      return false;
    }

    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    if (!vcsManager.hasAnyMappings()) {
      vcsManager.setDirectoryMappings(Collections.singletonList(VcsDirectoryMapping.createDefault(vcsKey.getName())));
    }
    return true;
  }
}
