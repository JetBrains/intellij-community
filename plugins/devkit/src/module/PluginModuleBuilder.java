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
import com.intellij.j2ee.j2eeDom.DeploymentDescriptorFactory;
import com.intellij.j2ee.j2eeDom.J2EEDeploymentItem;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;

import org.jetbrains.idea.devkit.DevKitBundle;

public class PluginModuleBuilder extends JavaModuleBuilder{


  public ModuleType getModuleType() {
    return PluginModuleType.getInstance();
  }

  public void setupRootModel(final ModifiableRootModel rootModel) throws ConfigurationException {
    super.setupRootModel(rootModel);
    //noinspection HardCodedStringLiteral
    final String defaultPluginXMLLocation = getModuleFileDirectory() + File.separator + "META-INF" + File.separator + "plugin.xml";
    VirtualFile file = LocalFileSystem.getInstance().findFileByPath(defaultPluginXMLLocation.replace(File.separatorChar, '/'));
    if (file == null) {
      //noinspection HardCodedStringLiteral
      CommandProcessor.getInstance().executeCommand(rootModel.getModule().getProject(), new Runnable() {
           public void run() {
             J2EEDeploymentItem pluginXML = DeploymentDescriptorFactory.getInstance().createDeploymentItem(rootModel.getModule(),
                                                                                                           new PluginDescriptorMetaData());
             pluginXML.setUrl(defaultPluginXMLLocation);
             pluginXML.createIfNotExists();
           }
         }, DevKitBundle.message("create.smth", "META-INF"), null);
    }
  }
}
