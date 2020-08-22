// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module.impl;

import com.intellij.openapi.module.ConfigurationErrorDescription;
import com.intellij.openapi.module.ConfigurationErrorType;
import com.intellij.projectModel.ProjectModelBundle;
import org.jetbrains.annotations.NotNull;

public final class ModuleLoadingErrorDescription extends ConfigurationErrorDescription {
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
