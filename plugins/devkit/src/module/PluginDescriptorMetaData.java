/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.devkit.module;

import com.intellij.javaee.DeploymentDescriptorMetaData;
import com.intellij.openapi.module.ModuleType;
import org.jetbrains.idea.devkit.DevKitBundle;

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
    return DevKitBundle.message("plugin.descriptor");
  }

  public ModuleType[] getSuitableTypes() {
    return new ModuleType[] {PluginModuleType.getInstance()};
  }

  public boolean isDescriptorOptional() {
    return false;
  }

}