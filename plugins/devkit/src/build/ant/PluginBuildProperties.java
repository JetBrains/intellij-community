/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package org.jetbrains.idea.devkit.build.ant;

import org.jetbrains.annotations.NonNls;
import com.intellij.compiler.ant.BuildProperties;
import com.intellij.openapi.module.Module;

/**
 * @author nik
 */
public class PluginBuildProperties {
  @NonNls public static final String PLUGIN_DIR_EXPLODED = "plugin.dir.exploded";
  @NonNls public static final String PLUGIN_PATH_JAR = "plugin.path.jar";

  @NonNls
  public static String getBuildExplodedTargetName(final String configurationName) {
    return "plugin.build.exploded." + BuildProperties.convertName(configurationName);
  }

  @NonNls
  public static String getBuildJarTargetName(final String configurationName) {
    return "plugin.build.jar." + BuildProperties.convertName(configurationName);
  }

  @NonNls
  public static String getExplodedPathProperty(final String configurationName) {
    return BuildProperties.convertName(configurationName) + ".plugin.exploded.dir";
  }

  @NonNls
  public static String getJarPathProperty(final String configurationName) {
    return BuildProperties.convertName(configurationName) + ".path.jar";
  }

  @NonNls
  public static String getBuildPluginTarget(final Module module) {
    return "plugin.build." + BuildProperties.convertName(module.getName());
  }
}
