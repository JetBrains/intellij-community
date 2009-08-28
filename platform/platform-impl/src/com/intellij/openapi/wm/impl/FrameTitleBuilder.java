package com.intellij.openapi.wm.impl;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.project.Project;

/**
 * @author yole
 */
public abstract class FrameTitleBuilder {
  public static FrameTitleBuilder getInstance() {
    return ServiceManager.getService(FrameTitleBuilder.class);
  }

  public abstract String getFileTitle(final Project project, final VirtualFile file);

  public abstract String getProjectTitle(final Project project);
}
