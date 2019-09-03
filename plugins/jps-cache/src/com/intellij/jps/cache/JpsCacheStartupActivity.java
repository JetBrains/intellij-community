package com.intellij.jps.cache;

import com.intellij.jps.cache.hashing.JpsCacheUtils;
import com.intellij.jps.cache.hashing.PersistentCachingModuleHashingService;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class JpsCacheStartupActivity implements StartupActivity {
  private static final Logger LOG = Logger.getInstance("com.intellij.jps.cache.JpsCacheStartupActivity");
  private PersistentCachingModuleHashingService moduleHashingService;

  @Override
  public void runActivity(@NotNull Project project) {
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
