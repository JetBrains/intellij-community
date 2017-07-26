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

public class NewModuleComponentAction extends GenerateComponentClassAndPatchPluginXmlActionBase {
  public NewModuleComponentAction() {
    super(DevKitBundle.message("new.menu.module.component.text"),
          DevKitBundle.message("new.menu.module.component.description"), null);
  }

  protected ComponentType getComponentType() {
    return ComponentType.MODULE;
  }

  protected String getErrorTitle() {
    return DevKitBundle.message("new.module.component.error");
  }

  protected String getCommandName() {
    return DevKitBundle.message("new.module.component.command");
  }

  protected String getClassNamePromptTitle() {
    return DevKitBundle.message("new.module.component.prompt.title");
  }

  protected String getClassTemplateName() {
    return "ModuleComponent.java";
  }

  protected String getClassNamePrompt() {
    return DevKitBundle.message("new.module.component.prompt");
  }

  protected String getActionName(PsiDirectory directory, String newName) {
    return DevKitBundle.message("new.module.component.action.name", directory, newName);
  }
}
