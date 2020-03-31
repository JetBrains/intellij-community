/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.openapi.module.impl;

import com.intellij.openapi.module.ConfigurationErrorDescription;
import com.intellij.openapi.module.ConfigurationErrorType;
import com.intellij.projectModel.ProjectModelBundle;
import org.jetbrains.annotations.NotNull;

public class ModuleLoadingErrorDescription extends ConfigurationErrorDescription {
  private final ModulePath myModulePath;
  private final ModuleManagerImpl myModuleManager;

  ModuleLoadingErrorDescription(String description, @NotNull ModulePath modulePath, @NotNull ModuleManagerImpl moduleManager) {
    super(modulePath.getModuleName(), description, new ConfigurationErrorType(ProjectModelBundle.message("element.kind.name.module"), false));

    myModulePath = modulePath;
    myModuleManager = moduleManager;
  }

  @NotNull
  public ModulePath getModulePath() {
    return myModulePath;
  }

  @Override
  public void ignoreInvalidElement() {
    myModuleManager.removeFailedModulePath(myModulePath);
  }

  @Override
  public String getIgnoreConfirmationMessage() {
    return ProjectModelBundle.message("module.remove.from.project.confirmation", getElementName());
  }
}
