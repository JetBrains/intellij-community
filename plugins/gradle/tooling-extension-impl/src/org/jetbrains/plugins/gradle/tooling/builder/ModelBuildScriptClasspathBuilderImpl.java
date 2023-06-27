// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.builder;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.gradle.plugins.ide.idea.model.IdeaModule;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.*;
import org.jetbrains.plugins.gradle.tooling.AbstractModelBuilderService;
import org.jetbrains.plugins.gradle.tooling.ErrorMessageBuilder;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;
import org.jetbrains.plugins.gradle.tooling.internal.BuildScriptClasspathModelImpl;
import org.jetbrains.plugins.gradle.tooling.internal.ClasspathEntryModelImpl;
import org.jetbrains.plugins.gradle.tooling.util.DependencyTraverser;
import org.jetbrains.plugins.gradle.tooling.util.SourceSetCachedFinder;
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
  private SourceSetCachedFinder mySourceSetFinder = null;

  @Override
  public boolean canBuild(String modelName) {
    return BuildScriptClasspathModel.class.getName().equals(modelName);
  }

  @Nullable
  @Override
  public Object buildAll(@NotNull final String modelName, @NotNull final Project project, @NotNull ModelBuilderContext context) {
    BuildScriptClasspathModelImpl buildScriptClasspath = cache.get(project.getPath());
    if (buildScriptClasspath != null) return buildScriptClasspath;

    if (mySourceSetFinder == null) mySourceSetFinder = new SourceSetCachedFinder(context);

    buildScriptClasspath = new BuildScriptClasspathModelImpl();
    final File gradleHomeDir = project.getGradle().getGradleHomeDir();
    buildScriptClasspath.setGradleHomeDir(gradleHomeDir);
    buildScriptClasspath.setGradleVersion(GradleVersion.current().getVersion());

    boolean downloadJavadoc = false;
    boolean downloadSources = true;

    final IdeaPlugin ideaPlugin = project.getPlugins().findPlugin(IdeaPlugin.class);
    if (ideaPlugin != null) {
      final IdeaModule ideaModule = ideaPlugin.getModel().getModule();
      downloadJavadoc = ideaModule.isDownloadJavadoc();
      downloadSources = ideaModule.isDownloadSources();
    }
    boolean forceDisableSourceDownload = Boolean.parseBoolean(System.getProperty("idea.gradle.download.sources", "true"));
    downloadSources = downloadSources && forceDisableSourceDownload;

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

    Collection<ExternalDependency> dependencies = new DependencyResolverImpl(project, downloadJavadoc, downloadSources, mySourceSetFinder).resolveDependencies(classpathConfiguration);

    for (ExternalDependency dependency : new DependencyTraverser(dependencies)) {
      if (dependency instanceof ExternalProjectDependency) {
        ExternalProjectDependency projectDependency = (ExternalProjectDependency)dependency;
        Collection<File> projectDependencyArtifacts = projectDependency.getProjectDependencyArtifacts();
        Collection<File> projectDependencyArtifactsSources = projectDependency.getProjectDependencyArtifactsSources();
        buildScriptClasspath.add(new ClasspathEntryModelImpl(
          projectDependencyArtifacts,
          projectDependencyArtifactsSources,
          Collections.<File>emptySet()
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
          Collections.<File>emptySet(),
          Collections.<File>emptySet()
        ));
      }
    }

    cache.put(project.getPath(), buildScriptClasspath);
    return buildScriptClasspath;
  }

  @NotNull
  @Override
  public ErrorMessageBuilder getErrorMessageBuilder(@NotNull Project project, @NotNull Exception e) {
    return ErrorMessageBuilder.create(
      project, e, "Project build classpath resolve errors"
    ).withDescription("Unable to resolve additional buildscript classpath dependencies");
  }

  @NotNull
  private static List<File> singletonListOrEmpty(@Nullable File file) {
    return file == null ? Collections.<File>emptyList() : Collections.singletonList(file);
  }
}
