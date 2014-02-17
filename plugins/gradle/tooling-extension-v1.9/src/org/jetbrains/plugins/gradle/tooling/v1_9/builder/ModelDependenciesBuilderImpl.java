/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.tooling.v1_9.builder;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.gradle.plugins.ide.internal.IdeDependenciesExtractor;
import org.gradle.tooling.model.idea.IdeaDependency;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.GradleDependencyScope;
import org.jetbrains.plugins.gradle.model.ProjectDependenciesModel;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService;
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions;
import org.jetbrains.plugins.gradle.tooling.internal.*;
import org.jetbrains.plugins.gradle.tooling.v1_9.internal.InternalDependencyVersionId;
import org.jetbrains.plugins.gradle.tooling.v1_9.internal.ModelDependenciesBuilderImplHelper;

import java.util.*;

/**
 * @author Vladislav.Soroka
 * @since 11/5/13
 */
@TargetVersions("1.8 <=> 1.10")
public class ModelDependenciesBuilderImpl implements ModelBuilderService {
  @Override
  public boolean canBuild(String modelName) {
    return ProjectDependenciesModel.class.getName().equals(modelName);
  }

  @Nullable
  @Override
  public Object buildAll(final String modelName, final Project project) {

    final List<IdeaDependency> dependencies = new ArrayList<IdeaDependency>();

    final Map<InternalDependencyVersionId, Scopes> scopesMap = new LinkedHashMap<InternalDependencyVersionId, Scopes>();
    final IdeDependenciesExtractor dependenciesExtractor = new IdeDependenciesExtractor();

    boolean offline = false;
    boolean downloadJavadoc = false;
    boolean downloadSources = true;

    final IdeaPlugin ideaPlugin = project.getPlugins().getPlugin(IdeaPlugin.class);
    Map<String, Map<String, Collection<Configuration>>> userScopes = Collections.emptyMap();
    if (ideaPlugin != null) {
      IdeaModel ideaModel = ideaPlugin.getModel();
      if (ideaModel != null && ideaModel.getModule() != null) {
        offline = ideaModel.getModule().isOffline();
        downloadJavadoc = ideaModel.getModule().isDownloadJavadoc();
        downloadSources = ideaModel.getModule().isDownloadSources();
        userScopes = ideaModel.getModule().getScopes();
      }
    }

    for (final Configuration configuration : project.getConfigurations()) {
      Collection<Configuration> plusConfigurations = new ArrayList<Configuration>();
      plusConfigurations.add(configuration);

      final List<IdeDependenciesExtractor.IdeProjectDependency> ideProjectDependencies =
        dependenciesExtractor.extractProjectDependencies(plusConfigurations, new ArrayList<Configuration>());

      for (IdeDependenciesExtractor.IdeProjectDependency ideProjectDependency : ideProjectDependencies) {
        ModelDependenciesBuilderImplHelper.merge(scopesMap, ideProjectDependency, userScopes);
      }

      if (!offline) {
        final Collection<IdeDependenciesExtractor.IdeRepoFileDependency> ideRepoFileDependencies =
          dependenciesExtractor.extractRepoFileDependencies(
            project.getConfigurations(), plusConfigurations, new ArrayList<Configuration>(), downloadSources, downloadJavadoc);
        for (IdeDependenciesExtractor.IdeRepoFileDependency repoFileDependency : ideRepoFileDependencies) {
          ModelDependenciesBuilderImplHelper.merge(scopesMap, repoFileDependency, userScopes);
        }
      }

      final List<IdeDependenciesExtractor.IdeLocalFileDependency> ideLocalFileDependencies =
        dependenciesExtractor.extractLocalFileDependencies(plusConfigurations, new ArrayList<Configuration>());
      for (IdeDependenciesExtractor.IdeLocalFileDependency fileDependency : ideLocalFileDependencies) {
        ModelDependenciesBuilderImplHelper.merge(scopesMap, fileDependency, userScopes);
      }
    }

    for (Map.Entry<InternalDependencyVersionId, Scopes> entry : scopesMap.entrySet()) {
      InternalDependencyVersionId versionId = entry.getKey();
      for (GradleDependencyScope scope : entry.getValue().getScopes()) {
        if (versionId.getIdeDependency() instanceof IdeDependenciesExtractor.IdeRepoFileDependency) {
          IdeDependenciesExtractor.IdeRepoFileDependency repoFileDependency =
            (IdeDependenciesExtractor.IdeRepoFileDependency)versionId.getIdeDependency();
          IdeaSingleEntryLibraryDependencyImpl libraryDependency = new IdeaSingleEntryLibraryDependencyImpl(
            new IdeaDependencyScopeImpl(scope),
            versionId.getName(),
            versionId.getGroup(),
            versionId.getVersion(),
            versionId.getClassifier()
          );
          libraryDependency.setFile(repoFileDependency.getFile());
          libraryDependency.setSource(repoFileDependency.getSourceFile());
          libraryDependency.setJavadoc(repoFileDependency.getJavadocFile());
          dependencies.add(libraryDependency);
        }
        else if (versionId.getIdeDependency() instanceof IdeDependenciesExtractor.IdeProjectDependency) {
          IdeDependenciesExtractor.IdeProjectDependency projectDependency =
            (IdeDependenciesExtractor.IdeProjectDependency)versionId.getIdeDependency();

          String ideaModuleName = ModelDependenciesBuilderImplHelper.findDeDuplicatedModuleName(projectDependency.getProject());
          if (ideaModuleName == null) {
            ideaModuleName = versionId.getName();
          }

          IdeaModuleDependencyImpl moduleDependency = new IdeaModuleDependencyImpl(
            new IdeaDependencyScopeImpl(scope),
            ideaModuleName,
            versionId.getGroup(),
            versionId.getVersion(),
            versionId.getClassifier()
          );
          moduleDependency.setIdeaModule(new StubIdeaModule(ideaModuleName));
          dependencies.add(moduleDependency);
        }
        else if (versionId.getIdeDependency() instanceof IdeDependenciesExtractor.IdeLocalFileDependency) {
          IdeDependenciesExtractor.IdeLocalFileDependency fileDependency =
            (IdeDependenciesExtractor.IdeLocalFileDependency)versionId.getIdeDependency();
          IdeaSingleEntryLibraryDependencyImpl libraryDependency = new IdeaSingleEntryLibraryDependencyImpl(
            new IdeaDependencyScopeImpl(scope),
            versionId.getName(),
            versionId.getGroup(),
            versionId.getVersion(),
            versionId.getClassifier()
          );
          libraryDependency.setFile(fileDependency.getFile());
          ModelDependenciesBuilderImplHelper.attachGradleSdkSources(libraryDependency, fileDependency);
          dependencies.add(libraryDependency);
        }
      }
    }

    return new ProjectDependenciesModelImpl(project.getPath(), dependencies);
  }
}
