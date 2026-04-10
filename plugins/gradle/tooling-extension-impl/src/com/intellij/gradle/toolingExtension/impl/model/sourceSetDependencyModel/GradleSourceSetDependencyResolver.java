// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.sourceSetDependencyModel;

import com.intellij.gradle.toolingExtension.impl.model.dependencyModel.GradleDependencyResolver;
import com.intellij.gradle.toolingExtension.impl.model.sourceSetArtifactIndex.GradleSourceSetArtifactIndex;
import com.intellij.gradle.toolingExtension.impl.modelBuilder.Messages;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.plugins.WarPlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetOutput;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.AbstractExternalDependency;
import org.jetbrains.plugins.gradle.model.DefaultFileCollectionDependency;
import org.jetbrains.plugins.gradle.model.ExternalDependency;
import org.jetbrains.plugins.gradle.model.ExternalProjectDependency;
import org.jetbrains.plugins.gradle.model.FileCollectionDependency;
import org.jetbrains.plugins.gradle.tooling.Message;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import static com.intellij.gradle.toolingExtension.impl.model.sourceSetDependencyModel.GradleSourceSetDependencyMerger.COMPILE_SCOPE;
import static com.intellij.gradle.toolingExtension.impl.model.sourceSetDependencyModel.GradleSourceSetDependencyMerger.PROVIDED_SCOPE;
import static com.intellij.gradle.toolingExtension.impl.model.sourceSetDependencyModel.GradleSourceSetDependencyMerger.RUNTIME_SCOPE;

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Internal
public final class GradleSourceSetDependencyResolver {

  private final @NotNull ModelBuilderContext myContext;
  private final @NotNull Project myProject;

  private final @NotNull GradleSourceSetArtifactIndex mySourceSetArtifactIndex;

  public GradleSourceSetDependencyResolver(
    @NotNull ModelBuilderContext context,
    @NotNull Project project
  ) {
    myContext = context;
    myProject = project;

    mySourceSetArtifactIndex = GradleSourceSetArtifactIndex.getInstance(context);
  }

  public @NotNull Collection<ExternalDependency> resolveDependencies(@NotNull SourceSet sourceSet) {
    Collection<ExternalDependency> dependencies = GradleSourceSetDependencyMerger.mergeDependencies(
      GradleSourceSetDependencyMerger.enrichDependencies(
        resolveClasspathDependencies(getCompileClasspath(sourceSet), COMPILE_SCOPE),
        resolveConfigurationDependencies(sourceSet.getCompileClasspathConfigurationName(), COMPILE_SCOPE)
      ),
      GradleSourceSetDependencyMerger.enrichDependencies(
        resolveClasspathDependencies(sourceSet.getRuntimeClasspath(), RUNTIME_SCOPE),
        resolveConfigurationDependencies(sourceSet.getRuntimeClasspathConfigurationName(), RUNTIME_SCOPE)
      ),
      resolveSourceSetProvidedDependencies(sourceSet)
    );

    int order = 0;
    for (ExternalDependency dependency : dependencies) {
      ((AbstractExternalDependency)dependency).setClasspathOrder(++order);
    }
    return dependencies;
  }

  private @NotNull Collection<? extends ExternalDependency> resolveClasspathDependencies(
    @NotNull FileCollection classpath,
    @NotNull String scope
  ) {
    GradleSourceSetClasspathDependencyVisitor visitor = new GradleSourceSetClasspathDependencyVisitor(scope);
    GradleSourceSetDependencyVisitor.traverse(myContext, myProject, classpath, visitor);
    return visitor.getDependencies();
  }

  /**
   * Returns the compile classpath for the given source set.
   * <p>
   * Prefers {@link AbstractCompile#getClasspath} over {@link SourceSet#getCompileClasspath}
   * because users may customize the compile task's classpath directly (e.g. {@code compileJava { classpath += ... }}),
   * and such additions are not reflected by the source set's own classpath property.
   * <p>
   * Falls back to {@link SourceSet#getCompileClasspath} when the compile task is absent or not an
   * {@link AbstractCompile}, or when resolving the task classpath throws an exception.
   */
  private @NotNull FileCollection getCompileClasspath(SourceSet sourceSet) {
    try {
      AbstractCompile compileTask = myProject.getTasks().withType(AbstractCompile.class)
        .findByName(sourceSet.getCompileJavaTaskName());
      if (compileTask != null) {
        return compileTask.getClasspath();
      }
    }
    catch (Exception e) {
      myContext.getMessageReporter().createMessage()
        .withGroup(Messages.DEPENDENCY_CLASSPATH_MODEL_GROUP)
        .withTitle("Compile classpath resolution error")
        .withText(String.format(
          "Error obtaining compile classpath for java compilation task for [%s] in project [%s]",
          sourceSet.getName(),
          myProject.getPath()
        ))
        .withException(e)
        .withKind(Message.Kind.WARNING)
        .reportMessage(myProject);
    }
    return sourceSet.getCompileClasspath();
  }

  private @NotNull Collection<? extends ExternalDependency> resolveConfigurationDependencies(
    @NotNull String configurationName,
    @NotNull String scope
  ) {
    Configuration configuration = myProject.getConfigurations().getByName(configurationName);
    return resolveConfigurationDependencies(configuration, scope);
  }

  private @NotNull Collection<? extends ExternalDependency> resolveConfigurationDependencies(
    @NotNull Configuration configuration,
    @NotNull String scope
  ) {
    GradleDependencyResolver dependencyResolver = new GradleDependencyResolver(myContext, myProject);
    Collection<ExternalDependency> dependencies = dependencyResolver.resolveDependencies(configuration);
    for (ExternalDependency dependency : dependencies) {
      ((AbstractExternalDependency)dependency).setScope(scope);
    }
    Collection<FileCollectionDependency> runtimeDependencies =
      RUNTIME_SCOPE.equals(scope)
      ? resolveSourceSetOutputDirsRuntimeFileDependencies(dependencies)
      : Collections.emptySet();

    List<ExternalDependency> result = new ArrayList<>();
    result.addAll(runtimeDependencies);
    result.addAll(dependencies);
    return result;
  }

  private static @NotNull Collection<ExternalDependency> resolveSourceOutputFileDependencies(
    @NotNull SourceSetOutput sourceSetOutput,
    @NotNull String scope
  ) {
    Collection<ExternalDependency> result = new ArrayList<>(2);
    List<File> files = new ArrayList<>(sourceSetOutput.getClassesDirs().getFiles());
    files.add(sourceSetOutput.getResourcesDir());
    DefaultFileCollectionDependency fileCollectionDependency = new DefaultFileCollectionDependency(files);
    fileCollectionDependency.setScope(scope);
    result.add(fileCollectionDependency);

    if (RUNTIME_SCOPE.equals(scope)) {
      ExternalDependency runtimeDependency = resolveSourceSetOutputDirsRuntimeFileDependency(sourceSetOutput);
      if (runtimeDependency != null) {
        result.add(runtimeDependency);
      }
    }
    return result;
  }

  private @NotNull Collection<FileCollectionDependency> resolveSourceSetOutputDirsRuntimeFileDependencies(
    @NotNull Collection<ExternalDependency> dependencies
  ) {
    Collection<FileCollectionDependency> result = new LinkedHashSet<>();
    for (ExternalDependency dependency : dependencies) {
      if (dependency instanceof ExternalProjectDependency) {
        ExternalProjectDependency projectDependency = (ExternalProjectDependency)dependency;
        for (File artifactFile : projectDependency.getProjectDependencyArtifacts()) {
          SourceSet sourceSet = mySourceSetArtifactIndex.findByArtifact(artifactFile.getPath());
          if (sourceSet != null) {
            FileCollectionDependency runtimeDependency = resolveSourceSetOutputDirsRuntimeFileDependency(sourceSet.getOutput());
            if (runtimeDependency != null) {
              result.add(runtimeDependency);
            }
          }
        }
      }
    }
    return result;
  }

  private static @Nullable FileCollectionDependency resolveSourceSetOutputDirsRuntimeFileDependency(
    @NotNull SourceSetOutput sourceSetOutput
  ) {
    List<File> runtimeOutputDirs = new ArrayList<>(sourceSetOutput.getDirs().getFiles());
    if (!runtimeOutputDirs.isEmpty()) {
      DefaultFileCollectionDependency dependency = new DefaultFileCollectionDependency(runtimeOutputDirs);
      dependency.setScope(RUNTIME_SCOPE);
      dependency.setExcludedFromIndexing(true);
      return dependency;
    }
    return null;
  }

  private @NotNull Collection<ExternalDependency> resolveSourceSetProvidedDependencies(@NotNull SourceSet sourceSet) {
    if (!sourceSet.getName().equals("main") || myProject.getPlugins().findPlugin(WarPlugin.class) == null) {
      return Collections.emptyList();
    }
    Collection<ExternalDependency> result = new LinkedHashSet<>();
    Configuration providedCompile = myProject.getConfigurations().findByName("providedCompile");
    if (providedCompile != null) {
      result.addAll(resolveConfigurationDependencies(providedCompile, PROVIDED_SCOPE));
    }
    Configuration providedRuntime = myProject.getConfigurations().findByName("providedRuntime");
    if (providedRuntime != null) {
      result.addAll(resolveConfigurationDependencies(providedRuntime, PROVIDED_SCOPE));
    }
    return result;
  }

  private static @NotNull ExternalDependency resolveFileCollectionDependency(@NotNull FileCollection fileCollection) {
    return new DefaultFileCollectionDependency(fileCollection.getFiles());
  }

  private static @NotNull ExternalDependency resolveFileDependency(@NotNull FileSystemLocation file) {
    return new DefaultFileCollectionDependency(Collections.singleton(file.getAsFile()));
  }

  private class GradleSourceSetClasspathDependencyVisitor implements GradleSourceSetDependencyVisitor {

    private final @NotNull String scope;
    private final @NotNull Collection<ExternalDependency> dependencies = new LinkedHashSet<>();

    private GradleSourceSetClasspathDependencyVisitor(@NotNull String scope) {
      this.scope = scope;
    }

    private @NotNull Collection<ExternalDependency> getDependencies() {
      return dependencies;
    }

    @Override
    public void visitConfiguration(@NotNull Configuration configuration) {
      dependencies.addAll(resolveConfigurationDependencies(configuration, scope));
    }

    @Override
    public void visitSourceSetOutput(@NotNull SourceSetOutput sourceSetOutput) {
      dependencies.addAll(resolveSourceOutputFileDependencies(sourceSetOutput, scope));
    }

    @Override
    public void visitFileCollection(@NotNull FileCollection fileCollection) {
      dependencies.add(resolveFileCollectionDependency(fileCollection));
    }

    @Override
    public void visitFile(@NotNull FileSystemLocation file) {
      dependencies.add(resolveFileDependency(file));
    }
  }
}
