/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.actions.service;

import org.jetbrains.idea.devkit.DevKitBundle;

public class NewProjectServiceAction extends NewServiceActionBase {
  public NewProjectServiceAction() {
    super(DevKitBundle.message("new.menu.project.service.text"),
          DevKitBundle.message("new.menu.project.service.description"));
  }

  @Override
  protected String getTagName() {
    return "projectService";
  }

  @Override
  protected String getOnlyImplementationTemplateName() {
    return "ProjectServiceClass.java";
  }

  @Override
  protected String getInterfaceTemplateName() {
    return "ProjectServiceInterface.java";
  }

  @Override
  protected String getImplementationTemplateName() {
    return "ProjectServiceImplementation.java";
  }

  @Override
  protected String getDialogTitle() {
    return DevKitBundle.message("new.project.service.dialog.title");
  }
}
