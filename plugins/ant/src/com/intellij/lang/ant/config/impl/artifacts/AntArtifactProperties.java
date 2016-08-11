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
package com.intellij.lang.ant.config.impl.artifacts;

import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.lang.ant.config.AntBuildModel;
import com.intellij.lang.ant.config.AntBuildTarget;
import com.intellij.lang.ant.config.AntConfiguration;
import com.intellij.lang.ant.config.impl.AntConfigurationImpl;
import com.intellij.lang.ant.config.impl.BuildFileProperty;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactProperties;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.ArtifactPropertiesEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ant.model.impl.artifacts.AntArtifactExtensionProperties;
import org.jetbrains.jps.ant.model.impl.artifacts.JpsAntArtifactExtensionImpl;

import java.util.ArrayList;
import java.util.List;

/**
* @author nik
*/
public class AntArtifactProperties extends ArtifactProperties<AntArtifactExtensionProperties> {
  private AntArtifactExtensionProperties myExtensionProperties = new AntArtifactExtensionProperties();
  private boolean myPostProcessing;

  public AntArtifactProperties(boolean postProcessing) {
    myPostProcessing = postProcessing;
  }

  public ArtifactPropertiesEditor createEditor(@NotNull ArtifactEditorContext context) {
    return new AntArtifactPropertiesEditor(this, context, myPostProcessing);
  }

  public AntArtifactExtensionProperties getState() {
    return myExtensionProperties;
  }

  @Override
  public void onBuildStarted(@NotNull Artifact artifact, @NotNull CompileContext compileContext) {
    if (!myPostProcessing) {
      runAntTarget(compileContext, artifact);
    }
  }

  @Override
  public void onBuildFinished(@NotNull Artifact artifact, @NotNull final CompileContext compileContext) {
    if (myPostProcessing) {
      runAntTarget(compileContext, artifact);
    }
  }

  private void runAntTarget(CompileContext compileContext, final Artifact artifact) {
    if (myExtensionProperties.myEnabled) {
      final Project project = compileContext.getProject();
      final AntBuildTarget target = findTarget(AntConfiguration.getInstance(project));
      if (target != null) {
        final DataContext dataContext = SimpleDataContext.getProjectContext(project);
        List<BuildFileProperty> properties = getAllProperties(artifact);
        final boolean success = AntConfigurationImpl.executeTargetSynchronously(dataContext, target, properties);
        if (!success) {
          compileContext.addMessage(CompilerMessageCategory.ERROR, "Cannot build artifact '" + artifact.getName() + "': ant target '" + target.getDisplayName() + "' failed with error", null, -1, -1);
        }
      }
    }
  }

  public void loadState(AntArtifactExtensionProperties state) {
    myExtensionProperties = state;
  }

  public String getFileUrl() {
    return myExtensionProperties.myFileUrl;
  }

  public String getTargetName() {
    return myExtensionProperties.myTargetName;
  }

  public boolean isEnabled() {
    return myExtensionProperties.myEnabled;
  }

  public List<BuildFileProperty> getUserProperties() {
    return myExtensionProperties.myUserProperties;
  }

  public void setUserProperties(List<BuildFileProperty> userProperties) {
    myExtensionProperties.myUserProperties = userProperties;
  }

  public void setEnabled(boolean enabled) {
    myExtensionProperties.myEnabled = enabled;
  }

  public void setFileUrl(String fileUrl) {
    myExtensionProperties.myFileUrl = fileUrl;
  }

  public void setTargetName(String targetName) {
    myExtensionProperties.myTargetName = targetName;
  }

  @Nullable
  public AntBuildTarget findTarget(final AntConfiguration antConfiguration) {
    String fileUrl = getFileUrl();
    String targetName = getTargetName();
    if (fileUrl == null || targetName == null) return null;

    for (AntBuildFile buildFile : antConfiguration.getBuildFileList()) {
      final VirtualFile file = buildFile.getVirtualFile();
      if (file != null && file.getUrl().equals(fileUrl)) {
        final AntBuildModel buildModel = buildFile.getModel();
        return buildModel != null ? buildModel.findTarget(targetName) : null;
      }
    }
    return null;
  }

  public List<BuildFileProperty> getAllProperties(@NotNull Artifact artifact) {
    final List<BuildFileProperty> properties = new ArrayList<>();
    properties.add(new BuildFileProperty(JpsAntArtifactExtensionImpl.ARTIFACT_OUTPUT_PATH_PROPERTY, artifact.getOutputPath()));
    properties.addAll(myExtensionProperties.myUserProperties);
    return properties;
  }

  public static boolean isPredefinedProperty(String propertyName) {
    return JpsAntArtifactExtensionImpl.ARTIFACT_OUTPUT_PATH_PROPERTY.equals(propertyName);
  }
}
