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
package org.jetbrains.idea.devkit;

import com.intellij.codeInspection.InspectionToolProvider;
import com.intellij.ide.fileTemplates.FileTemplateDescriptor;
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptor;
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptorFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileTypes.FileNameMatcher;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.WildcardFileNameMatcher;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.inspections.ComponentNotRegisteredInspection;
import org.jetbrains.idea.devkit.inspections.RegistrationProblemsInspection;
import org.jetbrains.idea.devkit.module.PluginModuleType;

import java.util.Arrays;

public class DevKitPlugin implements ApplicationComponent, InspectionToolProvider, FileTemplateGroupDescriptorFactory {

  public DevKitPlugin(ModuleTypeManager moduleTypeManager) {
    moduleTypeManager.registerModuleType(PluginModuleType.getInstance(), true);
  }

  @NotNull
  public String getComponentName() {
    return "DevKit.Plugin";
  }

  public Class[] getInspectionClasses() {
    return new Class[] {
            RegistrationProblemsInspection.class,
            ComponentNotRegisteredInspection.class,
    };
  }

  public void initComponent() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        final FileNameMatcher pluginMatcher = new WildcardFileNameMatcher("plugin.xml");
        FileTypeManager.getInstance().registerFileType(new PluginFileType(), Arrays.asList(pluginMatcher));
      }
    });
  }

  public void disposeComponent() {
  }

  public FileTemplateGroupDescriptor getFileTemplatesDescriptor() {
    FileTemplateGroupDescriptor descriptor = new FileTemplateGroupDescriptor(DevKitBundle.message("plugin.descriptor"), IconLoader.getIcon("/nodes/plugin.png"));
    descriptor.addTemplate(new FileTemplateDescriptor("plugin.xml", StdFileTypes.XML.getIcon()));
    descriptor.addTemplate(new FileTemplateDescriptor("ProjectComponent.java", StdFileTypes.JAVA.getIcon()));
    descriptor.addTemplate(new FileTemplateDescriptor("ApplicationComponent.java", StdFileTypes.JAVA.getIcon()));
    descriptor.addTemplate(new FileTemplateDescriptor("ModuleComponent.java", StdFileTypes.JAVA.getIcon()));
    descriptor.addTemplate(new FileTemplateDescriptor("Action.java", StdFileTypes.JAVA.getIcon()));
    return descriptor;
  }

}