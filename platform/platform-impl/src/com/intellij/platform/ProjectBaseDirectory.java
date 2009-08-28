package com.intellij.platform;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author yole
 */
public class ProjectBaseDirectory {
  public static ProjectBaseDirectory getInstance(Project project) {
    return ServiceManager.getService(project, ProjectBaseDirectory.class);
  }

  private VirtualFile BASE_DIR;

  public VirtualFile getBaseDir(final VirtualFile baseDir) {
    if (getBaseDir() != null) {
      return getBaseDir();
    }
    return baseDir;
  }

  public VirtualFile getBaseDir() {
    return BASE_DIR;
  }

  public void setBaseDir(final VirtualFile baseDir) {
    BASE_DIR = baseDir;
  }
}
