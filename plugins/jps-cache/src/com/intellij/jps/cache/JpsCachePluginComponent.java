package com.intellij.jps.cache;

import com.intellij.jps.cache.hashing.JpsCacheUtils;
import com.intellij.jps.cache.hashing.PersistentCachingModuleHashingService;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

import java.io.File;

public class JpsCachePluginComponent implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance("com.intellij.jps.cache.JpsCachePluginComponent");
  public static final String PLUGIN_NAME = "jpsCachePlugin";
  private final Project project;
  private PersistentCachingModuleHashingService moduleHashingService;

  public JpsCachePluginComponent(Project project) {
    this.project = project;
  }

  @Override
  public void projectOpened() {
    try {
      this.moduleHashingService = new PersistentCachingModuleHashingService(new File(JpsCacheUtils.getPluginStorageDir(project)), project);
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  public PersistentCachingModuleHashingService getModuleHashingService() {
    return moduleHashingService;
  }
}
