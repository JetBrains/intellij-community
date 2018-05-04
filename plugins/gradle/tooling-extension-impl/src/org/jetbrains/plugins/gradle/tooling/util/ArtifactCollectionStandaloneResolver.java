// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.util;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.ArtifactView;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.SourceSet;
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactIdentifier;
import org.gradle.util.CollectionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.ExternalDependencyId;
import org.jetbrains.plugins.gradle.model.DefaultExternalLibraryDependency;
import org.jetbrains.plugins.gradle.model.DefaultExternalProjectDependency;
import org.jetbrains.plugins.gradle.model.ExternalDependency;

import java.util.*;

public class ArtifactCollectionStandaloneResolver implements DependencyResolver {
  private final Project myProject;

  public ArtifactCollectionStandaloneResolver(@NotNull final Project project,
                                              boolean isPreview, boolean downloadJavadoc, boolean downloadSources,
                                              @NotNull SourceSetCachedFinder finder) {
    myProject = project;
  }

  @Override
  public Collection<ExternalDependency> resolveDependencies(@Nullable String configurationName) {
    return resolveDependencies(configurationName, null);
  }

  @Override
  public Collection<ExternalDependency> resolveDependencies(@NotNull SourceSet sourceSet) {
    final Collection<ExternalDependency> compileDeps = resolveDependencies(sourceSet.getCompileConfigurationName(), "COMPILE");
    final Collection<ExternalDependency> runtimeDeps = resolveDependencies(sourceSet.getRuntimeConfigurationName(), "RUNTIME");

    Collection<ExternalDependency> runtimeOnly = removeCompileDepsFromRuntimeDeps(runtimeDeps, compileDeps);

    compileDeps.addAll(runtimeOnly);
    return compileDeps;
  }

  private static Collection<ExternalDependency> removeCompileDepsFromRuntimeDeps(@NotNull Collection<ExternalDependency> runtimeDeps,
                                                                                 @NotNull Collection<ExternalDependency> compileDeps) {
    final Map<ExternalDependencyId, ExternalDependency> runtimeDepsById =
      CollectionUtils.collectMap(runtimeDeps, new Transformer<ExternalDependencyId, ExternalDependency>() {
        @NotNull
        @Override
        public ExternalDependencyId transform(@NotNull ExternalDependency dependency) {
          return dependency.getId();
        }
      });

    for (ExternalDependency compileDep : compileDeps) {
      runtimeDepsById.remove(compileDep.getId());
    }

    return runtimeDepsById.values();
  }

  private Collection<ExternalDependency> resolveDependencies(@Nullable String configurationName, @Nullable String scopeName) {
    if (configurationName == null) {
      return Collections.emptySet();
    }
    final Configuration configuration = myProject.getConfigurations().getByName(configurationName);
    return resolveDependencies(configuration, scopeName);
  }

  @Override
  public Collection<ExternalDependency> resolveDependencies(@Nullable Configuration configuration) {
    return resolveDependencies(configuration, null);
  }

  @NotNull
  private Collection<ExternalDependency> resolveDependencies(@Nullable Configuration configuration, @Nullable String scopeName) {
    if (configuration == null) {
      return Collections.emptySet();
    }

    final Set<ExternalDependency> result = new LinkedHashSet<ExternalDependency>();

    final ArtifactCollection artifactCollection = configuration.getIncoming().artifactView(new Action<ArtifactView.ViewConfiguration>() {
      @Override
      public void execute(@NotNull ArtifactView.ViewConfiguration viewConfiguration) {
        viewConfiguration.lenient(true);
        viewConfiguration.componentFilter(Specs.SATISFIES_ALL);
      }
    }).getArtifacts();

    for (ResolvedArtifactResult artifact : artifactCollection.getArtifacts()) {
      final ComponentIdentifier identifier = artifact.getId().getComponentIdentifier();
      if (identifier instanceof ModuleComponentIdentifier) {
        result.add(createModuleDependency(artifact, (ModuleComponentIdentifier)identifier, scopeName));
      }
      if (identifier instanceof ProjectComponentIdentifier) {
        result.add(createProjectDependency(artifact, (ProjectComponentIdentifier)identifier, scopeName));
      }
    }

    return result;
  }


  private ExternalDependency createProjectDependency(@NotNull final ResolvedArtifactResult artifactResult,
                                                     @NotNull final ProjectComponentIdentifier identifier,
                                                     @Nullable String scopeName) {
    final DefaultExternalProjectDependency dependency = new DefaultExternalProjectDependency();

    dependency.setName(identifier.getProjectName());

    //dependency.setGroup(identifier.getGroup());
    //dependency.setVersion(identifier.getVersion());

    dependency.setProjectPath(identifier.getProjectPath());
    if (scopeName != null) {
      dependency.setScope(scopeName);
    }

    dependency.setProjectDependencyArtifacts(Collections.singleton(artifactResult.getFile()));
    //dependency.setProjectDependencyArtifactsSources();
    return dependency;
  }

  private ExternalDependency createModuleDependency(@NotNull final ResolvedArtifactResult artifactResult,
                                                    @NotNull final ModuleComponentIdentifier identifier,
                                                    @Nullable String scopeName) {
    final DefaultExternalLibraryDependency dependency = new DefaultExternalLibraryDependency();

    dependency.setName(identifier.getModule());
    dependency.setGroup(identifier.getGroup());
    dependency.setVersion(identifier.getVersion());

    final ComponentArtifactIdentifier artifactId = artifactResult.getId();
    if (artifactId instanceof DefaultModuleComponentArtifactIdentifier) {
      final DefaultModuleComponentArtifactIdentifier castId = (DefaultModuleComponentArtifactIdentifier)artifactId;
      dependency.setPackaging(castId.getName().getExtension());
      dependency.setClassifier(castId.getName().getClassifier());
    }

    dependency.setFile(artifactResult.getFile());

    if (scopeName != null) {
      dependency.setScope(scopeName);
    }

    return dependency;
  }
}
