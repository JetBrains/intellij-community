// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.modelProvider;

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Collects, for each Gradle project, models with specified {@link #modelClass}.
 * Custom models should be prepared by corresponding {@link ModelBuilderService}.
 */
public class GradleClassProjectModelProvider<T> implements ProjectImportModelProvider {

  private static final long serialVersionUID = 2L;

  private final Class<T> modelClass;
  private final GradleModelFetchPhase phase;

  public GradleClassProjectModelProvider(Class<T> modelClass) {
    this(modelClass, GradleModelFetchPhase.ADDITIONAL_MODEL_PHASE);
  }

  public GradleClassProjectModelProvider(Class<T> modelClass, GradleModelFetchPhase phase) {
    this.modelClass = modelClass;
    this.phase = phase;
  }

  @Override
  public GradleModelFetchPhase getPhase() {
    return phase;
  }

  @Override
  public @NotNull String getName() {
    return modelClass.getSimpleName();
  }

  @Override
  public void populateModels(
    @NotNull BuildController controller,
    @NotNull Collection<? extends GradleBuild> buildModels,
    @NotNull GradleModelConsumer modelConsumer
  ) {
    for (GradleBuild buildModel : buildModels) {
      for (BasicGradleProject projectModel : buildModel.getProjects()) {
        T instance = controller.findModel(projectModel, modelClass);
        if (instance != null) {
          modelConsumer.consumeProjectModel(projectModel, instance, modelClass);
        }
      }
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    GradleClassProjectModelProvider<?> provider = (GradleClassProjectModelProvider<?>)o;
    if (!modelClass.equals(provider.modelClass)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return modelClass.hashCode();
  }

  @Override
  public String toString() {
    return "ProjectModel(" + modelClass.getName() + ")";
  }

  public static List<? extends GradleClassProjectModelProvider<?>> createAll(Class<?>... modelClasses) {
    return createAll(Arrays.asList(modelClasses));
  }

  public static List<? extends GradleClassProjectModelProvider<?>> createAll(Collection<Class<?>> modelClasses) {
    List<GradleClassProjectModelProvider<?>> providers = new ArrayList<>();
    for (Class<?> modelClass : modelClasses) {
      providers.add(new GradleClassProjectModelProvider<>(modelClass));
    }
    return providers;
  }
}
