/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
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

public class PluginModuleBuilder extends JavaModuleBuilder{


  public ModuleType getModuleType() {
    return PluginModuleType.getInstance();
  }

  public void setupRootModel(final ModifiableRootModel rootModel) throws ConfigurationException {
    super.setupRootModel(rootModel);
    final String defaultPluginXMLLocation = getModuleFileDirectory() + File.separator + "META-INF" + File.separator + "plugin.xml";
    VirtualFile file = LocalFileSystem.getInstance().findFileByPath(defaultPluginXMLLocation.replace(File.separatorChar, '/'));
    if (file == null) {
     CommandProcessor.getInstance().executeCommand(rootModel.getModule().getProject(), new Runnable() {
          public void run() {
            J2EEDeploymentItem pluginXML = DeploymentDescriptorFactory.getInstance().createDeploymentItem(rootModel.getModule(),
                                                                                                          new PluginDescriptorMetaData());
            pluginXML.setUrl(defaultPluginXMLLocation);
            pluginXML.createIfNotExists();
          }
        }, "Create META-INF", null);
    }
  }
}
