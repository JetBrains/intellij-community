// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.modelProvider;

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Collects, for each Gradle build, models with specified {@link #modelClass}.
 * Custom models should be prepared by corresponding {@link ModelBuilderService}.
 */
public class GradleClassBuildModelProvider<T> implements ProjectImportModelProvider {

  private static final long serialVersionUID = 2L;

  private final Class<T> modelClass;
  private final GradleModelFetchPhase phase;

  public GradleClassBuildModelProvider(Class<T> modelClass) {
    this(modelClass, GradleModelFetchPhase.ADDITIONAL_MODEL_PHASE);
  }

  public GradleClassBuildModelProvider(Class<T> modelClass, GradleModelFetchPhase phase) {
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
    @NotNull Collection<? extends @NotNull GradleBuild> buildModels,
    @NotNull GradleModelConsumer modelConsumer
  ) {
    for (GradleBuild buildModel : buildModels) {
      T instance = controller.findModel(buildModel, modelClass);
      if (instance != null) {
        modelConsumer.consumeBuildModel(buildModel, instance, modelClass);
      }
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    GradleClassBuildModelProvider<?> provider = (GradleClassBuildModelProvider<?>)o;
    if (!modelClass.equals(provider.modelClass)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return modelClass.hashCode();
  }

  @Override
  public String toString() {
    return "BuildModel(" + modelClass.getName() + ")";
  }

  public static List<GradleClassBuildModelProvider<?>> createAll(Class<?>... modelClasses) {
    return createAll(Arrays.asList(modelClasses));
  }

  public static List<GradleClassBuildModelProvider<?>> createAll(Collection<Class<?>> modelClasses) {
    List<GradleClassBuildModelProvider<?>> providers = new ArrayList<>();
    for (Class<?> modelClass : modelClasses) {
      providers.add(new GradleClassBuildModelProvider<>(modelClass));
    }
    return providers;
  }
}
