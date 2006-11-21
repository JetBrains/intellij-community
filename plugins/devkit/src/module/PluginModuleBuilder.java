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

import com.intellij.ide.util.projectWizard.JavaModuleBuilder;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.devkit.build.PluginModuleBuildProperties;
import org.jetbrains.idea.devkit.build.JavaeePluginModuleBuildProperties;

public class PluginModuleBuilder extends JavaModuleBuilder{
  @NonNls private static final String META_INF = "META-INF";
  @NonNls private static final String PLUGIN_XML = "plugin.xml";


  public ModuleType getModuleType() {
    return PluginModuleType.getInstance();
  }

  public void setupRootModel(final ModifiableRootModel rootModel) throws ConfigurationException {
    super.setupRootModel(rootModel);
    final String defaultPluginXMLLocation = getModuleFileDirectory() + '/' + META_INF + '/' + PLUGIN_XML;
    VirtualFile file = LocalFileSystem.getInstance().findFileByPath(defaultPluginXMLLocation);
    if (file == null) {
      final JavaeePluginModuleBuildProperties moduleProperties = (JavaeePluginModuleBuildProperties)JavaeePluginModuleBuildProperties.getInstance(rootModel.getModule());
      moduleProperties.getPluginXML(); //call to create if not exist
    }
  }
}
