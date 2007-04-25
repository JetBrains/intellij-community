/*
 *  Copyright 2000-2007 JetBrains s.r.o.
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.jetbrains.plugins.groovy.actions;

import com.intellij.ide.fileTemplates.FileTemplateDescriptor;
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptor;
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptorFactory;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileTypes.FileTypeManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.Icons;

public class GroovyTemplatesFactory implements FileTemplateGroupDescriptorFactory, ApplicationComponent {
  @NonNls
  public static final String[] TEMPLATES = {
          "GroovyClass.groovy", "GroovyScript.groovy"
  };

  public FileTemplateGroupDescriptor getFileTemplatesDescriptor() {
    final FileTemplateGroupDescriptor group = new FileTemplateGroupDescriptor(GroovyBundle.message("file.template.group.title.groovy"),
            Icons.SMALLEST);
    final FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    for (String template : TEMPLATES) {
      group.addTemplate(new FileTemplateDescriptor(template, fileTypeManager.getFileTypeByFileName(template).getIcon()));
    }
    return group;
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "GroovyTemplatesFactory";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }
}
