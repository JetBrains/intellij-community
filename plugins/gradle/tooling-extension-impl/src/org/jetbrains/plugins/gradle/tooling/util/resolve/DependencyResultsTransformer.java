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
import org.gradle.internal.impldep.com.google.common.collect.Lists;
import org.gradle.internal.impldep.com.google.common.collect.Multimap;
import org.gradle.internal.impldep.com.google.common.io.Files;
import org.gradle.language.base.artifact.SourcesArtifact;
import org.gradle.language.java.artifact.JavadocArtifact;
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
  Collection<DependencyResult> handledDependencyResults;
  Multimap<ModuleVersionIdentifier, ResolvedArtifact> artifactMap;
  Map<ComponentIdentifier, ComponentArtifactsResult> componentResultsMap;
  Multimap<ModuleComponentIdentifier, ProjectDependency> configurationProjectDependencies;
  String scope;
  Set<File> resolvedDepsFiles = new HashSet<File>();

  DependencyResultsTransformer(Project project,
                               SourceSetCachedFinder sourceSetFinder,
                               Multimap<ModuleVersionIdentifier, ResolvedArtifact> artifactMap,
                               Map<ComponentIdentifier, ComponentArtifactsResult> componentResultsMap,
                               Multimap<ModuleComponentIdentifier, ProjectDependency> configurationProjectDependencies,
                               String scope) {
    myProject = project;
    mySourceSetFinder = sourceSetFinder;
    this.handledDependencyResults = Lists.newArrayList();
    this.artifactMap = artifactMap;
    this.componentResultsMap = componentResultsMap;
    this.configurationProjectDependencies = configurationProjectDependencies;
    this.scope = scope;
  }

  Set<ExternalDependency> transform(Collection<? extends DependencyResult> dependencyResults) {

    Set<ExternalDependency> dependencies = new LinkedHashSet<ExternalDependency>();
    for (DependencyResult dependencyResult : dependencyResults) {

      // dependency cycles check
      if (!handledDependencyResults.contains(dependencyResult)) {
        handledDependencyResults.add(dependencyResult);

        if (dependencyResult instanceof ResolvedDependencyResult) {
          ResolvedComponentResult componentResult = ((ResolvedDependencyResult)dependencyResult).getSelected();
          ComponentSelector componentSelector = dependencyResult.getRequested();
          ModuleComponentIdentifier componentIdentifier = DependencyResolverImpl.toComponentIdentifier(componentResult.getModuleVersion());

          String name = componentResult.getModuleVersion().getName();
          String group = componentResult.getModuleVersion().getGroup();
          String version = componentResult.getModuleVersion().getVersion();
          String selectionReason = componentResult.getSelectionReason().getDescription();

          boolean resolveFromArtifacts = componentSelector instanceof ModuleComponentSelector;

          if (componentSelector instanceof ProjectComponentSelector) {
            Collection<ProjectDependency> projectDependencies = configurationProjectDependencies.get(componentIdentifier);
            Collection<Configuration> dependencyConfigurations;
            if (projectDependencies.isEmpty()) {
              Project dependencyProject = myProject.findProject(((ProjectComponentSelector)componentSelector).getProjectPath());
              if (dependencyProject != null) {
                Configuration dependencyProjectConfiguration =
                  dependencyProject.getConfigurations().getByName(Dependency.DEFAULT_CONFIGURATION);
                dependencyConfigurations = Collections.singleton(dependencyProjectConfiguration);
              } else {
                dependencyConfigurations = Collections.emptySet();
                resolveFromArtifacts = true;
                selectionReason = "composite build substitution";
              }
            } else {
              dependencyConfigurations = new ArrayList<Configuration>();
              for (ProjectDependency dependency : projectDependencies) {
                dependencyConfigurations.add(DependencyResolverImpl.getTargetConfiguration(dependency));
              }
            }

            for (Configuration it : dependencyConfigurations) {
              if (it.getName().equals(Dependency.DEFAULT_CONFIGURATION)) {
                DefaultExternalProjectDependency dependency = new DefaultExternalProjectDependency();
                dependency.setName( name);
                dependency.setGroup(group);
                dependency.setVersion(version);
                dependency.setScope(scope);
                dependency.setSelectionReason(selectionReason);
                dependency.setProjectPath(((ProjectComponentSelector)componentSelector).getProjectPath());
                dependency.setConfigurationName(it.getName());
                Set<File> artifacts = it.getAllArtifacts().getFiles().getFiles();
                dependency.setProjectDependencyArtifacts(artifacts);
                DependencyResolverImpl.setProjectDependencyArtifactsSources(dependency, artifacts, mySourceSetFinder);

                resolvedDepsFiles.addAll(dependency.getProjectDependencyArtifacts());

                if (it.getArtifacts().size() == 1) {
                  PublishArtifact publishArtifact = it.getAllArtifacts().iterator().next();
                  dependency.setClassifier(publishArtifact.getClassifier());
                  dependency.setPackaging(publishArtifact.getExtension() != null ? publishArtifact.getExtension() : "jar");
                }

                if (!componentResult.equals(dependencyResult.getFrom())) {
                  dependency.getDependencies().addAll(
                    transform(componentResult.getDependencies())
                  );
                }
                dependencies.add(dependency);
              }
              else {
                DefaultExternalProjectDependency dependency = new DefaultExternalProjectDependency();
                dependency.setName(name);
                dependency.setGroup(group);
                dependency.setVersion(version);
                dependency.setScope(scope);
                dependency.setSelectionReason(selectionReason);
                dependency.setProjectPath(((ProjectComponentSelector)componentSelector).getProjectPath());
                dependency.setConfigurationName(it.getName());
                Set<File> artifactsFiles = it.getAllArtifacts().getFiles().getFiles();
                dependency.setProjectDependencyArtifacts(artifactsFiles);
                DependencyResolverImpl.setProjectDependencyArtifactsSources(dependency, artifactsFiles, mySourceSetFinder);

                resolvedDepsFiles.addAll(dependency.getProjectDependencyArtifacts());

                if (it.getArtifacts().size() == 1) {
                  PublishArtifact publishArtifact = it.getAllArtifacts().iterator().next();
                  dependency.setClassifier(publishArtifact.getClassifier());
                  dependency.setPackaging(publishArtifact.getExtension() != null ? publishArtifact.getExtension() : "jar");
                }

                if (!componentResult.equals(dependencyResult.getFrom())) {

                  dependency.getDependencies().addAll(
                    transform(componentResult.getDependencies())
                  );
                }

                dependencies.add(dependency);

                List<File> files = new ArrayList<File>();
                PublishArtifactSet artifacts = it.getArtifacts();
                if (artifacts != null && !artifacts.isEmpty()) {
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
                }

                if (!files.isEmpty()) {
                  final DefaultFileCollectionDependency fileCollectionDependency = new DefaultFileCollectionDependency(files);
                  fileCollectionDependency.setScope(scope);
                  dependencies.add(fileCollectionDependency);
                  resolvedDepsFiles.addAll(files);
                }
              }
            }
          }

          if (resolveFromArtifacts) {
            Collection<ResolvedArtifact> artifacts = artifactMap.get(componentResult.getModuleVersion());

            if (artifacts != null && artifacts.isEmpty()) {
              dependencies.addAll(
                transform(componentResult.getDependencies())
              );
            }

            boolean first = true;

            if (artifacts != null) {
              for (ResolvedArtifact artifact: artifacts) {
                String packaging = artifact.getExtension() != null ? artifact.getExtension() : "jar";
                String classifier = artifact.getClassifier();
                final ExternalDependency dependency;
                if (DependencyResolverImpl.isProjectDependencyArtifact(artifact)) {
                  ProjectComponentIdentifier artifactComponentIdentifier =
                    (ProjectComponentIdentifier)artifact.getId().getComponentIdentifier();

                  dependency = new DefaultExternalProjectDependency();
                  DefaultExternalProjectDependency dDep = (DefaultExternalProjectDependency)dependency;
                  dDep.setName(name);
                  dDep.setGroup(group);
                  dDep.setVersion(version);
                  dDep.setScope(scope);
                  dDep.setSelectionReason(selectionReason);
                  dDep.setProjectPath(artifactComponentIdentifier.getProjectPath());
                  dDep.setConfigurationName(Dependency.DEFAULT_CONFIGURATION);

                  List<File> files = new ArrayList<File>();
                  for (ResolvedArtifact resolvedArtifact : artifactMap.get(componentResult.getModuleVersion())) {
                    files.add(resolvedArtifact.getFile());
                  }
                  dDep.setProjectDependencyArtifacts(files);
                  DependencyResolverImpl.setProjectDependencyArtifactsSources(dDep, files, mySourceSetFinder);
                  resolvedDepsFiles.addAll(dDep.getProjectDependencyArtifacts());
                }
                else {
                  dependency = new DefaultExternalLibraryDependency();
                  DefaultExternalLibraryDependency dDep = (DefaultExternalLibraryDependency)dependency;
                  dDep.setName(name);
                  dDep.setGroup(group);
                  dDep.setPackaging(packaging);
                  dDep.setClassifier(classifier);
                  dDep.setVersion(version);
                  dDep.setScope(scope);
                  dDep.setSelectionReason(selectionReason);
                  dDep.setFile(artifact.getFile());

                  ComponentArtifactsResult artifactsResult = componentResultsMap.get(componentIdentifier);
                  if (artifactsResult != null) {
                    ResolvedArtifactResult
                      sourcesResult = findMatchingArtifact(artifact, artifactsResult, SourcesArtifact.class);
                    if (sourcesResult != null) {
                      ((DefaultExternalLibraryDependency)dependency).setSource(sourcesResult.getFile());
                    }

                    ResolvedArtifactResult javadocResult = findMatchingArtifact(artifact, artifactsResult, JavadocArtifact.class);
                    if (javadocResult != null) {
                      ((DefaultExternalLibraryDependency)dependency).setJavadoc(javadocResult.getFile());
                    }
                  }
                }

                if (first) {
                  dependency.getDependencies().addAll(
                    transform(componentResult.getDependencies())
                  );
                  first = false;
                }

                dependencies.add(dependency);
                resolvedDepsFiles.add(artifact.getFile());
              }
            }
          }
        }

        if (dependencyResult instanceof UnresolvedDependencyResult) {
          ComponentSelector attempted = ((UnresolvedDependencyResult)dependencyResult).getAttempted();
          if (attempted instanceof ModuleComponentSelector) {
            final ModuleComponentSelector attemptedMCSelector = (ModuleComponentSelector)attempted;
            final DefaultUnresolvedExternalDependency dependency = new DefaultUnresolvedExternalDependency();
            dependency.setName(attemptedMCSelector.getModule());
            dependency.setGroup(attemptedMCSelector.getGroup());
            dependency.setVersion(attemptedMCSelector.getVersion());
            dependency.setScope(scope);
            dependency.setFailureMessage(((UnresolvedDependencyResult)dependencyResult).getFailure().getMessage());

            dependencies.add(dependency);
          }
        }
      }
    }

    return dependencies;
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
