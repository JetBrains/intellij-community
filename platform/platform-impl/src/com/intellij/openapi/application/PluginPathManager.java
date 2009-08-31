package com.intellij.openapi.application;

import java.io.File;

/**
 * @author yole
 */
public class PluginPathManager {
  private PluginPathManager() {
  }

  public static String getPluginHomePath(String pluginName) {
    String homePath = PathManager.getHomePath();
    File candidate = new File(homePath, "community/plugins/" + pluginName);
    if (candidate.isDirectory()) {
      return candidate.getPath();
    }
    return new File(homePath, "plugins/" + pluginName).getPath();
  }
}
