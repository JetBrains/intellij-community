/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.ide.fileTemplates.*;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyFileType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Properties;

public class GroovyTemplatesFactory implements FileTemplateGroupDescriptorFactory {
  @NonNls
  public static final String[] TEMPLATES = {GroovyTemplates.GROOVY_CLASS, GroovyTemplates.GROOVY_SCRIPT};

  public void registerCustromTemplates(String... templates) {
    Collections.addAll(myCustomTemplates, templates);
  }

  private static class GroovyTemplatesFactoryHolder {
    private static final GroovyTemplatesFactory myInstance = new GroovyTemplatesFactory();
  }

  public static GroovyTemplatesFactory getInstance() {
    return GroovyTemplatesFactoryHolder.myInstance;
  }

  private final ArrayList<String> myCustomTemplates = new ArrayList<>();

  @NonNls
  static final String NAME_TEMPLATE_PROPERTY = "NAME";
  static final String LOW_CASE_NAME_TEMPLATE_PROPERTY = "lowCaseName";

  @Override
  public FileTemplateGroupDescriptor getFileTemplatesDescriptor() {
    final FileTemplateGroupDescriptor group = new FileTemplateGroupDescriptor(GroovyBundle.message("file.template.group.title.groovy"), JetgroovyIcons.Groovy.Groovy_16x16);
    final FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    for (String template : TEMPLATES) {
      group.addTemplate(new FileTemplateDescriptor(template, fileTypeManager.getFileTypeByFileName(template).getIcon()));
    }
    //add GSP Template
    group.addTemplate(new FileTemplateDescriptor(
      GroovyTemplates.GROOVY_SERVER_PAGE, fileTypeManager.getFileTypeByFileName(GroovyTemplates.GROOVY_SERVER_PAGE).getIcon()));

    // register custom templates
    for (String template : getInstance().getCustomTemplates()) {
      group.addTemplate(new FileTemplateDescriptor(template, fileTypeManager.getFileTypeByFileName(template).getIcon()));
    }
    return group;
  }


  public static PsiFile createFromTemplate(@NotNull final PsiDirectory directory,
                                           @NotNull final String name,
                                           @NotNull String fileName,
                                           @NotNull String templateName,
                                           boolean allowReformatting,
                                           @NonNls String... parameters) throws IncorrectOperationException {
    final FileTemplate template = FileTemplateManager.getInstance(directory.getProject()).getInternalTemplate(templateName);

    Project project = directory.getProject();

    Properties properties = new Properties(FileTemplateManager.getInstance(project).getDefaultProperties());
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
      throw new RuntimeException("Unable to load template for " + FileTemplateManager.getInstance(project).internalTemplateToSubject(templateName), e);
    }

    final PsiFileFactory factory = PsiFileFactory.getInstance(project);
    PsiFile file = factory.createFileFromText(fileName, GroovyFileType.GROOVY_FILE_TYPE, text);

    file = (PsiFile)directory.add(file);

    if (file != null && allowReformatting && template.isReformatCode()) {
      new ReformatCodeProcessor(project, file, null, false).run();
    }

    return file;
  }

  public String[] getCustomTemplates() {
    return ArrayUtil.toStringArray(myCustomTemplates);
  }
}
