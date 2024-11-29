// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing;

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider;

import java.util.Collection;

/**
 * @see org.jetbrains.plugins.gradle.importing.GradleActionWithImportTest#test start tasks can be set by model builder and run on import()
 */
public class TestBuildObjectModelProvider implements ProjectImportModelProvider {

  @Override
  public GradleModelFetchPhase getPhase() {
    return GradleModelFetchPhase.PROJECT_LOADED_PHASE;
  }

  @Override
  public void populateModels(
    @NotNull BuildController controller,
    @NotNull Collection<? extends @NotNull GradleBuild> buildModels,
    @NotNull GradleModelConsumer modelConsumer
  ) {
    // used as a trigger for a ModelBuilder which accepts Object and configure build
    // the model instance is not used somehow further
    //noinspection unused
    Object model = controller.findModel(Object.class);
  }
}
