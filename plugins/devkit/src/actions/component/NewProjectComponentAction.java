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
package org.jetbrains.idea.devkit.actions.component;

import com.intellij.psi.PsiDirectory;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.util.ComponentType;

public class NewProjectComponentAction extends GenerateComponentClassAndPatchPluginXmlActionBase {
  public NewProjectComponentAction() {
    super(DevKitBundle.message("new.menu.project.component.text"),
          DevKitBundle.message("new.menu.project.component.description"), null);
  }

  @Override
  protected ComponentType getComponentType() {
    return ComponentType.PROJECT;
  }

  @Override
  protected String getErrorTitle() {
    return DevKitBundle.message("new.project.component.error");
  }

  @Override
  protected String getCommandName() {
    return DevKitBundle.message("new.project.component.command");
  }

  @Override
  protected String getClassNamePromptTitle() {
    return DevKitBundle.message("new.project.component.prompt.title");
  }

  @Override
  protected String getClassTemplateName() {
    return "ProjectComponent.java";
  }

  @Override
  protected String getClassNamePrompt() {
    return DevKitBundle.message("new.project.component.prompt");
  }

  @Override
  protected String getActionName(PsiDirectory directory, String newName) {
    return DevKitBundle.message("new.project.component.action.name", directory, newName);
  }
}
