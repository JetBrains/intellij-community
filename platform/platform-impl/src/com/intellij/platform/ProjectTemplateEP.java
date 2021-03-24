// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform;

import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.util.xmlb.annotations.Attribute;

/**
 * Registers project template.
 *
 * @author Dmitry Avdeev
 */
public final class ProjectTemplateEP {
  /**
   * If the category attribute is set to {@code true}, specifies the title under which the template appears in the first page
   * of the new project dialog. If the category attribute is set to {@code false}, specifies the module type ID for which
   * the template is displayed in the "Create project from template" list.
   */
  @Attribute("projectType")
  public String projectType;

  /**
   * The path to a {@code .zip} file containing the template contents of the project. The top level directory of the archive
   * is ignored (i.e. the contents of the archive must be a single directory, which is going to be renamed to the
   * name of the project the user is creating). Under that directory, {@code .idea/description.html} specifies the description
   * of the template and {@code .idea/project-template.xml} specifies additional metadata for the template.
   */
  @Attribute("templatePath")
  @RequiredElement
  public String templatePath;

  /**
   * If {@code true}, this template will be offered on the first page of the new project wizard dialog, and the value
   * of the {@link #projectType} attribute will define the top-level category under which the template will appear.
   *
   * If {@code false}, the template will be offered on the second page of the dialog, under the "[x] Create project from template"
   * option, and the {@link #projectType} attribute is the module type ID for which the template will be available
   * (for example, {@code "JAVA_MODULE"} for a regular Java module).
   */
  @Attribute("category")
  public boolean category;
}
