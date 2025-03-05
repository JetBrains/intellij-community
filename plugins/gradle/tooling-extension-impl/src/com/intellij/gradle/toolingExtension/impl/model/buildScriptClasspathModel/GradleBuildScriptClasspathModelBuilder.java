// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.buildScriptClasspathModel;

import com.intellij.gradle.toolingExtension.impl.model.dependencyModel.GradleDependencyResolver;
import com.intellij.gradle.toolingExtension.impl.model.dependencyModel.GradleDependencyTraverser;
import com.intellij.gradle.toolingExtension.impl.modelBuilder.Messages;
import com.intellij.gradle.toolingExtension.impl.util.collectionUtil.GradleCollections;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.*;
import org.jetbrains.plugins.gradle.tooling.AbstractModelBuilderService;
import org.jetbrains.plugins.gradle.tooling.Message;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;
import org.jetbrains.plugins.gradle.tooling.internal.ClasspathEntryModelImpl;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Internal
public class GradleBuildScriptClasspathModelBuilder extends AbstractModelBuilderService {

  private static final String CLASSPATH_CONFIGURATION_NAME = "classpath";

  @Override
  public boolean canBuild(String modelName) {
    return GradleBuildScriptClasspathModel.class.getName().equals(modelName);
  }

  @Override
  public @Nullable Object buildAll(final @NotNull String modelName, final @NotNull Project project, @NotNull ModelBuilderContext context) {
    DefaultGradleBuildScriptClasspathModel buildScriptClasspath = new DefaultGradleBuildScriptClasspathModel();
    buildScriptClasspath.setGradleHomeDir(project.getGradle().getGradleHomeDir());
    buildScriptClasspath.setGradleVersion(GradleVersion.current().getVersion());

    Project parentProject = project.getParent();
    if (parentProject != null) {
      GradleBuildScriptClasspathModel parentBuildScriptClasspath = GradleBuildScriptClasspathCache.getInstance(context)
        .getBuildScriptClasspathModel(parentProject);
      for (ClasspathEntryModel classpathEntryModel : parentBuildScriptClasspath.getClasspath()) {
        buildScriptClasspath.add(classpathEntryModel);
      }
    }

    Configuration classpathConfiguration = project.getBuildscript().getConfigurations().findByName(CLASSPATH_CONFIGURATION_NAME);
    if (classpathConfiguration != null) {
      Collection<ExternalDependency> dependencies = new GradleDependencyResolver(context, project)
        .resolveDependencies(classpathConfiguration);

      for (ExternalDependency dependency : new GradleDependencyTraverser(dependencies)) {
        if (dependency instanceof ExternalProjectDependency) {
          ExternalProjectDependency projectDependency = (ExternalProjectDependency)dependency;
          Collection<File> projectDependencyArtifacts = projectDependency.getProjectDependencyArtifacts();
          Collection<File> projectDependencyArtifactsSources = projectDependency.getProjectDependencyArtifactsSources();
          buildScriptClasspath.add(new ClasspathEntryModelImpl(
            projectDependencyArtifacts,
            projectDependencyArtifactsSources,
            Collections.emptySet()
          ));
        }
        else if (dependency instanceof ExternalLibraryDependency) {
          final ExternalLibraryDependency libraryDep = (ExternalLibraryDependency)dependency;
          buildScriptClasspath.add(new ClasspathEntryModelImpl(
            GradleCollections.createMaybeSingletonList(libraryDep.getFile()),
            GradleCollections.createMaybeSingletonList(libraryDep.getSource()),
            GradleCollections.createMaybeSingletonList(libraryDep.getJavadoc())
          ));
        }
        else if (dependency instanceof ExternalMultiLibraryDependency) {
          ExternalMultiLibraryDependency multiLibraryDependency = (ExternalMultiLibraryDependency)dependency;
          buildScriptClasspath.add(new ClasspathEntryModelImpl(
            multiLibraryDependency.getFiles(),
            multiLibraryDependency.getSources(),
            multiLibraryDependency.getJavadoc()
          ));
        }
        else if (dependency instanceof FileCollectionDependency) {
          FileCollectionDependency fileCollectionDependency = (FileCollectionDependency)dependency;
          buildScriptClasspath.add(new ClasspathEntryModelImpl(
            fileCollectionDependency.getFiles(),
            Collections.emptySet(),
            Collections.emptySet()
          ));
        }
      }
    }

    GradleBuildScriptClasspathCache.getInstance(context)
      .setBuildScriptClasspathModel(project, buildScriptClasspath);

    return buildScriptClasspath;
  }


  @Override
  public void reportErrorMessage(
    @NotNull String modelName,
    @NotNull Project project,
    @NotNull ModelBuilderContext context,
    @NotNull Exception exception
  ) {
    GradleBuildScriptClasspathCache.getInstance(context)
      .markBuildScriptClasspathModelAsError(project);

    context.getMessageReporter().createMessage()
      .withGroup(Messages.BUILDSCRIPT_CLASSPATH_MODEL_GROUP)
      .withKind(Message.Kind.WARNING)
      .withTitle("Project build classpath resolve failure")
      .withText("Unable to resolve additional buildscript classpath dependencies")
      .withException(exception)
      .reportMessage(project);
  }
}
