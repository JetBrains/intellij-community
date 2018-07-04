// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit;

import com.intellij.icons.AllIcons;
import com.intellij.ide.fileTemplates.FileTemplateDescriptor;
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptor;
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptorFactory;
import com.intellij.openapi.fileTypes.StdFileTypes;

public class DevKitFileTemplatesFactory implements FileTemplateGroupDescriptorFactory {

  public FileTemplateGroupDescriptor getFileTemplatesDescriptor() {
    FileTemplateGroupDescriptor descriptor = new FileTemplateGroupDescriptor(DevKitBundle.message("plugin.descriptor"),
                                                                             AllIcons.Nodes.Plugin);
    descriptor.addTemplate(new FileTemplateDescriptor("gradleBasedPlugin.xml", StdFileTypes.XML.getIcon()) {
      @Override
      public String getDisplayName() {
        return "plugin.xml in Gradle-based projects";
      }
    });
    descriptor.addTemplate(new FileTemplateDescriptor("plugin.xml", StdFileTypes.XML.getIcon()));
    descriptor.addTemplate(new FileTemplateDescriptor("ProjectServiceClass.java", StdFileTypes.JAVA.getIcon()));
    descriptor.addTemplate(new FileTemplateDescriptor("ProjectServiceInterface.java", StdFileTypes.JAVA.getIcon()));
    descriptor.addTemplate(new FileTemplateDescriptor("ProjectServiceImplementation.java", StdFileTypes.JAVA.getIcon()));
    descriptor.addTemplate(new FileTemplateDescriptor("ApplicationServiceClass.java", StdFileTypes.JAVA.getIcon()));
    descriptor.addTemplate(new FileTemplateDescriptor("ApplicationServiceInterface.java", StdFileTypes.JAVA.getIcon()));
    descriptor.addTemplate(new FileTemplateDescriptor("ApplicationServiceImplementation.java", StdFileTypes.JAVA.getIcon()));
    descriptor.addTemplate(new FileTemplateDescriptor("ModuleServiceClass.java", StdFileTypes.JAVA.getIcon()));
    descriptor.addTemplate(new FileTemplateDescriptor("ModuleServiceInterface.java", StdFileTypes.JAVA.getIcon()));
    descriptor.addTemplate(new FileTemplateDescriptor("ModuleServiceImplementation.java", StdFileTypes.JAVA.getIcon()));
    descriptor.addTemplate(new FileTemplateDescriptor("Action.java", StdFileTypes.JAVA.getIcon()));
    descriptor.addTemplate(new FileTemplateDescriptor("InspectionDescription.html", StdFileTypes.HTML.getIcon()));
    return descriptor;
  }

}
