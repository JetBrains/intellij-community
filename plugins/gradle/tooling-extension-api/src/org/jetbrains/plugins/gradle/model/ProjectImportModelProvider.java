// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.model;

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.BuildModel;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.Collection;

/**
 * Allows the Gradle model fetch action to be extended to allow extra flexibility to extensions when requesting the models.
 */
public interface ProjectImportModelProvider extends Serializable {

  default GradleModelFetchPhase getPhase() {
    return GradleModelFetchPhase.ADDITIONAL_MODEL_PHASE;
  }

  default @NotNull String getName() {
    return getClass().getSimpleName();
  }

  /**
   * This function is called once for all {@link GradleBuild} that is obtained from the Gradle Tooling API.
   */
  void populateModels(
    @NotNull BuildController controller,
    @NotNull Collection<? extends @NotNull GradleBuild> buildModels,
    @NotNull GradleModelConsumer modelConsumer
  );

  interface GradleModelConsumer {

    default void consumeBuildModel(@NotNull BuildModel buildModel, @NotNull Object object, @NotNull Class<?> clazz) { }

    default void consumeProjectModel(@NotNull BasicGradleProject projectModel, @NotNull Object object, @NotNull Class<?> clazz) { }

    GradleModelConsumer NOOP = new GradleModelConsumer() {
    };
  }
}
