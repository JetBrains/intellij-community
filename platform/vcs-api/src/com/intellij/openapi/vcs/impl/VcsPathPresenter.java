package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author yole
 */
public abstract class VcsPathPresenter {
  public static VcsPathPresenter getInstance(Project project) {
    return ServiceManager.getService(project, VcsPathPresenter.class);
  }

  public static VcsPathPresenter getInstanceChecked(final Project project) {
    return ApplicationManager.getApplication().runReadAction(new Computable<VcsPathPresenter>() {
      public VcsPathPresenter compute() {
        return ServiceManager.getService(project, VcsPathPresenter.class);
      }
    });
  }

  /**
   * Returns the user-visible relative path from the content root under which the
   * specified file is located to the file itself, prefixed by the module name in
   * angle brackets.
   *
   * @param file the file for which the path is requested.
   * @return the relative path.
   */
  public abstract String getPresentableRelativePathFor(VirtualFile file);

  public abstract String getPresentableRelativePath(ContentRevision fromRevision, ContentRevision toRevision);
}