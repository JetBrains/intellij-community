/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.startup.StartupManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.devkit.build.PluginBuildConfiguration;
import org.jetbrains.idea.devkit.projectRoots.IdeaJdk;

public class PluginModuleBuilder extends JavaModuleBuilder{
  @NonNls private static final String META_INF = "META-INF";
  @NonNls private static final String PLUGIN_XML = "plugin.xml";


  public ModuleType getModuleType() {
    return PluginModuleType.getInstance();
  }

  public void setupRootModel(final ModifiableRootModel rootModel) throws ConfigurationException {
    super.setupRootModel(rootModel);
    final String defaultPluginXMLLocation = getModuleFileDirectory() + '/' + META_INF + '/' + PLUGIN_XML;
    final Module module = rootModel.getModule();
    StartupManager.getInstance(module.getProject()).runWhenProjectIsInitialized(new Runnable() {
      public void run() {
        final PluginBuildConfiguration buildConfiguration = PluginBuildConfiguration.getInstance(module);
        if (buildConfiguration != null) {
          buildConfiguration.setPluginXmlPathAndCreateDescriptorIfDoesntExist(defaultPluginXMLLocation);
        }
      }
    });
  }

  @Override
  public boolean isSuitableSdkType(SdkTypeId sdk) {
    return sdk == IdeaJdk.getInstance();
  }

  @Override
  public String getGroupName() {
    return JavaModuleType.JAVA_GROUP;
  }
}
