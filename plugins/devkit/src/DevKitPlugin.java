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
import com.intellij.ide.IconProvider;
import com.intellij.ide.fileTemplates.FileTemplateDescriptor;
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptor;
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptorFactory;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.devkit.inspections.ComponentNotRegisteredInspection;
import org.jetbrains.idea.devkit.inspections.RegistrationProblemsInspection;
import org.jetbrains.idea.devkit.module.PluginModuleType;

import javax.swing.*;

public class DevKitPlugin implements ApplicationComponent, InspectionToolProvider, FileTemplateGroupDescriptorFactory, IconProvider {
  private static final Icon ICON = IconLoader.getIcon("/plugin.png");

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

  public void initComponent() {}

  public void disposeComponent() {}

  public FileTemplateGroupDescriptor getFileTemplatesDescriptor() {
    FileTemplateGroupDescriptor descriptor = new FileTemplateGroupDescriptor(DevKitBundle.message("plugin.descriptor"), IconLoader.getIcon("/nodes/plugin.png"));
    descriptor.addTemplate(new FileTemplateDescriptor("plugin.xml", StdFileTypes.XML.getIcon()));
    descriptor.addTemplate(new FileTemplateDescriptor("ProjectComponent.java", StdFileTypes.JAVA.getIcon()));
    descriptor.addTemplate(new FileTemplateDescriptor("ApplicationComponent.java", StdFileTypes.JAVA.getIcon()));
    descriptor.addTemplate(new FileTemplateDescriptor("ModuleComponent.java", StdFileTypes.JAVA.getIcon()));
    descriptor.addTemplate(new FileTemplateDescriptor("Action.java", StdFileTypes.JAVA.getIcon()));
    return descriptor;
  }

  @Nullable
  public Icon getIcon(@NotNull final PsiElement element, final int flags) {
    @NonNls final String pluginXml = "plugin.xml";
    if (element instanceof XmlFile && Comparing.strEqual(((XmlFile)element).getName(), pluginXml)) {
      return ICON;
    }
    return null;
  }
}