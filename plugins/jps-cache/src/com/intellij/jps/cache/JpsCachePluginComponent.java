package com.intellij.jps.cache;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

import java.io.File;
import java.io.IOException;

public class JpsCachePluginComponent implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance("com.intellij.jps.cache.JpsCachePluginComponent");
  static final String PLUGIN_NAME = "jpsCachePlugin";
  private final Project project;
  private PersistentCachingModuleHashingService moduleHashingService;

  public JpsCachePluginComponent(Project project) {
    this.project = project;
    try {
      this.moduleHashingService = new PersistentCachingModuleHashingService(new File(JpsCacheUtils.getPluginStorageDir(project)), project);
    }
    catch (Exception e) {
      LOG.warn(e);
    }
  }

  @Override
  public void projectOpened() {
    try {
      moduleHashingService.getAffectedModulesWithHashes();
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }
}
