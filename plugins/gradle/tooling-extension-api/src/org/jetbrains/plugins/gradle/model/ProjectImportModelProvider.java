// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.model;

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.BuildModel;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.Model;
import org.gradle.tooling.model.ProjectModel;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

/**
 * Allows the {@link ProjectImportAction} to be extended to allow extra flexibility to extensions when requesting the models.
 * <p>
 * {@link #populateProjectModels(BuildController, Model, ProjectModelConsumer)} is called once for each {@link GradleProject} obtained
 * from the Gradle Tooling API (this includes projects from included builds).
 * <p>
 * {@link #populateBuildModels(BuildController, GradleBuild, BuildModelConsumer)} is called once for each {@link GradleBuild} that is
 * obtained from the Gradle Tooling API, for none-composite builds this will be called exactly once, for composite builds this will be
 * called once for each included build and once for the name build. This will always be called after
 * {@link #populateProjectModels(BuildController, Model, ProjectModelConsumer)}.
 */
public interface ProjectImportModelProvider extends Serializable {

  default GradleModelFetchPhase getPhase() {
    return GradleModelFetchPhase.ADDITIONAL_MODEL_PHASE;
  }

  default @NotNull String getName() {
    return getClass().getName();
  }

  default void populateBuildModels(
    @NotNull BuildController controller,
    @NotNull GradleBuild buildModel,
    @NotNull BuildModelConsumer consumer
  ) { }

  default void populateProjectModels(
    @NotNull BuildController controller,
    @NotNull Model projectModel,
    @NotNull ProjectModelConsumer modelConsumer
  ) { }

  interface BuildModelConsumer {

    void consume(@NotNull BuildModel buildModel, @NotNull Object object, @NotNull Class<?> clazz);

    void consumeProjectModel(@NotNull ProjectModel projectModel, @NotNull Object object, @NotNull Class<?> clazz);
  }

  interface ProjectModelConsumer {

    void consume(@NotNull Object object, @NotNull Class<?> clazz);
  }
}
