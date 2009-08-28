package com.intellij.openapi.wm.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.ProjectBaseDirectory;

/**
 * @author yole
 */
public class PlatformFrameTitleBuilder extends FrameTitleBuilder {
  public String getProjectTitle(final Project project) {
    VirtualFile baseDir = ProjectBaseDirectory.getInstance(project).getBaseDir();
    if (baseDir != null) {
      return FileUtil.toSystemDependentName(baseDir.getPath());
    }
    return project.getName();
  }

  public String getFileTitle(final Project project, final VirtualFile file) {
    String url = file.getPresentableUrl();
    VirtualFile baseDir = ProjectBaseDirectory.getInstance(project).getBaseDir();
    if (baseDir == null) baseDir = project.getBaseDir();
    if (baseDir != null) {
      //noinspection ConstantConditions
      final String projectHomeUrl = baseDir.getPresentableUrl();
      if (url.startsWith(projectHomeUrl)) {
        url = "..." + url.substring(projectHomeUrl.length());
      }
    }
    return url;
  }
}
