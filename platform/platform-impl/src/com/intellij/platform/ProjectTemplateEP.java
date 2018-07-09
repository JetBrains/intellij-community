/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.platform;

import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.xmlb.annotations.Attribute;

/**
 * Allows to define a project template in plugin.xml.
 *
 * @author Dmitry Avdeev
 */
public class ProjectTemplateEP extends AbstractExtensionPointBean {

  public static final ExtensionPointName<ProjectTemplateEP> EP_NAME = ExtensionPointName.create("com.intellij.projectTemplate");

  /**
   * If the category attribute is set to true, specifies the title under which the template appears in the first page
   * of the new project dialog. If the category attribute is set to false, specifies the module type ID for which
   * the template is displayed in the "Create project from template" list.
   */
  @Attribute("projectType")
  public String projectType;

  /**
   * The path to a .zip file containing the template contents of the project. The top level directory of the archive
   * is ignored (i.e. the contents of the archive must be a single directory, which is going to be renamed to the
   * name of the project the user is creating). Under that directory, .idea/description.html specifies the description
   * of the template and .idea/project-template.xml specifies additional metadata for the template.
   */
  @Attribute("templatePath")
  public String templatePath;

  /**
   * If true, this template will be offered on the first page of the new project wizard dialog, and the value
   * of the projectType attribute will define the top-level category under which the template will appear.
   *
   * If false, the template will be offered on the second page of the dialog, under the "[x] Create project from template"
   * option, and the projectType attribute is the module type ID for which the template will be available
   * (for example, "JAVA_MODULE" for a regular Java module).
   */
  @Attribute("category")
  public boolean category;
}
