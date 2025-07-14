// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.sourceSetDependencyModel;

import com.intellij.gradle.toolingExtension.impl.model.dependencyModel.GradleDependencyResolver;
import com.intellij.gradle.toolingExtension.impl.model.sourceSetArtifactIndex.GradleSourceSetArtifactIndex;
import com.intellij.gradle.toolingExtension.impl.modelBuilder.Messages;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.*;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.UnionFileCollection;
import org.gradle.api.plugins.WarPlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetOutput;
import org.gradle.api.tasks.compile.AbstractCompile;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.ExternalDependency;
import org.jetbrains.plugins.gradle.model.FileCollectionDependency;
import org.jetbrains.plugins.gradle.model.*;
import org.jetbrains.plugins.gradle.tooling.Message;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.Supplier;

import java.io.File;
import java.util.*;

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Internal
public final class GradleSourceSetDependencyResolver {

  private static final String COMPILE_SCOPE = "COMPILE";
  private static final String RUNTIME_SCOPE = "RUNTIME";
  private static final String PROVIDED_SCOPE = "PROVIDED";

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
    Collection<? extends ExternalDependency> compileDependencies = resolveSourceSetCompileDependencies(sourceSet);
    Collection<? extends ExternalDependency> runtimeDependencies = resolveSourceSetRuntimeDependencies(sourceSet);
    Collection<? extends ExternalDependency> dependencies = mergeResolvedDependencies(compileDependencies, runtimeDependencies);
    Collection<? extends ExternalDependency> providedDependencies = resolveSourceSetProvidedDependencies(sourceSet, dependencies);

    Collection<ExternalDependency> result = new LinkedHashSet<>();
    result.addAll(dependencies);
    result.addAll(providedDependencies);

    int order = 0;
    for (ExternalDependency dependency : result) {
      ((AbstractExternalDependency)dependency).setClasspathOrder(++order);
    }
    return result;
  }

  private @NotNull Collection<? extends ExternalDependency> resolveSourceSetCompileDependencies(@NotNull SourceSet sourceSet) {
    FileCollection compileClasspath = getCompileClasspath(sourceSet);
    return resolveDependenciesWithDefault(compileClasspath, COMPILE_SCOPE, new Supplier<Collection<? extends ExternalDependency>>() {
      @Override
      public Collection<? extends ExternalDependency> get() {
        String configurationName = sourceSet.getCompileClasspathConfigurationName();
        Configuration configuration = myProject.getConfigurations().getByName(configurationName);
        return getDependencies(configuration, COMPILE_SCOPE);
      }
    });
  }

  private @NotNull Collection<? extends ExternalDependency> resolveSourceSetRuntimeDependencies(@NotNull SourceSet sourceSet) {
    FileCollection runtimeClasspath = sourceSet.getRuntimeClasspath();
    return resolveDependenciesWithDefault(runtimeClasspath, RUNTIME_SCOPE, new Supplier<Collection<? extends ExternalDependency>>() {
      @Override
      public Collection<? extends ExternalDependency> get() {
        String configurationName = sourceSet.getRuntimeClasspathConfigurationName();
        Configuration configuration = myProject.getConfigurations().getByName(configurationName);
        return getDependencies(configuration, RUNTIME_SCOPE);
      }
    });
  }

  private @NotNull FileCollection getCompileClasspath(SourceSet sourceSet) {
    String compileTaskName = sourceSet.getCompileJavaTaskName();
    Task compileTask = myProject.getTasks().findByName(compileTaskName);
    if (compileTask instanceof AbstractCompile) {
      try {
        return ((AbstractCompile)compileTask).getClasspath();
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
    }
    return sourceSet.getCompileClasspath();
  }

  private @NotNull Collection<ExternalDependency> resolveDependencies(
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

  /**
   * Merges two lists of resolved dependencies by rules:
   * <ul>
   * <li>Dependencies that present only in the compile scope are marked with {@link #PROVIDED_SCOPE}</li>
   * <li>Dependencies that present only in the runtime scope are marked with {@link #RUNTIME_SCOPE}</li>
   * <li>Dependencies that present in the both scopes are marked with {@link #COMPILE_SCOPE}</li>
   * </ul>
   *
   * @param compileDependencies dependencies that at least present in the compile scope
   * @param runtimeDependencies dependencies that at least present in the runtime scope
   */
  private static @NotNull Collection<? extends ExternalDependency> mergeResolvedDependencies(
    @NotNull Collection<? extends ExternalDependency> compileDependencies,
    @NotNull Collection<? extends ExternalDependency> runtimeDependencies
  ) {
    Multimap<Collection<File>, ExternalDependency> runtimeDependencyFileIndex = HashMultimap.create();
    for (ExternalDependency dependency : runtimeDependencies) {
      Collection<File> resolvedFiles = getFiles(dependency);
      runtimeDependencyFileIndex.put(resolvedFiles, dependency);
    }
    Collection<ExternalDependency> runtimeOnlyDependencies = new LinkedHashSet<>(runtimeDependencies);
    for (ExternalDependency dependency : compileDependencies) {
      Collection<File> resolvedFiles = getFiles(dependency);
      Collection<ExternalDependency> compileAndRuntimeDependencies = runtimeDependencyFileIndex.get(resolvedFiles);
      if (compileAndRuntimeDependencies.isEmpty()) {
        ((AbstractExternalDependency)dependency).setScope(PROVIDED_SCOPE);
      }
      else {
        runtimeOnlyDependencies.removeAll(compileAndRuntimeDependencies);
      }
    }

    Collection<ExternalDependency> result = new LinkedHashSet<>();
    result.addAll(compileDependencies);
    result.addAll(runtimeOnlyDependencies);
    return result;
  }

  private @NotNull Collection<? extends ExternalDependency> resolveSourceSetProvidedDependencies(
    @NotNull SourceSet sourceSet,
    @NotNull Collection<? extends ExternalDependency> resolvedDependencies
  ) {
    Collection<Configuration> providedConfigurations = new LinkedHashSet<>();
    if (sourceSet.getName().equals("main") && myProject.getPlugins().findPlugin(WarPlugin.class) != null) {
      Configuration providedCompile = myProject.getConfigurations().findByName("providedCompile");
      if (providedCompile != null) {
        providedConfigurations.add(providedCompile);
      }
      Configuration providedRuntime = myProject.getConfigurations().findByName("providedRuntime");
      if (providedRuntime != null) {
        providedConfigurations.add(providedRuntime);
      }
    }
    if (providedConfigurations.isEmpty()) {
      return Collections.emptyList();
    }

    Multimap<Object, ExternalDependency> dependencyFileIndex = ArrayListMultimap.create();
    for (ExternalDependency dependency : resolvedDependencies) {
      dependencyFileIndex.put(getFiles(dependency), dependency);
    }

    Collection<ExternalDependency> result = new LinkedHashSet<>();
    for (Configuration configuration : providedConfigurations) {
      Collection<ExternalDependency> providedDependencies = resolveDependencies(configuration, PROVIDED_SCOPE);
      for (Iterator<ExternalDependency> iterator = providedDependencies.iterator(); iterator.hasNext(); ) {
        ExternalDependency providedDependency = iterator.next();
        Collection<File> files = getFiles(providedDependency);
        Collection<ExternalDependency> dependencies = dependencyFileIndex.get(files);
        if (!dependencies.isEmpty()) {
          for (ExternalDependency dependency : dependencies) {
            ((AbstractExternalDependency)dependency).setScope(PROVIDED_SCOPE);
          }
          iterator.remove();
        }
      }
      result.addAll(providedDependencies);
    }
    return result;
  }

  private @NotNull Collection<? extends ExternalDependency> getDependencies(
    @NotNull FileCollection fileCollection,
    @NotNull String scope
  ) {
    return resolveDependenciesWithDefault(fileCollection, scope, new Supplier<Collection<? extends ExternalDependency>>() {
      @Override
      public Collection<? extends ExternalDependency> get() {
        return Collections.singleton(new DefaultFileCollectionDependency(fileCollection.getFiles()));
      }
    });
  }

  private @NotNull Collection<? extends ExternalDependency> resolveDependenciesWithDefault(
    @NotNull FileCollection fileCollection,
    @NotNull String scope,
    @NotNull Supplier<Collection<? extends ExternalDependency>> defaultValueProvider
  ) {
    if (fileCollection instanceof ConfigurableFileCollection) {
      return getDependencies(((ConfigurableFileCollection)fileCollection).getFrom(), scope);
    }
    else if (fileCollection instanceof UnionFileCollection) {
      return getDependencies(((UnionFileCollection)fileCollection).getSources(), scope);
    }
    else if (fileCollection instanceof Configuration) {
      return resolveDependencies((Configuration)fileCollection, scope);
    }
    else if (fileCollection instanceof SourceSetOutput) {
      return resolveSourceOutputFileDependencies((SourceSetOutput)fileCollection, scope);
    }
    return defaultValueProvider.get();
  }

  private @NotNull Collection<? extends ExternalDependency> getDependencies(
    @NotNull Iterable<?> fileCollections,
    @NotNull String scope
  ) {
    Set<ExternalDependency> result = new LinkedHashSet<>();
    for (Object fileCollection : fileCollections) {
      if (fileCollection instanceof FileCollection) {
        result.addAll(getDependencies((FileCollection)fileCollection, scope));
      }
    }
    return result;
  }

  private static @NotNull Collection<File> getFiles(@NotNull ExternalDependency dependency) {
    if (dependency instanceof ExternalLibraryDependency) {
      return Collections.singleton(((ExternalLibraryDependency)dependency).getFile());
    }
    else if (dependency instanceof FileCollectionDependency) {
      return ((FileCollectionDependency)dependency).getFiles();
    }
    else if (dependency instanceof ExternalMultiLibraryDependency) {
      return ((ExternalMultiLibraryDependency)dependency).getFiles();
    }
    else if (dependency instanceof ExternalProjectDependency) {
      return ((ExternalProjectDependency)dependency).getProjectDependencyArtifacts();
    }
    return Collections.emptySet();
  }
}
