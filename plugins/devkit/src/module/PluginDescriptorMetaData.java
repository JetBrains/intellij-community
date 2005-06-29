/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package org.jetbrains.idea.devkit.module;

import com.intellij.j2ee.j2eeDom.DeploymentDescriptorMetaData;
import com.intellij.openapi.module.ModuleType;

public class PluginDescriptorMetaData implements DeploymentDescriptorMetaData {
  private final static String VERSION = "1.0";

  public String[] getAvailableVersions() {
    return new String[] {VERSION};
  }

  public String getTemplateNameAccordingToVersion(String version) {
    return "plugin.xml";
  }

  public String getDefaultVersion() {
    return VERSION;
  }

  public String getDefaultFileName() {
    return "plugin.xml";
  }

  public String getDefaultDirectoryName() {
    return "META-INF";
  }

  public String getTitle() {
    return "IntelliJ IDEA Plugin Descriptor";
  }

  public ModuleType[] getSuitableTypes() {
    return new ModuleType[] {PluginModuleType.getInstance()};
  }

  public boolean isDescriptorOptional() {
    return false;
  }
}