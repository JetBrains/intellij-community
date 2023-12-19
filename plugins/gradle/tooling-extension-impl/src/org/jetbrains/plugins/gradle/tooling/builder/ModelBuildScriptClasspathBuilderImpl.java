// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.builder;

import com.intellij.gradle.toolingExtension.impl.model.dependencyDownloadPolicyModel.GradleDependencyDownloadPolicy;
import com.intellij.gradle.toolingExtension.impl.model.dependencyDownloadPolicyModel.GradleDependencyDownloadPolicyCache;
import com.intellij.gradle.toolingExtension.impl.modelBuilder.Messages;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.*;
import org.jetbrains.plugins.gradle.tooling.AbstractModelBuilderService;
import org.jetbrains.plugins.gradle.tooling.Message;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;
import org.jetbrains.plugins.gradle.tooling.internal.BuildScriptClasspathModelImpl;
import org.jetbrains.plugins.gradle.tooling.internal.ClasspathEntryModelImpl;
import org.jetbrains.plugins.gradle.tooling.util.DependencyTraverser;
import org.jetbrains.plugins.gradle.tooling.util.resolve.DependencyResolverImpl;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Vladislav.Soroka
 */
public class ModelBuildScriptClasspathBuilderImpl extends AbstractModelBuilderService {

  private static final String CLASSPATH_CONFIGURATION_NAME = "classpath";
  private final Map<String, BuildScriptClasspathModelImpl> cache = new ConcurrentHashMap<>();

  @Override
  public boolean canBuild(String modelName) {
    return BuildScriptClasspathModel.class.getName().equals(modelName);
  }

  @Nullable
  @Override
  public Object buildAll(@NotNull final String modelName, @NotNull final Project project, @NotNull ModelBuilderContext context) {
    BuildScriptClasspathModelImpl buildScriptClasspath = cache.get(project.getPath());
    if (buildScriptClasspath != null) return buildScriptClasspath;

    buildScriptClasspath = new BuildScriptClasspathModelImpl();
    final File gradleHomeDir = project.getGradle().getGradleHomeDir();
    buildScriptClasspath.setGradleHomeDir(gradleHomeDir);
    buildScriptClasspath.setGradleVersion(GradleVersion.current().getVersion());

    GradleDependencyDownloadPolicy dependencyDownloadPolicy = GradleDependencyDownloadPolicyCache.getInstance(context)
      .getDependencyDownloadPolicy(project);

    Project parent = project.getParent();
    if (parent != null) {
      BuildScriptClasspathModelImpl parentBuildScriptClasspath = (BuildScriptClasspathModelImpl)buildAll(modelName, parent, context);
      if (parentBuildScriptClasspath != null) {
        for (ClasspathEntryModel classpathEntryModel : parentBuildScriptClasspath.getClasspath()) {
          buildScriptClasspath.add(classpathEntryModel);
        }
      }
    }
    Configuration classpathConfiguration = project.getBuildscript().getConfigurations().findByName(CLASSPATH_CONFIGURATION_NAME);
    if (classpathConfiguration == null) return null;

    Collection<ExternalDependency> dependencies = new DependencyResolverImpl(context, project, dependencyDownloadPolicy)
      .resolveDependencies(classpathConfiguration);

    for (ExternalDependency dependency : new DependencyTraverser(dependencies)) {
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
          singletonListOrEmpty(libraryDep.getFile()),
          singletonListOrEmpty(libraryDep.getSource()),
          singletonListOrEmpty(libraryDep.getJavadoc())
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

    cache.put(project.getPath(), buildScriptClasspath);
    return buildScriptClasspath;
  }

  @Override
  public void reportErrorMessage(
    @NotNull String modelName,
    @NotNull Project project,
    @NotNull ModelBuilderContext context,
    @NotNull Exception exception
  ) {
    context.getMessageReporter().createMessage()
      .withGroup(Messages.BUILDSCRIPT_CLASSPATH_MODEL_GROUP)
      .withKind(Message.Kind.WARNING)
      .withTitle("Project build classpath resolve failure")
      .withText("Unable to resolve additional buildscript classpath dependencies")
      .withException(exception)
      .reportMessage(project);
  }

  @NotNull
  private static List<File> singletonListOrEmpty(@Nullable File file) {
    return file == null ? Collections.emptyList() : Collections.singletonList(file);
  }
}
