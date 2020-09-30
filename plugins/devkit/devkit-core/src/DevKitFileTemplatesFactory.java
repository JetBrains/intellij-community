// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit;

import com.intellij.icons.AllIcons;
import com.intellij.ide.fileTemplates.FileTemplateDescriptor;
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptor;
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptorFactory;
import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.highlighter.XmlFileType;
import org.jetbrains.annotations.NotNull;

public class DevKitFileTemplatesFactory implements FileTemplateGroupDescriptorFactory {
  @Override
  public FileTemplateGroupDescriptor getFileTemplatesDescriptor() {
    FileTemplateGroupDescriptor descriptor = new FileTemplateGroupDescriptor(DevKitBundle.message("plugin.descriptor"), AllIcons.Nodes.Plugin);
    descriptor.addTemplate(new FileTemplateDescriptor("gradleBasedPlugin.xml", XmlFileType.INSTANCE.getIcon()) {
      @Override
      public @NotNull String getDisplayName() {
        return DevKitBundle.message("module.wizard.gradle.plugin.xml.template.display.name");
      }
    });
    descriptor.addTemplate(new FileTemplateDescriptor("plugin.xml", XmlFileType.INSTANCE.getIcon()));
    descriptor.addTemplate(new FileTemplateDescriptor("ProjectServiceClass.java", JavaFileType.INSTANCE.getIcon()));
    descriptor.addTemplate(new FileTemplateDescriptor("ProjectServiceInterface.java", JavaFileType.INSTANCE.getIcon()));
    descriptor.addTemplate(new FileTemplateDescriptor("ProjectServiceImplementation.java", JavaFileType.INSTANCE.getIcon()));
    descriptor.addTemplate(new FileTemplateDescriptor("ApplicationServiceClass.java", JavaFileType.INSTANCE.getIcon()));
    descriptor.addTemplate(new FileTemplateDescriptor("ApplicationServiceInterface.java", JavaFileType.INSTANCE.getIcon()));
    descriptor.addTemplate(new FileTemplateDescriptor("ApplicationServiceImplementation.java", JavaFileType.INSTANCE.getIcon()));
    descriptor.addTemplate(new FileTemplateDescriptor("ModuleServiceClass.java", JavaFileType.INSTANCE.getIcon()));
    descriptor.addTemplate(new FileTemplateDescriptor("ModuleServiceInterface.java", JavaFileType.INSTANCE.getIcon()));
    descriptor.addTemplate(new FileTemplateDescriptor("ModuleServiceImplementation.java", JavaFileType.INSTANCE.getIcon()));
    descriptor.addTemplate(new FileTemplateDescriptor("Action.java", JavaFileType.INSTANCE.getIcon()));
    descriptor.addTemplate(new FileTemplateDescriptor("InspectionDescription.html", HtmlFileType.INSTANCE.getIcon()));
    return descriptor;
  }
}
