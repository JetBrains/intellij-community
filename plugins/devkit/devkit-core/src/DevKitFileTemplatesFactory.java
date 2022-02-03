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

public final class DevKitFileTemplatesFactory implements FileTemplateGroupDescriptorFactory {

  public static final String PLUGIN_XML = "devkit-plugin.xml";
  public static final String BUILD_GRADLE_KTS = "devkit-build.gradle.kts";
  public static final String SETTINGS_GRADLE_KTS = "devkit-settings.gradle.kts";
  public static final String GRADLE_WRAPPER_PROPERTIES = "devkit-gradle-wrapper.properties";

  @Override
  public FileTemplateGroupDescriptor getFileTemplatesDescriptor() {
    FileTemplateGroupDescriptor root = new FileTemplateGroupDescriptor(DevKitBundle.message("module.builder.title"), AllIcons.Nodes.Plugin);
    FileTemplateGroupDescriptor templatesRoot =
      new FileTemplateGroupDescriptor(DevKitBundle.message("file.templates"), AllIcons.Nodes.Plugin);

    templatesRoot.addTemplate(new FileTemplateDescriptor("gradleBasedPlugin.xml", XmlFileType.INSTANCE.getIcon()) {
      @Override
      public @NotNull String getDisplayName() {
        return DevKitBundle.message("module.wizard.gradle.plugin.xml.template.display.name");
      }
    });
    templatesRoot.addTemplate(new FileTemplateDescriptor("plugin.xml", XmlFileType.INSTANCE.getIcon()));
    templatesRoot.addTemplate(new FileTemplateDescriptor("Action.java", JavaFileType.INSTANCE.getIcon()));
    templatesRoot.addTemplate(new FileTemplateDescriptor("InspectionDescription.html", HtmlFileType.INSTANCE.getIcon()));

    FileTemplateGroupDescriptor newProjectRoot =
      new FileTemplateGroupDescriptor(DevKitBundle.message("file.templates.new.plugin"), AllIcons.Nodes.Plugin);

    newProjectRoot.addTemplate(PLUGIN_XML);
    newProjectRoot.addTemplate(BUILD_GRADLE_KTS);
    newProjectRoot.addTemplate(SETTINGS_GRADLE_KTS);
    newProjectRoot.addTemplate(GRADLE_WRAPPER_PROPERTIES);

    root.addTemplate(templatesRoot);
    root.addTemplate(newProjectRoot);

    return root;
  }
}
