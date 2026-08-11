// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.model;

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase;
import com.intellij.gradle.toolingExtension.modelAction.GradleModelController;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.BuildModel;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.jetbrains.annotations.ApiStatus.NonExtendable;
import org.jetbrains.annotations.ApiStatus.OverrideOnly;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.Collection;

/**
 * Contributes Gradle Tooling API models to the IDE project import.
 * <p>
 * Implementations are registered in {@code GradleProjectResolverExtension} and are invoked during the {@link #getPhase()} phase.
 * A provider uses {@link GradleModelController} to request built-in Gradle or custom IDE models from Gradle and reports the collected
 * models to {@link GradleModelConsumer}. The import action stores consumed models in the import result and sends them back to the IDE.
 * <p>
 * Providers are executed inside a {@code GradleModelFetchAction} and are serialized as part of that action.
 */
@OverrideOnly
public interface ProjectImportModelProvider extends Serializable {

  /**
   * Returns the phase in which this provider should collect models.
   */
  default GradleModelFetchPhase getPhase() {
    return GradleModelFetchPhase.ADDITIONAL_MODEL_PHASE;
  }

  /**
   * Returns a human-readable provider name used in tracing and diagnostics.
   */
  default @NotNull String getName() {
    return getClass().getSimpleName();
  }

  /**
   * Collects and reports models for the supplied Gradle builds.
   * <p>
   * This method is called once per provider execution with all {@link GradleBuild} instances known to the import action. Implementations
   * should use {@code modelController} to fetch required models and then pass every model that must be available in the IDE import result
   * to {@code modelConsumer}.
   *
   * @param modelController controller for Gradle Tooling API model requests
   * @param buildModels Gradle builds participating in the import
   * @param modelConsumer consumer used to publish fetched models to the import result
   */
  default void populateModels(
    @NotNull GradleModelController modelController,
    @NotNull Collection<? extends @NotNull GradleBuild> buildModels,
    @NotNull GradleModelConsumer modelConsumer
  ) { }

  /**
   * Collects and reports models using the raw Gradle {@link BuildController}.
   *
   * @deprecated implement {@link #populateModels(GradleModelController, Collection, GradleModelConsumer)} instead.
   */
  @Deprecated
  default void populateModels(
    @NotNull BuildController controller,
    @NotNull Collection<? extends @NotNull GradleBuild> buildModels,
    @NotNull GradleModelConsumer modelConsumer
  ) { }

  /**
   * Receives models produced by a {@link ProjectImportModelProvider} and stores them in the import result.
   * <p>
   * Use {@link #consumeBuildModel(BuildModel, Object, Class)} for models associated with a Gradle build and
   * {@link #consumeProjectModel(BasicGradleProject, Object, Class)} for models associated with a Gradle project.
   * Implementations are supplied by the Gradle import action and should not be implemented by clients.
   */
  @NonExtendable
  interface GradleModelConsumer {

    /**
     * Consumes a model associated with a Gradle build.
     *
     * @param buildModel the Gradle build the model belongs to
     * @param object the fetched model instance
     * @param clazz the model type requested from the Gradle Tooling API
     */
    void consumeBuildModel(@NotNull BuildModel buildModel, @NotNull Object object, @NotNull Class<?> clazz);

    /**
     * Consumes a model associated with a Gradle project.
     *
     * @param projectModel the Gradle project the model belongs to
     * @param object the fetched model instance
     * @param clazz the model type requested from the Gradle Tooling API
     */
    void consumeProjectModel(@NotNull BasicGradleProject projectModel, @NotNull Object object, @NotNull Class<?> clazz);

    /**
     * Consumer that ignores all received models.
     */
    GradleModelConsumer NOOP = new GradleModelConsumer() {
      @Override public void consumeBuildModel(@NotNull BuildModel buildModel, @NotNull Object object, @NotNull Class<?> clazz) {}
      @Override public void consumeProjectModel(@NotNull BasicGradleProject projectModel, @NotNull Object object, @NotNull Class<?> clazz) {}
    };
  }
}
