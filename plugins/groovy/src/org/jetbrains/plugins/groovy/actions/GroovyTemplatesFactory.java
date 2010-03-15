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

package org.jetbrains.plugins.groovy.actions;

import com.intellij.ide.fileTemplates.*;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyIcons;

import java.util.ArrayList;
import java.util.Properties;

public class GroovyTemplatesFactory implements FileTemplateGroupDescriptorFactory {
  @NonNls
  public static final String[] TEMPLATES = {"GroovyClass.groovy", "GroovyScript.groovy", "GroovyControllerTests.groovy",};

  public void registerCustromTemplates(String... templates) {
    for (String template : templates) {
      myCustomTemplates.add(template);
    }
  }

  private static class GroovyTemplatesFactoryHolder {
    private static final GroovyTemplatesFactory myInstance = new GroovyTemplatesFactory();
  }

  public static GroovyTemplatesFactory getInstance() {
    return GroovyTemplatesFactoryHolder.myInstance;
  }

  private final ArrayList<String> myCustomTemplates = new ArrayList<String>();

  public static final String GSP_TEMPLATE = "GroovyServerPage.gsp";
  @NonNls
  static final String NAME_TEMPLATE_PROPERTY = "NAME";
  static final String LOW_CASE_NAME_TEMPLATE_PROPERTY = "lowCaseName";

  public FileTemplateGroupDescriptor getFileTemplatesDescriptor() {
    final FileTemplateGroupDescriptor group =
      new FileTemplateGroupDescriptor(GroovyBundle.message("file.template.group.title.groovy"), GroovyIcons.GROOVY_ICON_16x16);
    final FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    for (String template : TEMPLATES) {
      group.addTemplate(new FileTemplateDescriptor(template, fileTypeManager.getFileTypeByFileName(template).getIcon()));
    }
    //add GSP Template
    group.addTemplate(new FileTemplateDescriptor(GSP_TEMPLATE, fileTypeManager.getFileTypeByFileName(GSP_TEMPLATE).getIcon()));

    // register custom templates
    for (String template : getInstance().getCustomTemplates()) {
      group.addTemplate(new FileTemplateDescriptor(template, fileTypeManager.getFileTypeByFileName(template).getIcon()));
    }
    return group;
  }


  public static PsiFile createFromTemplate(final PsiDirectory directory,
                                           final String name,
                                           String fileName,
                                           String templateName,
                                           @NonNls String... parameters) throws IncorrectOperationException {
    final FileTemplate template = FileTemplateManager.getInstance().getInternalTemplate(templateName);

    Properties properties = new Properties(FileTemplateManager.getInstance().getDefaultProperties());
    JavaTemplateUtil.setPackageNameAttribute(properties, directory);
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
      throw new RuntimeException("Unable to load template for " + FileTemplateManager.getInstance().internalTemplateToSubject(templateName),
                                 e);
    }

    final PsiFileFactory factory = PsiFileFactory.getInstance(directory.getProject());
    final PsiFile file = factory.createFileFromText(fileName, text);

    return (PsiFile)directory.add(file);
  }

  public String[] getCustomTemplates() {
    return ArrayUtil.toStringArray(myCustomTemplates);
  }
}
