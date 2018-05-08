// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.util.resolve;

import groovy.lang.MetaMethod;
import groovy.lang.MetaProperty;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.gradle.api.Project;
import org.gradle.api.artifacts.*;
import org.gradle.api.artifacts.component.*;
import org.gradle.api.artifacts.result.*;
import org.gradle.api.component.Artifact;
import org.gradle.api.tasks.AbstractCopyTask;
import org.gradle.api.tasks.SourceSetOutput;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.internal.impldep.com.google.common.collect.Multimap;
import org.gradle.internal.impldep.com.google.common.io.Files;
import org.gradle.language.base.artifact.SourcesArtifact;
import org.gradle.language.java.artifact.JavadocArtifact;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.*;
import org.jetbrains.plugins.gradle.model.ExternalDependency;
import org.jetbrains.plugins.gradle.tooling.util.SourceSetCachedFinder;

import java.io.File;
import java.lang.reflect.Method;
import java.util.*;

class DependencyResultsTransformer {
  private final Project myProject;
  private final SourceSetCachedFinder mySourceSetFinder;
  private final Multimap<ModuleVersionIdentifier, ResolvedArtifact> myArtifactMap;
  private final Map<ComponentIdentifier, ComponentArtifactsResult> myAuxiliaryArtifactsMap;
  private final Multimap<ModuleComponentIdentifier, ProjectDependency> myConfigurationProjectDependencies;
  private final String myScope;
  private final Set<File> resolvedDepsFiles = new HashSet<File>();

  DependencyResultsTransformer(@NotNull final Project project,
                               @NotNull final SourceSetCachedFinder sourceSetFinder,
                               @NotNull final Multimap<ModuleVersionIdentifier, ResolvedArtifact> artifactMap,
                               @NotNull final Map<ComponentIdentifier, ComponentArtifactsResult> componentResultsMap,
                               @NotNull final Multimap<ModuleComponentIdentifier, ProjectDependency> configurationProjectDependencies,
                               @Nullable final String scope) {
    myProject = project;
    mySourceSetFinder = sourceSetFinder;

    myArtifactMap = artifactMap;
    myAuxiliaryArtifactsMap = componentResultsMap;
    myConfigurationProjectDependencies = configurationProjectDependencies;
    myScope = scope;
  }

  public Set<File> getResolvedDepsFiles() {
    return resolvedDepsFiles;
  }

  Set<ExternalDependency> buildExternalDependencies(Collection<? extends DependencyResult> gradleDependencies) {
    Set<DependencyResult> handledDependencyResults = new HashSet<DependencyResult>();
    Set<ExternalDependency> result = new LinkedHashSet<ExternalDependency>();

    for (DependencyResult gradleDep : gradleDependencies) {
      // dependency cycles check
      if (handledDependencyResults.add(gradleDep)) {
        if (gradleDep instanceof ResolvedDependencyResult) {
          result.addAll(buildExternalDependencies((ResolvedDependencyResult)gradleDep));
        }

        if (gradleDep instanceof UnresolvedDependencyResult) {
          result.addAll(buildExternalDependencies((UnresolvedDependencyResult)gradleDep));
        }
      }
    }

    return result;
  }

  private Set<ExternalDependency> buildExternalDependencies(UnresolvedDependencyResult unresolvedGradleDep) {
    ComponentSelector attempted = unresolvedGradleDep.getAttempted();
    if (attempted instanceof ModuleComponentSelector) {
      final ModuleComponentSelector attemptedMCSelector = (ModuleComponentSelector)attempted;
      return Collections.<ExternalDependency>singleton(createUnresolvedExternalDep(attemptedMCSelector,
                                                                                   unresolvedGradleDep.getFailure().getMessage()));
    }
    return Collections.emptySet();
  }


  private Set<ExternalDependency> buildExternalDependencies(ResolvedDependencyResult resolvedGradleDep) {
    Set<ExternalDependency> result = new LinkedHashSet<ExternalDependency>();

    ResolvedComponentResult componentResult = resolvedGradleDep.getSelected();
    ComponentSelector componentSelector = resolvedGradleDep.getRequested();
    ModuleVersionIdentifier moduleVersionId = componentResult.getModuleVersion();
    ModuleComponentIdentifier componentIdentifier = DependencyResolverImpl.toComponentIdentifier(moduleVersionId);
    String selectionReason = componentResult.getSelectionReason().getDescription();

    boolean resolveFromArtifacts = false;

    if (componentSelector instanceof ProjectComponentSelector) {
      final ProjectComponentSelector selector = (ProjectComponentSelector)componentSelector;

      final Collection<Configuration> dependencyConfigurations =
        getProjectDepsConfigurations(selector.getProjectPath(),
                                     myConfigurationProjectDependencies.get(componentIdentifier));

      if (dependencyConfigurations.isEmpty()) {
        resolveFromArtifacts = true;
        selectionReason = "composite build substitution";
      }

      for (Configuration it : dependencyConfigurations) {
        DefaultExternalProjectDependency dependency = createExternalProjectDep(moduleVersionId,
                                                                               selectionReason,
                                                                               selector.getProjectPath(),
                                                                               it.getName());
        processProjectDependencyConfiguration(resolvedGradleDep, result, componentResult, it, dependency);
      }
    }

    if (componentSelector instanceof ModuleComponentSelector || resolveFromArtifacts) {
      Collection<ResolvedArtifact> artifacts = myArtifactMap.get(moduleVersionId);

      final Set<ExternalDependency> transitiveDeps = buildExternalDependencies(componentResult.getDependencies());
      if (artifacts != null && artifacts.isEmpty()) {
        result.addAll(transitiveDeps);
      }

      boolean first = true;

      if (artifacts != null) {
        for (ResolvedArtifact artifact: artifacts) {
          final ExternalDependency dependency = transformResolvedArtifact(moduleVersionId, componentIdentifier, selectionReason, artifact);

          if (first) {
            dependency.getDependencies().addAll(transitiveDeps);
            first = false;
          }
          result.add(dependency);
          resolvedDepsFiles.add(artifact.getFile());
        }
      }
    }

    return result;
  }

  @NotNull
  private Collection<Configuration> getProjectDepsConfigurations(@NotNull final String projectPath,
                                                                 @NotNull final Collection<ProjectDependency> projectDependencies) {
    Collection<Configuration> dependencyConfigurations;
    if (projectDependencies.isEmpty()) {
      Project dependencyProject = myProject.findProject(projectPath);
      if (dependencyProject != null) {
        Configuration dependencyProjectConfiguration =
          dependencyProject.getConfigurations().getByName(Dependency.DEFAULT_CONFIGURATION);
        dependencyConfigurations = Collections.singleton(dependencyProjectConfiguration);
      } else {
        dependencyConfigurations = Collections.emptySet();
      }
    } else {
      dependencyConfigurations = new ArrayList<Configuration>();
      for (ProjectDependency dependency : projectDependencies) {
        dependencyConfigurations.add(DependencyResolverImpl.getTargetConfiguration(dependency));
      }
    }
    return dependencyConfigurations;
  }

  @NotNull
  private ExternalDependency transformResolvedArtifact(@NotNull final ModuleVersionIdentifier moduleVersionId,
                                                       @NotNull final ModuleComponentIdentifier componentIdentifier,
                                                       @NotNull final String selectionReason,
                                                       @NotNull final ResolvedArtifact artifact) {
    String packaging = artifact.getExtension() != null ? artifact.getExtension() : "jar";
    String classifier = artifact.getClassifier();
    final ExternalDependency dependency;

    if (DependencyResolverImpl.isProjectDependencyArtifact(artifact)) {

      final String projectPath = ((ProjectComponentIdentifier)artifact.getId().getComponentIdentifier()).getProjectPath();
      dependency = createExternalProjectDep(moduleVersionId,
                                            selectionReason,
                                            projectPath,
                                            Dependency.DEFAULT_CONFIGURATION);

      DefaultExternalProjectDependency projectDependency = (DefaultExternalProjectDependency)dependency;

      List<File> files = new ArrayList<File>();
      for (ResolvedArtifact resolvedArtifact : myArtifactMap.get(moduleVersionId)) {
        files.add(resolvedArtifact.getFile());
      }

      projectDependency.setProjectDependencyArtifacts(files);
      projectDependency.setProjectDependencyArtifactsSources(DependencyResolverImpl.findArtifactSources(files, mySourceSetFinder));
      resolvedDepsFiles.addAll(projectDependency.getProjectDependencyArtifacts());
    }
    else {
      dependency = createExternalLibraryDep(moduleVersionId, selectionReason);
      DefaultExternalLibraryDependency libraryDependency = (DefaultExternalLibraryDependency)dependency;

      libraryDependency.setPackaging(packaging);
      libraryDependency.setClassifier(classifier);
      libraryDependency.setFile(artifact.getFile());

      ComponentArtifactsResult artifactsResult = myAuxiliaryArtifactsMap.get(componentIdentifier);
      if (artifactsResult != null) {
        ResolvedArtifactResult sourcesResult = findMatchingArtifact(artifact, artifactsResult, SourcesArtifact.class);
        if (sourcesResult != null) {
          libraryDependency.setSource(sourcesResult.getFile());
        }

        ResolvedArtifactResult javadocResult = findMatchingArtifact(artifact, artifactsResult, JavadocArtifact.class);
        if (javadocResult != null) {
          libraryDependency.setJavadoc(javadocResult.getFile());
        }
      }
    }
    return dependency;
  }

  private void processProjectDependencyConfiguration(@NotNull final ResolvedDependencyResult resolvedGradleDep,
                                                     @NotNull final Set<ExternalDependency> result,
                                                     @NotNull final ResolvedComponentResult resolvedComponent,
                                                     @NotNull final Configuration projectDepConfiguration,
                                                     @NotNull final DefaultExternalProjectDependency externalDependency) {
    Set<File> artifactsFiles = projectDepConfiguration.getAllArtifacts().getFiles().getFiles();
    externalDependency.setProjectDependencyArtifacts(artifactsFiles);
    externalDependency.setProjectDependencyArtifactsSources(DependencyResolverImpl.findArtifactSources(artifactsFiles, mySourceSetFinder));
    resolvedDepsFiles.addAll(externalDependency.getProjectDependencyArtifacts());

    if (projectDepConfiguration.getArtifacts().size() == 1) {
      PublishArtifact publishArtifact = projectDepConfiguration.getAllArtifacts().iterator().next();
      externalDependency.setClassifier(publishArtifact.getClassifier());
      externalDependency.setPackaging(publishArtifact.getExtension() != null ? publishArtifact.getExtension() : "jar");
    }

    if (!resolvedComponent.equals(resolvedGradleDep.getFrom())) {
      externalDependency.getDependencies().addAll(buildExternalDependencies(resolvedComponent.getDependencies()));
    }
    result.add(externalDependency);

    if (!projectDepConfiguration.getName().equals(Dependency.DEFAULT_CONFIGURATION)) {
      List<File> configurationSources = new ArrayList<File>();
      PublishArtifactSet artifacts = projectDepConfiguration.getArtifacts();

      if (artifacts != null && !artifacts.isEmpty()) {
        configurationSources.addAll(collectSourcesOfPublishedArtifacts(artifacts));
      }

      if (!configurationSources.isEmpty()) {
        final DefaultFileCollectionDependency fileCollectionDependency = new DefaultFileCollectionDependency(configurationSources);
        fileCollectionDependency.setScope(myScope);
        result.add(fileCollectionDependency);
        resolvedDepsFiles.addAll(configurationSources);
      }
    }
  }

  @NotNull
  private List<File> collectSourcesOfPublishedArtifacts(@NotNull final PublishArtifactSet artifacts) {
    final List<File> files = new ArrayList<File>();

    PublishArtifact artifact = artifacts.iterator().next();
    final MetaProperty taskProperty = DefaultGroovyMethods.hasProperty(artifact, "archiveTask");
    if (taskProperty != null && (taskProperty.getProperty(artifact) instanceof AbstractArchiveTask)) {

      AbstractArchiveTask archiveTask = (AbstractArchiveTask)taskProperty.getProperty(artifact);
      resolvedDepsFiles.add(new File(archiveTask.getDestinationDir(), archiveTask.getArchiveName()));

      try {
        final Method mainSpecGetter = AbstractCopyTask.class.getDeclaredMethod("getMainSpec");
        mainSpecGetter.setAccessible(true);
        Object mainSpec = mainSpecGetter.invoke(archiveTask);

        final List<MetaMethod> sourcePathGetters =
          DefaultGroovyMethods.respondsTo(mainSpec, "getSourcePaths", new Object[]{});
        if (!sourcePathGetters.isEmpty()) {
          Set<Object> sourcePaths = (Set<Object>)sourcePathGetters.get(0).doMethodInvoke(mainSpec, new Object[]{});
          if (sourcePaths != null) {
            for (Object path : sourcePaths) {
              if (path instanceof String) {
                File file = new File((String)path);
                if (file.isAbsolute()) {
                  files.add(file);
                }
              }
              else if (path instanceof SourceSetOutput) {
                files.addAll(((SourceSetOutput)path).getFiles());
              }
            }
          }
        }
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    return files;
  }


  private ExternalLibraryDependency createExternalLibraryDep(@NotNull final ModuleVersionIdentifier moduleVersionId,
                                                             @NotNull final String selectionReason) {
    DefaultExternalLibraryDependency dDep = new DefaultExternalLibraryDependency();
    dDep.setName(moduleVersionId.getName());
    dDep.setGroup(moduleVersionId.getGroup());
    dDep.setVersion(moduleVersionId.getVersion());
    dDep.setSelectionReason(selectionReason);
    dDep.setScope(myScope);
    return dDep;
  }

  @NotNull
  private DefaultExternalProjectDependency createExternalProjectDep(@NotNull final ModuleVersionIdentifier moduleVersionId,
                                                                    @NotNull final String selectionReason,
                                                                    @NotNull final String projectPath,
                                                                    @NotNull final String configurationName) {
    DefaultExternalProjectDependency dependency = new DefaultExternalProjectDependency();
    dependency.setName(moduleVersionId.getName());
    dependency.setGroup(moduleVersionId.getGroup());
    dependency.setVersion(moduleVersionId.getVersion());
    dependency.setScope(myScope);
    dependency.setSelectionReason(selectionReason);
    dependency.setProjectPath(projectPath);
    dependency.setConfigurationName(configurationName);
    return dependency;
  }

  @NotNull
  private DefaultUnresolvedExternalDependency createUnresolvedExternalDep(@NotNull final ModuleComponentSelector attemptedMCSelector,
                                                                          @NotNull final String message) {
    final DefaultUnresolvedExternalDependency dependency = new DefaultUnresolvedExternalDependency();
    dependency.setName(attemptedMCSelector.getModule());
    dependency.setGroup(attemptedMCSelector.getGroup());
    dependency.setVersion(attemptedMCSelector.getVersion());
    dependency.setScope(myScope);
    dependency.setFailureMessage(message);
    return dependency;
  }

  @Nullable
  private static ResolvedArtifactResult findMatchingArtifact(ResolvedArtifact artifact,
                                                             ComponentArtifactsResult componentArtifacts,
                                                             Class<? extends Artifact> artifactType) {
    String baseName = Files.getNameWithoutExtension(artifact.getFile().getName());
    Set<ArtifactResult> artifactResults = componentArtifacts.getArtifacts(artifactType);

    if (artifactResults.size() == 1) {
      ArtifactResult artifactResult = artifactResults.iterator().next();
      return artifactResult instanceof ResolvedArtifactResult ? (ResolvedArtifactResult)artifactResult : null;
    }

    for (ArtifactResult result : artifactResults) {
      if (result instanceof ResolvedArtifactResult && ((ResolvedArtifactResult)result).getFile().getName().startsWith(baseName)) {
        return (ResolvedArtifactResult)result;
      }
    }
    return null;
  }
}
