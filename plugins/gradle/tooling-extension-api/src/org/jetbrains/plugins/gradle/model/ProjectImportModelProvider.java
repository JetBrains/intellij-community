// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.model;

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.BuildModel;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.Collection;

/**
 * Allows the Gradle model fetch action to be extended to allow extra flexibility to extensions when requesting the models.
 * <p>
 * {@link #populateProjectModels} is called once for each {@link GradleProject} obtained
 * from the Gradle Tooling API (this includes projects from included builds).
 * <p>
 * {@link #populateBuildModels} is called once for each {@link GradleBuild} that is
 * obtained from the Gradle Tooling API, for none-composite builds this will be called exactly once, for composite builds this will be
 * called once for each included build and once for the name build. This will always be called after {@link #populateProjectModels}.
 * <p>
 * {@link #populateModels} is called once for all {@link GradleBuild} that is obtained from the Gradle Tooling API.
 */
public interface ProjectImportModelProvider extends Serializable {

  default GradleModelFetchPhase getPhase() {
    return GradleModelFetchPhase.ADDITIONAL_MODEL_PHASE;
  }

  default @NotNull String getName() {
    return getClass().getSimpleName();
  }

  default void populateModels(
    @NotNull BuildController controller,
    @NotNull Collection<? extends GradleBuild> buildModels,
    @NotNull GradleModelConsumer modelConsumer
  ) { }

  default void populateBuildModels(
    @NotNull BuildController controller,
    @NotNull GradleBuild buildModel,
    @NotNull GradleModelConsumer modelConsumer
  ) { }

  default void populateProjectModels(
    @NotNull BuildController controller,
    @NotNull BasicGradleProject projectModel,
    @NotNull GradleModelConsumer modelConsumer
  ) { }

  interface GradleModelConsumer {

    default void consumeBuildModel(@NotNull BuildModel buildModel, @NotNull Object object, @NotNull Class<?> clazz) { }

    default void consumeProjectModel(@NotNull BasicGradleProject projectModel, @NotNull Object object, @NotNull Class<?> clazz) { }

    GradleModelConsumer NOOP = new GradleModelConsumer() {
    };
  }
}
