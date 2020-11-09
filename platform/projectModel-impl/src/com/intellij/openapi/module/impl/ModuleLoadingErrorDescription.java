// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module.impl;

import com.intellij.openapi.module.ConfigurationErrorDescription;
import com.intellij.openapi.module.ConfigurationErrorType;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.projectModel.ProjectModelBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public final class ModuleLoadingErrorDescription extends ConfigurationErrorDescription {
  public static final ConfigurationErrorType MODULE_ERROR = new ConfigurationErrorType(false) {
    @Override
    public @Nls @NotNull String getErrorText(int errorCount, @NlsSafe String firstElementName) {
      return ProjectModelBundle.message("module.configuration.problem.text", errorCount, firstElementName);
    }
  };

  private final ModulePath myModulePath;
  private final ModuleManagerImpl myModuleManager;

  ModuleLoadingErrorDescription(@NlsContexts.DetailedDescription String description, @NotNull ModulePath modulePath, @NotNull ModuleManagerImpl moduleManager) {
    super(modulePath.getModuleName(), description, MODULE_ERROR);
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
  public @NotNull String getIgnoreConfirmationMessage() {
    return ProjectModelBundle.message("module.remove.from.project.confirmation", getElementName());
  }
}
