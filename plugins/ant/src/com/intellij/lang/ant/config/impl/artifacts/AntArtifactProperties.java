// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant.config.impl.artifacts;

import com.intellij.lang.ant.AntBundle;
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
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactProperties;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.ArtifactPropertiesEditor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ant.model.impl.artifacts.AntArtifactExtensionProperties;
import org.jetbrains.jps.ant.model.impl.artifacts.JpsAntArtifactExtensionImpl;

import java.util.ArrayList;
import java.util.List;

public class AntArtifactProperties extends ArtifactProperties<AntArtifactExtensionProperties> {
  private AntArtifactExtensionProperties myExtensionProperties = new AntArtifactExtensionProperties();
  private final boolean myPostProcessing;

  public AntArtifactProperties(boolean postProcessing) {
    myPostProcessing = postProcessing;
  }

  @Override
  public ArtifactPropertiesEditor createEditor(@NotNull ArtifactEditorContext context) {
    return new AntArtifactPropertiesEditor(this, context, myPostProcessing);
  }

  @Override
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
          String message = AntBundle.message("cannot.build.artifact.using.ant.target", artifact.getName(), target.getDisplayName());
          compileContext.addMessage(CompilerMessageCategory.ERROR, message, null, -1, -1);
        }
      }
    }
  }

  @Override
  public void loadState(@NotNull AntArtifactExtensionProperties state) {
    myExtensionProperties = state;
  }

  public @NlsSafe String getFileUrl() {
    return myExtensionProperties.myFileUrl;
  }

  public @NlsSafe String getTargetName() {
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

  public void setFileUrl(@NlsSafe String fileUrl) {
    myExtensionProperties.myFileUrl = fileUrl;
  }

  public void setTargetName(@NlsSafe String targetName) {
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

  public static boolean isPredefinedProperty(@NonNls String propertyName) {
    return JpsAntArtifactExtensionImpl.ARTIFACT_OUTPUT_PATH_PROPERTY.equals(propertyName);
  }
}
