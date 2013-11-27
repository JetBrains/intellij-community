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
package org.jetbrains.plugins.gradle.model.builder;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.gradle.plugins.ide.internal.IdeDependenciesExtractor;
import org.gradle.tooling.model.idea.IdeaDependency;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.GradleDependencyScope;
import org.jetbrains.plugins.gradle.model.ModelBuilderService;
import org.jetbrains.plugins.gradle.model.ProjectDependenciesModel;
import org.jetbrains.plugins.gradle.model.internal.*;

import java.util.*;

/**
 * @author Vladislav.Soroka
 * @since 11/5/13
 */
public class ModelDependenciesBuilderImpl implements ModelBuilderService {

  @Override
  public boolean canBuild(String modelName) {
    return ProjectDependenciesModel.class.getName().equals(modelName);
  }

  @Nullable
  @Override
  public Object buildAll(final String modelName, final Project project) {
    final List<IdeaDependency> dependencies = new ArrayList<IdeaDependency>();

    final Map<DependencyVersionId, Scopes> scopesMap = new HashMap<DependencyVersionId, Scopes>();
    final IdeDependenciesExtractor dependenciesExtractor = new IdeDependenciesExtractor();

    boolean offline = false;
    boolean downloadJavadoc = false;
    boolean downloadSources = true;

    final IdeaPlugin ideaPlugin = project.getPlugins().getPlugin(IdeaPlugin.class);
    if (ideaPlugin != null) {
      IdeaModel ideaModel = ideaPlugin.getModel();
      if (ideaModel != null && ideaModel.getModule() == null) {
        offline = ideaModel.getModule().isOffline();
        downloadJavadoc = ideaModel.getModule().isDownloadJavadoc();
        downloadSources = ideaModel.getModule().isDownloadSources();
      }
    }

    for (final Configuration configuration : project.getConfigurations()) {
      Collection<Configuration> plusConfigurations = new ArrayList<Configuration>();
      plusConfigurations.add(configuration);

      final List<IdeDependenciesExtractor.IdeProjectDependency> ideProjectDependencies =
        dependenciesExtractor.extractProjectDependencies(plusConfigurations, new ArrayList<Configuration>());

      for (IdeDependenciesExtractor.IdeProjectDependency ideProjectDependency : ideProjectDependencies) {
        merge(scopesMap, ideProjectDependency);
      }

      if (!offline) {
        final Collection<IdeDependenciesExtractor.IdeRepoFileDependency> ideRepoFileDependencies =
          dependenciesExtractor.extractRepoFileDependencies(
            project.getConfigurations(), plusConfigurations, new ArrayList<Configuration>(), downloadSources, downloadJavadoc);
        for (IdeDependenciesExtractor.IdeRepoFileDependency repoFileDependency : ideRepoFileDependencies) {
          merge(scopesMap, repoFileDependency);
        }
      }

      final List<IdeDependenciesExtractor.IdeLocalFileDependency> ideLocalFileDependencies =
        dependenciesExtractor.extractLocalFileDependencies(plusConfigurations, new ArrayList<Configuration>());
      for (IdeDependenciesExtractor.IdeLocalFileDependency fileDependency : ideLocalFileDependencies) {
        merge(scopesMap, fileDependency);
      }
    }

    for (Map.Entry<DependencyVersionId, Scopes> entry : scopesMap.entrySet()) {
      DependencyVersionId versionId = entry.getKey();
      for (GradleDependencyScope scope : entry.getValue().getScopes()) {
        if (versionId.getIdeDependency() instanceof IdeDependenciesExtractor.IdeRepoFileDependency) {
          IdeDependenciesExtractor.IdeRepoFileDependency repoFileDependency =
            (IdeDependenciesExtractor.IdeRepoFileDependency)versionId.getIdeDependency();
          IdeaSingleEntryLibraryDependencyImpl libraryDependency = new IdeaSingleEntryLibraryDependencyImpl(
            new IdeaDependencyScopeImpl(scope),
            versionId.getName(),
            versionId.getGroup(),
            versionId.getVersion()
          );
          libraryDependency.setFile(repoFileDependency.getFile());
          libraryDependency.setSource(repoFileDependency.getSourceFile());
          libraryDependency.setJavadoc(repoFileDependency.getJavadocFile());
          dependencies.add(libraryDependency);
        }
        else if (versionId.getIdeDependency() instanceof IdeDependenciesExtractor.IdeProjectDependency) {
          IdeaModuleDependencyImpl moduleDependency = new IdeaModuleDependencyImpl(
            new IdeaDependencyScopeImpl(scope),
            versionId.getName(),
            versionId.getGroup(),
            versionId.getVersion()
          );
          moduleDependency.setIdeaModule(new StubIdeaModule(versionId.getName()));
          dependencies.add(moduleDependency);
        }
        else if (versionId.getIdeDependency() instanceof IdeDependenciesExtractor.IdeLocalFileDependency) {
          IdeDependenciesExtractor.IdeLocalFileDependency fileDependency =
            (IdeDependenciesExtractor.IdeLocalFileDependency)versionId.getIdeDependency();
          IdeaSingleEntryLibraryDependencyImpl libraryDependency = new IdeaSingleEntryLibraryDependencyImpl(
            new IdeaDependencyScopeImpl(scope),
            versionId.getName(),
            versionId.getGroup(),
            versionId.getVersion()
          );
          libraryDependency.setFile(fileDependency.getFile());
          dependencies.add(libraryDependency);
        }
      }
    }

    return new ProjectDependenciesModelImpl(project.getPath(), dependencies);
  }

  private static void merge(Map<DependencyVersionId, Scopes> map, IdeDependenciesExtractor.IdeProjectDependency dependency) {
    final String configurationName = dependency.getDeclaredConfiguration().getName();
    final GradleDependencyScope scope = GradleDependencyScope.fromName(configurationName);
    if (scope == null) return;

    final Project project = dependency.getProject();
    final String version = project.hasProperty("version") ? str(project.property("version")) : "";
    final String group = project.hasProperty("group") ? str(project.property("group")) : "";

    DependencyVersionId versionId =
      new DependencyVersionId(dependency, project.getName(), group, version);
    Scopes scopes = map.get(versionId);
    if (scopes == null) {
      map.put(versionId, new Scopes(scope));
    }
    else {
      scopes.add(scope);
    }
  }

  private static String str(Object o) {
    return String.valueOf(o == null ? "" : o);
  }

  private static void merge(Map<DependencyVersionId, Scopes> map, IdeDependenciesExtractor.IdeRepoFileDependency dependency) {
    final String configurationName = dependency.getDeclaredConfiguration().getName();
    final GradleDependencyScope scope = GradleDependencyScope.fromName(configurationName);
    if (scope == null) return;

    ModuleVersionIdentifier dependencyId = dependency.getId();
    DependencyVersionId versionId =
      new DependencyVersionId(dependency, dependencyId.getName(), dependencyId.getGroup(), dependencyId.getVersion());
    Scopes scopes = map.get(versionId);
    if (scopes == null) {
      map.put(versionId, new Scopes(scope));
    }
    else {
      scopes.add(scope);
    }
  }

  private static void merge(Map<DependencyVersionId, Scopes> map, IdeDependenciesExtractor.IdeLocalFileDependency dependency) {

    final String configurationName = dependency.getDeclaredConfiguration().getName();
    final GradleDependencyScope scope = GradleDependencyScope.fromName(configurationName);
    if (scope == null) return;

    String path = dependency.getFile().getPath();
    DependencyVersionId versionId =
      new DependencyVersionId(dependency, path, "", "");
    Scopes scopes = map.get(versionId);
    if (scopes == null) {
      map.put(versionId, new Scopes(scope));
    }
    else {
      scopes.add(scope);
    }
  }
}
