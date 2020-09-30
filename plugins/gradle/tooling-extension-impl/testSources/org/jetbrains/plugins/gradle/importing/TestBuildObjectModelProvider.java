// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing;

import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.Model;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider;

/**
 * @see org.jetbrains.plugins.gradle.importing.GradleActionWithImportTest#test start tasks can be set by model builder and run on import()
 */
public class TestBuildObjectModelProvider implements ProjectImportModelProvider {
  @Override
  public void populateBuildModels(@NotNull BuildController controller,
                                  @NotNull GradleBuild buildModel,
                                  @NotNull ProjectImportModelProvider.BuildModelConsumer consumer) {
    // used as a trigger for a ModelBuilder which accepts Object and configure build
    // the model instance is not used somehow further
    //noinspection unused
    Object model = controller.findModel(Object.class);
  }

  @Override
  public void populateProjectModels(@NotNull BuildController controller,
                                    @NotNull Model projectModel,
                                    @NotNull ProjectImportModelProvider.ProjectModelConsumer modelConsumer) { }
}
