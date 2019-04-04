/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.util.descriptors.ConfigFileVersion;
import com.intellij.util.descriptors.ConfigFileMetaData;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.devkit.DevKitBundle;

public interface PluginDescriptorConstants {
  String VERSION = "1.0";
  ConfigFileVersion[] VERSIONS = {
    new ConfigFileVersion(VERSION, "plugin.xml")
  };

  ConfigFileMetaData META_DATA =
    new ConfigFileMetaData(DevKitBundle.message("plugin.descriptor"), "plugin.xml", "META-INF", VERSIONS, null, false, true, true);

  @NonNls
  String PLUGIN_XML_PATH = META_DATA.getDirectoryPath() + "/" + META_DATA.getFileName();
}