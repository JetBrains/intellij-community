// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.util;

import groovy.lang.MetaMethod;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.component.*;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.component.Artifact;
import org.gradle.api.internal.file.copy.CopySpecInternal;
import org.gradle.api.tasks.SourceSetOutput;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.internal.component.local.model.LocalComponentArtifactMetadata;
import org.gradle.internal.component.local.model.PublishArtifactLocalArtifactMetadata;
import org.gradle.language.base.artifact.SourcesArtifact;
import org.gradle.language.java.artifact.JavadocArtifact;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.*;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

class ScopedExternalDependenciesFactory {
  @Nullable private final String myScope;
  private final Project myProject;

  public ScopedExternalDependenciesFactory(@NotNull Project project, @Nullable String scope) {
    myScope = scope;
    myProject = project;
  }

  public Collection<ExternalDependency> processUnresolvedDeps(@NotNull Iterable<UnresolvedDependencyResult> unresolvedDependencies) {
    Collection<ExternalDependency> result = new HashSet<ExternalDependency>();
    for (UnresolvedDependencyResult dependency : unresolvedDependencies) {
      ComponentSelector attempted = dependency.getAttempted();
      if (attempted instanceof ModuleComponentSelector) {
        final ModuleComponentSelector attemptedMCSelector = (ModuleComponentSelector)attempted;
        final DefaultUnresolvedExternalDependency externalDependency = new DefaultUnresolvedExternalDependency();
        externalDependency.setName(attemptedMCSelector.getModule());
        externalDependency.setGroup(attemptedMCSelector.getGroup());
        externalDependency.setVersion(attemptedMCSelector.getVersion());
        externalDependency.setScope(myScope);
        externalDependency.setFailureMessage((dependency).getFailure().getMessage());

        result.add(externalDependency);
      }
    }
    return result;
  }

  public Collection<? extends ExternalDependency> processResolvedArtifacts(@NotNull ArtifactCollection artifacts,
                                                                            @NotNull Map<ModuleComponentIdentifier,
                                                                              Map<Class<? extends Artifact>,
                                                                                Set<ResolvedArtifactResult>>> sourcesAndJavadocs) {
    Collection<ExternalDependency> result = new HashSet<ExternalDependency>();
    for (ResolvedArtifactResult artifact : artifacts) {
      ComponentIdentifier identifier = artifact.getId().getComponentIdentifier();

      if (identifier instanceof ProjectComponentIdentifier) {
        result.add(createProjectDependency(artifact));
      } else if (identifier instanceof ModuleComponentIdentifier) {
        result.add(createExternalLibraryDependency(artifact, sourcesAndJavadocs.get(identifier)));
      } else {
        result.add(createFileDependency(artifact));
      }
    }
    return result;
  }

  private ExternalDependency createProjectDependency(@NotNull ResolvedArtifactResult artifact) {
    ProjectComponentIdentifier id = (ProjectComponentIdentifier)artifact.getId().getComponentIdentifier();
    DefaultExternalProjectDependency projectDependency = new DefaultExternalProjectDependency();
    Project targetProject = myProject.getRootProject().findProject(id.getProjectPath());

    projectDependency.setName(targetProject.getName());
    projectDependency.setGroup(targetProject.getGroup().toString());
    projectDependency.setVersion(targetProject.getVersion().toString());
    projectDependency.setProjectPath(id.getProjectPath());
    projectDependency.setProjectDependencyArtifacts(Collections.singleton(artifact.getFile()));
    projectDependency.setScope(myScope);

    projectDependency.setConfigurationName(artifact.getFile().getName());

    if (artifact.getId() instanceof PublishArtifactLocalArtifactMetadata) {

      LocalComponentArtifactMetadata artifactMetadata = (LocalComponentArtifactMetadata)artifact.getId();

      try {
        Field publishArtifactField = artifactMetadata.getClass().getField("publishArtifact");
        publishArtifactField.setAccessible(true);
        PublishArtifact publishArtifact = (PublishArtifact)publishArtifactField.get(artifactMetadata);
        Field archiveTaskField = publishArtifact.getClass().getField("archiveTask");
        archiveTaskField.setAccessible(true);
        AbstractArchiveTask archiveTask = (AbstractArchiveTask)archiveTaskField.get(publishArtifact);

        Set<File> sourceRootOutputs = collectSourceRootOutputs(archiveTask.getRootSpec());

        return new DefaultFileCollectionDependency(sourceRootOutputs);
      }
      catch (NoSuchFieldException e) {
        // ignore and continue
      }
      catch (IllegalAccessException e) {
        // ignore and continue
      }
    }

    return projectDependency;
  }

  private Set<File> collectSourceRootOutputs(CopySpecInternal spec) {
    Set<File> result = new LinkedHashSet<File>();
    final List<MetaMethod> sourcePathGetters =
      DefaultGroovyMethods.respondsTo(spec, "getSourcePaths", new Object[]{});
    if (!sourcePathGetters.isEmpty()) {
      Set<Object> sourcePaths = (Set<Object>)sourcePathGetters.get(0).doMethodInvoke(spec, new Object[]{});
      if (sourcePaths != null) {
        for (Object path : sourcePaths) {
          if (path instanceof SourceSetOutput) {
            result.addAll(((SourceSetOutput)path).getFiles());
          }
        }
      }
    }

    for (CopySpecInternal internal : spec.getChildren()) {
      result.addAll(collectSourceRootOutputs(internal));
    }

    return result;
  }

  @NotNull
  private ExternalDependency createExternalLibraryDependency(@NotNull ResolvedArtifactResult artifact,
                                                             @Nullable Map<Class<? extends Artifact>, Set<ResolvedArtifactResult>> sourcesAndJavadocs) {
    ModuleComponentIdentifier id = (ModuleComponentIdentifier)artifact.getId().getComponentIdentifier();
    DefaultExternalLibraryDependency externalLibDep = new DefaultExternalLibraryDependency();

    externalLibDep.setName(id.getModule());
    externalLibDep.setGroup(id.getGroup());
    externalLibDep.setVersion(id.getVersion());

    externalLibDep.setFile(artifact.getFile());
    externalLibDep.setScope(myScope);

    File javadocFile = extractFile(JavadocArtifact.class, sourcesAndJavadocs);
    if (javadocFile != null) {
      externalLibDep.setJavadoc(javadocFile);
    }

    File sourceFile = extractFile(SourcesArtifact.class, sourcesAndJavadocs);
    if (sourceFile != null) {
      externalLibDep.setSource(sourceFile);
    }
    return externalLibDep;
  }


  @NotNull
  private ExternalDependency createFileDependency(@NotNull ResolvedArtifactResult artifact) {
    DefaultFileCollectionDependency dependency = new DefaultFileCollectionDependency(Collections.singleton(artifact.getFile()));
    dependency.setName(artifact.getId().getComponentIdentifier().getDisplayName());
    dependency.setScope(myScope);
    return dependency;
  }


  @Nullable
  private File extractFile(@NotNull Class<? extends Artifact> type,
                           @Nullable Map<Class<? extends Artifact>, Set<ResolvedArtifactResult>> container) {
    if (container == null) {
      return null;
    }
    Set<ResolvedArtifactResult> artifactResults = container.get(type);
    if (artifactResults != null && !artifactResults.isEmpty()) {
      return artifactResults.iterator().next().getFile();
    }
    return null;
  }

}
