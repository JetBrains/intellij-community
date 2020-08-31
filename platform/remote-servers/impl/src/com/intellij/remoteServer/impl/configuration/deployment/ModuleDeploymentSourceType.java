// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.impl.configuration.deployment;

import com.intellij.openapi.module.ModulePointerManager;
import com.intellij.openapi.project.Project;
import com.intellij.remoteServer.configuration.deployment.DeploymentSourceType;
import com.intellij.remoteServer.configuration.deployment.ModuleDeploymentSource;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public class ModuleDeploymentSourceType extends DeploymentSourceType<ModuleDeploymentSource> {
  private static final String NAME_ATTRIBUTE = "name";

  public ModuleDeploymentSourceType() {
    super("module");
  }

  @NotNull
  @Override
  public ModuleDeploymentSource load(@NotNull Element tag, @NotNull Project project) {
    String moduleName = tag.getAttributeValue(NAME_ATTRIBUTE);
    assert moduleName != null;
    return new ModuleDeploymentSourceImpl(ModulePointerManager.getInstance(project).create(moduleName));
  }

  @Override
  public void save(@NotNull ModuleDeploymentSource source, @NotNull Element tag) {
    tag.setAttribute(NAME_ATTRIBUTE, source.getModulePointer().getModuleName());
  }
}
