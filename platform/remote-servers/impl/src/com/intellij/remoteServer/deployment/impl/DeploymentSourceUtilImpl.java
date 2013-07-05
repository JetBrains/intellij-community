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
package com.intellij.remoteServer.deployment.impl;

import com.intellij.openapi.project.Project;
import com.intellij.packaging.artifacts.ArtifactPointer;
import com.intellij.packaging.artifacts.ArtifactPointerManager;
import com.intellij.remoteServer.deployment.ArtifactDeploymentSource;
import com.intellij.remoteServer.deployment.DeploymentSource;
import com.intellij.remoteServer.deployment.DeploymentSourceUtil;
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
  public DeploymentSource loadDeploymentSource(@NotNull Element element, @NotNull Project project) {
    ArtifactPointerManager artifactPointerManager = ArtifactPointerManager.getInstance(project);
    Element artifact = element.getChild("artifact");
    return createArtifactDeploymentSource(artifactPointerManager.createPointer(artifact.getAttributeValue("name")));
  }

  @Override
  public void saveDeploymentSource(@NotNull DeploymentSource source, @NotNull Element element, @NotNull Project project) {
    if (source instanceof ArtifactDeploymentSource) {
      String artifactName = ((ArtifactDeploymentSource)source).getArtifactPointer().getArtifactName();
      element.addContent(new Element("artifact").setAttribute("name", artifactName));
    }
  }
}
