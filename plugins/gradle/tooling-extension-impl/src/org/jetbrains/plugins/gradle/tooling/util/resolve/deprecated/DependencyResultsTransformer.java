// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.util.resolve.deprecated;

import com.intellij.gradle.toolingExtension.impl.model.sourceSetModel.GradleSourceSetCachedFinder;
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil;
import groovy.lang.MetaMethod;
import groovy.lang.MetaProperty;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.gradle.api.Project;
import org.gradle.api.artifacts.*;
import org.gradle.api.artifacts.component.*;
import org.gradle.api.artifacts.result.*;
import org.gradle.api.attributes.AttributeContainer;
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
import org.jetbrains.plugins.gradle.model.ExternalDependency;
import org.jetbrains.plugins.gradle.model.*;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;

import java.io.File;
import java.lang.reflect.Method;
import java.util.*;

import static com.intellij.gradle.toolingExtension.util.GradleReflectionUtil.reflectiveCall;
import static org.jetbrains.plugins.gradle.tooling.util.resolve.DependencyResolverImpl.toComponentIdentifier;
import static org.jetbrains.plugins.gradle.tooling.util.resolve.deprecated.DeprecatedDependencyResolver.*;

/**
 * @deprecated use org.jetbrains.plugins.gradle.tooling.util.resolve.DependencyResolverImpl
 */
@Deprecated
public class DependencyResultsTransformer {

  private static final boolean is31orBetter = GradleVersionUtil.isCurrentGradleAtLeast("3.1");
  private static final boolean is46rBetter = is31orBetter && GradleVersionUtil.isCurrentGradleAtLeast("4.6");


  private final @NotNull ModelBuilderContext myContext;
  private final @NotNull Project myProject;

  private final Multimap<ModuleVersionIdentifier, ResolvedArtifact> artifactMap;
  private final Map<ComponentIdentifier, ComponentArtifactsResult> componentResultsMap;
  private final Multimap<ModuleComponentIdentifier, ProjectDependency> configurationProjectDependencies;
  private final String scope;
  private final Set<File> resolvedDepsFiles = new HashSet<>();

  private final Set<DependencyResult> handledDependencyResults = new HashSet<>();
  private final Set<ComponentResultKey> myVisitedComponentResults = new HashSet<>();

  public DependencyResultsTransformer(
    @NotNull ModelBuilderContext context,
    @NotNull Project project,
    @NotNull Multimap<ModuleVersionIdentifier, ResolvedArtifact> artifactMap,
    @NotNull Map<ComponentIdentifier, ComponentArtifactsResult> auxiliaryArtifactsMap,
    @NotNull Multimap<ModuleComponentIdentifier, ProjectDependency> configurationProjectDependencies,
    @Nullable String scope
  ) {
    myContext = context;
    myProject = project;

    this.artifactMap = artifactMap;
    componentResultsMap = auxiliaryArtifactsMap;
    this.configurationProjectDependencies = configurationProjectDependencies;
    this.scope = scope;
  }

  public Set<File> getResolvedDepsFiles() {
    return resolvedDepsFiles;
  }

  Set<ExternalDependency> buildExternalDependencies(Collection<? extends DependencyResult> gradleDependencies) {

    Set<ExternalDependency> dependencies = new LinkedHashSet<>();
    for (DependencyResult dependencyResult : gradleDependencies) {

      // dependency cycles check
      if (handledDependencyResults.add(dependencyResult)) {
        if (dependencyResult instanceof ResolvedDependencyResult) {
          dependencies.addAll(processResolvedResult((ResolvedDependencyResult)dependencyResult));
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

  private Set<ExternalDependency> processResolvedResult(ResolvedDependencyResult dependencyResult) {
    Set<ExternalDependency> result = new LinkedHashSet<>();

    final ResolvedComponentResult componentResult = dependencyResult.getSelected();

    if (!myVisitedComponentResults.add(getKey(componentResult))) {
      return Collections.emptySet();
    }

    final ComponentIdentifier resultId = componentResult.getId();
    ModuleComponentIdentifier componentIdentifier = resultId instanceof ModuleComponentIdentifier
                                                    ? (ModuleComponentIdentifier)resultId
                                                    : toComponentIdentifier(componentResult.getModuleVersion());

    String name = componentResult.getModuleVersion().getName();
    String group = componentResult.getModuleVersion().getGroup();
    String version = componentResult.getModuleVersion().getVersion();
    ComponentSelectionReason reason = componentResult.getSelectionReason();

    Object description = invokeMethod(reason, "getDescription");
    String selectionReason = description == null ? "" : description.toString();

    boolean resolveFromArtifacts = resultId instanceof ModuleComponentIdentifier;

    if (resultId instanceof ProjectComponentIdentifier) {
      ProjectComponentIdentifier projectComponentIdentifier = (ProjectComponentIdentifier)resultId;
      Collection<ProjectDependency> projectDependencies = configurationProjectDependencies.get(componentIdentifier);
      Collection<Configuration> dependencyConfigurations;
      String projectPath = projectComponentIdentifier.getProjectPath();
      boolean currentBuild;
      if (is31orBetter) {
        currentBuild = projectComponentIdentifier.getBuild().isCurrentBuild();
      }
      else {
        currentBuild = true;
      }

      if (projectDependencies.isEmpty()) {
        Project dependencyProject = myProject.findProject(projectPath);
        if (dependencyProject != null && currentBuild) {
          Configuration dependencyProjectConfiguration =
            dependencyProject.getConfigurations().findByName(Dependency.DEFAULT_CONFIGURATION);
          if (dependencyProjectConfiguration != null) {
            dependencyConfigurations = Collections.singleton(dependencyProjectConfiguration);
          } else {
            dependencyConfigurations = Collections.emptySet();
          }
        }
        else {
          dependencyConfigurations = Collections.emptySet();
          resolveFromArtifacts = true;
          selectionReason = "composite build substitution";
        }
      }
      else {
        dependencyConfigurations = new ArrayList<>();
        for (ProjectDependency dependency : projectDependencies) {
          Configuration targetConfiguration = getTargetConfiguration(dependency);
          if(targetConfiguration != null) {
            dependencyConfigurations.add(targetConfiguration);
          }
        }
      }

      for (Configuration it : dependencyConfigurations) {
        DefaultExternalProjectDependency dependency =
          createProjectDependency(componentResult, projectPath, it);

        if (!componentResult.equals(dependencyResult.getFrom())) {
          dependency.getDependencies().addAll(
            buildExternalDependencies(componentResult.getDependencies())
          );
        }
        result.add(dependency);
        resolvedDepsFiles.addAll(dependency.getProjectDependencyArtifacts());

        if (!it.getName().equals(Dependency.DEFAULT_CONFIGURATION)) {
          List<File> files = new ArrayList<>();
          PublishArtifactSet artifacts = it.getArtifacts();
          if (!artifacts.isEmpty()) {
            PublishArtifact artifact = artifacts.iterator().next();
            final MetaProperty taskProperty = DefaultGroovyMethods.hasProperty(artifact, "archiveTask");
            if (taskProperty != null && (taskProperty.getProperty(artifact) instanceof AbstractArchiveTask)) {

              AbstractArchiveTask archiveTask = (AbstractArchiveTask)taskProperty.getProperty(artifact);
              resolvedDepsFiles.add(new File(reflectiveCall(archiveTask, "getDestinationDir", File.class),
                                             reflectiveCall(archiveTask, "getArchiveName", String.class)));


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
              }
              catch (Exception e) {
                throw new RuntimeException(e);
              }
            }
          }

          if (!files.isEmpty()) {
            final DefaultFileCollectionDependency fileCollectionDependency = new DefaultFileCollectionDependency(files);
            fileCollectionDependency.setScope(scope);
            result.add(fileCollectionDependency);
            resolvedDepsFiles.addAll(files);
          }
        }
      }
    }

    if (resolveFromArtifacts) {
      Collection<ResolvedArtifact> artifacts = artifactMap.get(componentResult.getModuleVersion());

      if (artifacts.isEmpty()) {
        result.addAll(
          buildExternalDependencies(componentResult.getDependencies())
        );
      }

      boolean first = true;

      for (ResolvedArtifact artifact : artifacts) {
        String packaging = artifact.getExtension();
        String classifier = artifact.getClassifier();
        final ExternalDependency dependency;
        if (isProjectDependencyArtifact(artifact)) {
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

          Collection<ResolvedArtifact> resolvedArtifacts = artifactMap.get(componentResult.getModuleVersion());
          List<File> files = new ArrayList<>(resolvedArtifacts.size());
          for (ResolvedArtifact resolvedArtifact : resolvedArtifacts) {
            files.add(resolvedArtifact.getFile());
          }
          dDep.setProjectDependencyArtifacts(files);
          GradleSourceSetCachedFinder sourceSetFinder = GradleSourceSetCachedFinder.getInstance(myContext);
          dDep.setProjectDependencyArtifactsSources(sourceSetFinder.findArtifactSources(files));
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
            ResolvedArtifactResult sourcesResult = findMatchingArtifact(artifact, artifactsResult, SourcesArtifact.class);
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
            buildExternalDependencies(componentResult.getDependencies())
          );
          first = false;
        }

        result.add(dependency);
        resolvedDepsFiles.add(artifact.getFile());
      }
    }

    return result;
  }

  private ComponentResultKey getKey(ResolvedComponentResult result) {
    if (is46rBetter) {
      try {
        ResolvedVariantResult variant = reflectiveCall(result, "getVariant", ResolvedVariantResult.class);
        return new AttributesBasedKey(result.getId(), variant.getAttributes());
      } catch (Exception e) {
        myProject.getLogger().lifecycle("Error getting variant", e);
      }
    }
    return new ComponentIdKey(result.getId());
  }

  private interface ComponentResultKey{}

  private static class ComponentIdKey implements ComponentResultKey {
    private final ComponentIdentifier myId;

    ComponentIdKey(ComponentIdentifier id) {
      myId = id;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ComponentIdKey key = (ComponentIdKey)o;

      if (!myId.equals(key.myId)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myId.hashCode();
    }
  }

  private static class AttributesBasedKey implements ComponentResultKey {
    private final ComponentIdentifier myId;
    private final AttributeContainer myAttributes;

    AttributesBasedKey(ComponentIdentifier id, AttributeContainer attributes) {
      myId = id;
      myAttributes = attributes;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      AttributesBasedKey key = (AttributesBasedKey)o;

      if (!myId.equals(key.myId)) return false;
      if (!myAttributes.equals(key.myAttributes)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myId.hashCode();
      result = 31 * result + myAttributes.hashCode();
      return result;
    }

    @Override
    public String toString() {
      return "AttributesBasedKey{" +
             "myId=" + myId +
             ", myAttributes=" + myAttributes +
             '}';
    }
  }


  @NotNull
  private DefaultExternalProjectDependency createProjectDependency(ResolvedComponentResult componentResult,
                                                                   String projectPath,
                                                                   Configuration it) {
    String name = componentResult.getModuleVersion().getName();
    String group = componentResult.getModuleVersion().getGroup();
    String version = componentResult.getModuleVersion().getVersion();

    ComponentSelectionReason reason = componentResult.getSelectionReason();
    Object description = invokeMethod(reason, "getDescription");
    String selectionReason = description == null ? "" : description.toString();

    DefaultExternalProjectDependency dependency = new DefaultExternalProjectDependency();
    dependency.setName(name);
    dependency.setGroup(group);
    dependency.setVersion(version);
    dependency.setScope(scope);
    dependency.setSelectionReason(selectionReason);
    dependency.setProjectPath(projectPath);
    dependency.setConfigurationName(it.getName());
    Set<File> artifactsFiles = new LinkedHashSet<>(it.getAllArtifacts().getFiles().getFiles());
    dependency.setProjectDependencyArtifacts(artifactsFiles);
    GradleSourceSetCachedFinder sourceSetFinder = GradleSourceSetCachedFinder.getInstance(myContext);
    dependency.setProjectDependencyArtifactsSources(sourceSetFinder.findArtifactSources(artifactsFiles));

    if (it.getArtifacts().size() == 1) {
      PublishArtifact publishArtifact = it.getAllArtifacts().iterator().next();
      dependency.setClassifier(publishArtifact.getClassifier());
      dependency.setPackaging(publishArtifact.getExtension());
    }
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
