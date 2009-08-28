/*
 * @author max
 */
package com.intellij.openapi.project.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

public class DefaultProjectLocator extends ProjectLocator {
  @Nullable
  public Project guessProjectForFile(final VirtualFile file) {
    ProjectManager projectManager = ProjectManager.getInstance();
    if (projectManager == null) return null;

    final Project[] projects = projectManager.getOpenProjects();
    if (projects.length == 1) {
      return !projects[0].isDisposed() ? projects[0] : null;
    }
    else {
      return null;
    }
  }
}