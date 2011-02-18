/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.compiler.ant.*;
import com.intellij.compiler.ant.taskdefs.AntCall;
import com.intellij.compiler.ant.taskdefs.Import;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.artifacts.ArtifactPropertiesProvider;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author nik
 */
public class AntArtifactBuildExtension extends ChunkBuildExtension {
  @Override
  public void generateProjectTargets(Project project, GenerationOptions genOptions, CompositeGenerator generator) {
    final Artifact[] artifacts = ArtifactManager.getInstance(project).getSortedArtifacts();
    Set<String> filesToImport = new LinkedHashSet<String>();
    for (Artifact artifact : artifacts) {
      for (Boolean value : Arrays.asList(true, false)) {
        final AntArtifactProperties properties = getProperties(artifact, value);
        if (properties != null) {
          filesToImport.add(properties.getFileUrl());
        }
      }
    }
    for (String url : filesToImport) {
      final String path = VfsUtil.urlToPath(url);
      final String relativePath = GenerationUtils.toRelativePath(path, BuildProperties.getProjectBaseDir(project), BuildProperties.getProjectBaseDirProperty(), genOptions);
      generator.add(new Import(relativePath));
    }
  }

  @Override
  public void generateTasksForArtifact(Project project, Artifact artifact, boolean preprocessing, CompositeGenerator generator) {
    final AntArtifactProperties properties = getProperties(artifact, preprocessing);
    if (properties != null) {
      generator.add(new AntCall(properties.getTargetName()));
    }
  }

  @Nullable
  private static AntArtifactProperties getProperties(Artifact artifact, boolean preprocessing) {
    final ArtifactPropertiesProvider provider;
    if (preprocessing) {
      provider = AntArtifactPreProcessingPropertiesProvider.getInstance();
    }
    else {
      provider = AntArtifactPostprocessingPropertiesProvider.getInstance();
    }
    final AntArtifactProperties properties = (AntArtifactProperties)artifact.getProperties(provider);
    return properties != null && properties.isEnabled() ? properties : null;
  }

  @NotNull
  @Override
  public String[] getTargets(ModuleChunk chunk) {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @Override
  public void process(Project project, ModuleChunk chunk, GenerationOptions genOptions, CompositeGenerator generator) {
  }
}
