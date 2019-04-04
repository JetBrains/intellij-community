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
import com.intellij.compiler.ant.taskdefs.Property;
import com.intellij.lang.ant.config.impl.BuildFileProperty;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactPropertiesProvider;
import com.intellij.packaging.elements.ArtifactAntGenerationContext;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.ant.model.impl.artifacts.JpsAntArtifactExtensionImpl;

/**
 * @author nik
 */
public class AntArtifactBuildExtension extends ChunkBuildExtension {
  @Override
  public void generateTasksForArtifact(Artifact artifact, boolean preprocessing, ArtifactAntGenerationContext context,
                                       CompositeGenerator generator) {
    final ArtifactPropertiesProvider provider;
    if (preprocessing) {
      provider = AntArtifactPreProcessingPropertiesProvider.getInstance();
    }
    else {
      provider = AntArtifactPostprocessingPropertiesProvider.getInstance();
    }
    final AntArtifactProperties properties = (AntArtifactProperties)artifact.getProperties(provider);
    if (properties != null && properties.isEnabled()) {
      final String path = VfsUtil.urlToPath(properties.getFileUrl());
      String fileName = PathUtil.getFileName(path);
      String dirPath = PathUtil.getParentPath(path);
      final String relativePath = GenerationUtils.toRelativePath(dirPath, BuildProperties.getProjectBaseDir(context.getProject()),
                                                                 BuildProperties.getProjectBaseDirProperty(), context.getGenerationOptions());
      final Tag ant = new Tag("ant", Pair.create("antfile", fileName), Pair.create("target", properties.getTargetName()),
                                     Pair.create("dir", relativePath));
      final String outputPath = BuildProperties.propertyRef(context.getArtifactOutputProperty(artifact));
      ant.add(new Property(JpsAntArtifactExtensionImpl.ARTIFACT_OUTPUT_PATH_PROPERTY, outputPath));
      for (BuildFileProperty property : properties.getUserProperties()) {
        ant.add(new Property(property.getPropertyName(), property.getPropertyValue()));
      }
      generator.add(ant);
    }
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
