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
package com.intellij.appengine.maven;

import com.intellij.appengine.facet.AppEngineFacet;
import com.intellij.appengine.facet.AppEngineFacetConfiguration;
import com.intellij.appengine.facet.AppEngineFacetType;
import com.intellij.appengine.facet.AppEngineWebIntegration;
import com.intellij.appengine.sdk.impl.AppEngineSdkUtil;
import com.intellij.facet.FacetType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.util.io.ZipUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.importing.FacetImporter;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import org.jetbrains.idea.maven.importing.MavenRootModelAdapter;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenArtifactInfo;
import org.jetbrains.idea.maven.model.MavenPlugin;
import org.jetbrains.idea.maven.model.MavenRemoteRepository;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * @author nik
 */
public class AppEngineFacetImporter extends FacetImporter<AppEngineFacet, AppEngineFacetConfiguration, AppEngineFacetType> {
  public AppEngineFacetImporter() {
    super("com.google.appengine", "appengine-maven-plugin", FacetType.findInstance(AppEngineFacetType.class));
  }

  @Override
  public void resolve(Project project,
                      MavenProject mavenProject,
                      NativeMavenProjectHolder nativeMavenProject,
                      MavenEmbedderWrapper embedder,
                      ResolveContext context) throws MavenProcessCanceledException {
    String version = getVersion(mavenProject);
    if (version != null) {
      List<MavenRemoteRepository> repos = mavenProject.getRemoteRepositories();
      MavenArtifactInfo artifactInfo = new MavenArtifactInfo("com.google.appengine", "appengine-java-sdk", version, "zip", null);
      MavenArtifact artifact = embedder.resolve(artifactInfo, repos);
      File file = artifact.getFile();
      File unpackedSdkPath = new File(file.getParentFile(), "appengine-java-sdk");
      if (file.exists() && !AppEngineSdkUtil.checkPath(FileUtil.toSystemIndependentName(unpackedSdkPath.getAbsolutePath())).isOk()) {
        try {
          ZipUtil.extract(file, unpackedSdkPath, null, false);
        }
        catch (IOException e) {
          MavenLog.LOG.warn("cannot unpack AppEngine SDK", e);
        }
      }
    }
  }

  @Nullable
  private String getVersion(MavenProject project) {
    for (MavenArtifact artifact : project.findDependencies("com.google.appengine", "appengine-api-1.0-sdk")) {
      String artifactVersion = artifact.getVersion();
      if (artifactVersion != null) return artifactVersion;
    }
    MavenPlugin plugin = project.findPlugin(myPluginGroupID, myPluginArtifactID);
    return plugin != null ? plugin.getVersion() : null;
  }

  @Override
  protected void setupFacet(AppEngineFacet f, MavenProject mavenProject) {

  }

  @Override
  protected void reimportFacet(IdeModifiableModelsProvider modelsProvider,
                               Module module,
                               MavenRootModelAdapter rootModel,
                               AppEngineFacet facet,
                               MavenProjectsTree mavenTree,
                               MavenProject mavenProject,
                               MavenProjectChanges changes,
                               Map<MavenProject, String> mavenProjectToModuleName,
                               List<MavenProjectsProcessorTask> postTasks) {
    String version = getVersion(mavenProject);
    if (version != null) {
      String relativePath = "/com/google/appengine/appengine-java-sdk/" + version + "/appengine-java-sdk/appengine-java-sdk-" + version;
      facet.getConfiguration().setSdkHomePath(FileUtil.toSystemIndependentName(mavenProject.getLocalRepository().getPath()) + relativePath);
      AppEngineWebIntegration.getInstance().setupDevServer(facet.getSdk());
      final String artifactName = module.getName() + ":war exploded";
      final Artifact webArtifact = modelsProvider.getModifiableArtifactModel().findArtifact(artifactName);
      AppEngineWebIntegration.getInstance().setupRunConfiguration(facet.getSdk(), webArtifact, module.getProject());
    }
  }
}
