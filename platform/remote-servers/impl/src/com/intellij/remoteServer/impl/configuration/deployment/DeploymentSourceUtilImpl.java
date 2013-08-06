/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.remoteServer.impl.configuration.deployment;

import com.intellij.openapi.module.ModulePointer;
import com.intellij.openapi.module.ModulePointerManager;
import com.intellij.openapi.project.Project;
import com.intellij.packaging.artifacts.ArtifactPointer;
import com.intellij.packaging.artifacts.ArtifactPointerManager;
import com.intellij.remoteServer.configuration.deployment.ArtifactDeploymentSource;
import com.intellij.remoteServer.configuration.deployment.DeploymentSource;
import com.intellij.remoteServer.configuration.deployment.DeploymentSourceUtil;
import com.intellij.remoteServer.configuration.deployment.ModuleDeploymentSource;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class DeploymentSourceUtilImpl extends DeploymentSourceUtil {
  @NotNull
  @Override
  public ArtifactDeploymentSource createArtifactDeploymentSource(@NotNull ArtifactPointer artifactPointer) {
    return new ArtifactDeploymentSourceImpl(artifactPointer);
  }

  @Override
  @NotNull
  public ModuleDeploymentSource createModuleDeploymentSource(@NotNull ModulePointer modulePointer) {
    return new ModuleDeploymentSourceImpl(modulePointer);
  }

  @Override
  public DeploymentSource loadDeploymentSource(@NotNull Element element, @NotNull Project project) {
    ArtifactPointerManager artifactPointerManager = ArtifactPointerManager.getInstance(project);
    Element artifact = element.getChild("artifact");
    if (artifact != null) {
      return createArtifactDeploymentSource(artifactPointerManager.createPointer(artifact.getAttributeValue("name")));
    }
    Element module = element.getChild("module");
    ModulePointerManager modulePointerManager = ModulePointerManager.getInstance(project);
    return createModuleDeploymentSource(modulePointerManager.create(module.getAttributeValue("name")));
  }

  @Override
  public void saveDeploymentSource(@NotNull DeploymentSource source, @NotNull Element element, @NotNull Project project) {
    if (source instanceof ArtifactDeploymentSource) {
      String artifactName = ((ArtifactDeploymentSource)source).getArtifactPointer().getArtifactName();
      element.addContent(new Element("artifact").setAttribute("name", artifactName));
    }
    else if (source instanceof ModuleDeploymentSource) {
      String moduleName = ((ModuleDeploymentSource)source).getModulePointer().getModuleName();
      element.addContent(new Element("module").setAttribute("name", moduleName));
    }
  }
}
