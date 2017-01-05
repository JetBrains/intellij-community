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
package org.jetbrains.jps.gradle.build;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.Base64;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.artifacts.ArtifactBuildTaskProvider;
import org.jetbrains.jps.gradle.model.JpsGradleExtensionService;
import org.jetbrains.jps.gradle.model.artifacts.JpsGradleArtifactExtension;
import org.jetbrains.jps.gradle.model.impl.artifacts.GradleArtifactExtensionProperties;
import org.jetbrains.jps.incremental.BuildTask;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.elements.JpsArtifactRootElement;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;

/**
 * @author Vladislav.Soroka
 * @since 10/12/2016
 */
public class GradleArtifactBuildTaskProvider extends ArtifactBuildTaskProvider {
  @NotNull
  @Override
  public List<? extends BuildTask> createArtifactBuildTasks(@NotNull JpsArtifact artifact, @NotNull ArtifactBuildPhase buildPhase) {
    String artifactName = artifact.getName();
    if (buildPhase == ArtifactBuildPhase.PRE_PROCESSING && (artifactName.endsWith(" (exploded)"))
        && artifact.getRootElement() instanceof JpsArtifactRootElement) {
      JpsGradleArtifactExtension extension = getArtifactExtension(artifact, buildPhase);
      if (extension != null && extension.getProperties() != null) {
        return ContainerUtil.list(new GradleManifestGenerationBuildTask(artifact, extension.getProperties()),
                                  new GradleAdditionalFilesGenerationBuildTask(artifact, extension.getProperties()));
      }
    }

    return Collections.emptyList();
  }

  @Nullable
  private static JpsGradleArtifactExtension getArtifactExtension(JpsArtifact artifact, ArtifactBuildPhase buildPhase) {
    switch (buildPhase) {
      case PRE_PROCESSING:
        return JpsGradleExtensionService.getArtifactExtension(artifact);
      default:
        return null;
    }
  }

  private abstract static class GradleGenerationBuildTask extends BuildTask {
    protected final JpsArtifact myArtifact;
    protected final GradleArtifactExtensionProperties myProperties;

    public GradleGenerationBuildTask(@NotNull JpsArtifact artifact, @NotNull GradleArtifactExtensionProperties properties) {
      myArtifact = artifact;
      myProperties = properties;
    }
  }

  private static class GradleManifestGenerationBuildTask extends GradleGenerationBuildTask {
    private static final Logger LOG = Logger.getInstance(GradleManifestGenerationBuildTask.class);

    public GradleManifestGenerationBuildTask(@NotNull JpsArtifact artifact,
                                             @NotNull GradleArtifactExtensionProperties properties) {
      super(artifact, properties);
    }

    @Override
    public void build(final CompileContext context) throws ProjectBuildException {
      if (myProperties.manifest != null) {
        try {
          File output = new File(myArtifact.getOutputPath(), JarFile.MANIFEST_NAME);
          FileUtil.writeToFile(output, Base64.decode(myProperties.manifest));
        }
        // do not fail the whole 'Make' if there is an invalid manifest cached
        catch (Exception e) {
          LOG.debug(e);
        }
      }
    }
  }

  private static class GradleAdditionalFilesGenerationBuildTask extends GradleGenerationBuildTask {
    private static final Logger LOG = Logger.getInstance(GradleAdditionalFilesGenerationBuildTask.class);

    public GradleAdditionalFilesGenerationBuildTask(@NotNull JpsArtifact artifact,
                                                    @NotNull GradleArtifactExtensionProperties properties) {
      super(artifact, properties);
    }

    @Override
    public void build(final CompileContext context) throws ProjectBuildException {
      if (myProperties.additionalFiles != null) {
        for (Map.Entry<String, String> entry : myProperties.additionalFiles.entrySet()) {
          try {
            File output = new File(entry.getKey());
            FileUtil.writeToFile(output, Base64.decode(entry.getValue()));
          }
          // do not fail the whole 'Make' if there is an invalid file cached
          catch (Exception e) {
            LOG.debug(e);
          }
        }
      }
    }
  }
}

