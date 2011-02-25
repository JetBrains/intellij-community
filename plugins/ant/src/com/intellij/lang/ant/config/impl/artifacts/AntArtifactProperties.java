/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.lang.ant.config.*;
import com.intellij.lang.ant.config.impl.AntConfigurationImpl;
import com.intellij.lang.ant.config.impl.BuildFileProperty;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactProperties;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.ArtifactPropertiesEditor;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
* @author nik
*/
public class AntArtifactProperties extends ArtifactProperties<AntArtifactProperties> {
  @NonNls private static final String ARTIFACT_OUTPUT_PATH_PROPERTY = "artifact.output.path";
  private String myFileUrl;
  private String myTargetName;
  private boolean myEnabled;
  private boolean myPostProcessing;
  private List<BuildFileProperty> myUserProperties = new ArrayList<BuildFileProperty>();

  public AntArtifactProperties() {
  }

  public AntArtifactProperties(boolean postProcessing) {
    myPostProcessing = postProcessing;
  }

  public ArtifactPropertiesEditor createEditor(@NotNull ArtifactEditorContext context) {
    return new AntArtifactPropertiesEditor(this, context, myPostProcessing);
  }

  public AntArtifactProperties getState() {
    return this;
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
    if (myEnabled) {
      final Project project = compileContext.getProject();
      final AntBuildTarget target = findTarget(AntConfiguration.getInstance(project));
      if (target != null) {
        final List<BuildFileProperty> properties = getAllProperties(artifact);
        final DataContext dataContext = SimpleDataContext.getProjectContext(project);
        if (!myPostProcessing) {
          final boolean success = AntConfigurationImpl.executeTargetSynchronously(dataContext, target, properties);
          if (!success) {
            compileContext.addMessage(CompilerMessageCategory.ERROR, "Cannot build artifact '" + artifact.getName() + "': ant target '" + target.getDisplayName() + "' failed with error", null, -1, -1);
          }
          return;
        }
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            target.run(dataContext, properties, AntBuildListener.NULL);
          }
        });
      }
    }
  }

  public void loadState(AntArtifactProperties state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  @Tag("file")
  public String getFileUrl() {
    return myFileUrl;
  }

  @Tag("target")
  public String getTargetName() {
    return myTargetName;
  }

  @Attribute("enabled")
  public boolean isEnabled() {
    return myEnabled;
  }

  @Tag("build-properties")
  @AbstractCollection(surroundWithTag = false)
  public List<BuildFileProperty> getUserProperties() {
    return myUserProperties;
  }

  public void setUserProperties(List<BuildFileProperty> userProperties) {
    myUserProperties = userProperties;
  }

  public void setEnabled(boolean enabled) {
    myEnabled = enabled;
  }

  public void setFileUrl(String fileUrl) {
    myFileUrl = fileUrl;
  }

  public void setTargetName(String targetName) {
    myTargetName = targetName;
  }

  @Nullable
  public AntBuildTarget findTarget(final AntConfiguration antConfiguration) {
    if (myFileUrl == null || myTargetName == null) return null;

    final AntBuildFile[] buildFiles = antConfiguration.getBuildFiles();
    for (AntBuildFile buildFile : buildFiles) {
      final VirtualFile file = buildFile.getVirtualFile();
      if (file != null && file.getUrl().equals(myFileUrl)) {
        final AntBuildModel buildModel = buildFile.getModel();
        return buildModel != null ? buildModel.findTarget(myTargetName) : null;
      }
    }
    return null;
  }

  public List<BuildFileProperty> getAllProperties(@NotNull Artifact artifact) {
    final List<BuildFileProperty> properties = new ArrayList<BuildFileProperty>();
    properties.add(new BuildFileProperty(ARTIFACT_OUTPUT_PATH_PROPERTY, artifact.getOutputPath()));
    properties.addAll(myUserProperties);
    return properties;
  }

  public static boolean isPredefinedProperty(String propertyName) {
    return ARTIFACT_OUTPUT_PATH_PROPERTY.equals(propertyName);
  }
}
