/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 *         Date: 9/28/12
 */
public interface ProjectTemplatesFactory {

  ExtensionPointName<ProjectTemplatesFactory> EP_NAME = ExtensionPointName.create("com.intellij.projectTemplatesFactory");

  String OTHER_GROUP = "Other";
  String CUSTOM_GROUP = "User-defined";

  @NotNull
  String[] getGroups();

  @NotNull
  ProjectTemplate[] createTemplates(String group, WizardContext context);
}
