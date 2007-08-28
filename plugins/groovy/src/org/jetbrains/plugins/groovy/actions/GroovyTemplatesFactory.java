/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.actions;

import com.intellij.ide.fileTemplates.*;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.Icons;

import java.util.Properties;

public class GroovyTemplatesFactory implements FileTemplateGroupDescriptorFactory, ApplicationComponent {
  @NonNls
  public static final String[] TEMPLATES = {
      "GroovyClass.groovy",
      "GroovyScript.groovy",
      "GrailsDomainClass.groovy",
      "GrailsController.groovy",
      "GrailsTests.groovy",
  };

  public static final String GSP_TEMPLATE = "GroovyServerPage.gsp";
  @NonNls
  static final String NAME_TEMPLATE_PROPERTY = "NAME";
  static final String LOW_CASE_NAME_TEMPLATE_PROPERTY = "lowCaseName";

  public FileTemplateGroupDescriptor getFileTemplatesDescriptor() {
    final FileTemplateGroupDescriptor group = new FileTemplateGroupDescriptor(GroovyBundle.message("file.template.group.title.groovy"),
        Icons.SMALLEST);
    final FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    for (String template : TEMPLATES) {
      group.addTemplate(new FileTemplateDescriptor(template, fileTypeManager.getFileTypeByFileName(template).getIcon()));
    }
    //add GSP Template
    group.addTemplate(new FileTemplateDescriptor(GSP_TEMPLATE, fileTypeManager.getFileTypeByFileName(GSP_TEMPLATE).getIcon()));
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

  public static PsiFile createFromTemplate(final PsiDirectory directory, final String name, String fileName, String templateName,
                                           @NonNls String... parameters) throws IncorrectOperationException {
    final FileTemplate template = FileTemplateManager.getInstance().getJ2eeTemplate(templateName);

    Properties properties = new Properties(FileTemplateManager.getInstance().getDefaultProperties());
    FileTemplateUtil.setPackageNameAttribute(properties, directory);
    properties.setProperty(NAME_TEMPLATE_PROPERTY, name);
    properties.setProperty(LOW_CASE_NAME_TEMPLATE_PROPERTY, name.substring(0, 1).toLowerCase() + name.substring(1));
    for (int i = 0; i < parameters.length; i += 2) {
      properties.setProperty(parameters[i], parameters[i + 1]);
    }
    String text;
    try {
      text = template.getText(properties);
    }
    catch (Exception e) {
      throw new RuntimeException("Unable to load template for " + FileTemplateManager.getInstance().internalTemplateToSubject(templateName), e);
    }

    final PsiManager psiManager = PsiManager.getInstance(directory.getProject());
    final PsiFile file = psiManager.getElementFactory().createFileFromText(fileName, text);

//    CodeStyleManager.getInstance(psiManager).reformat(file, false);

    return (PsiFile) directory.add(file);
  }
}
