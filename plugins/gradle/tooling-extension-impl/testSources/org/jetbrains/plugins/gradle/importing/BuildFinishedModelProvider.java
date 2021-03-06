// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing;

import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.Model;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider;
import org.jetbrains.plugins.gradle.tooling.builder.ProjectPropertiesTestModelBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class BuildFinishedModelProvider implements ProjectImportModelProvider {
  @Override
  public void populateBuildModels(@NotNull BuildController controller,
                                  @NotNull GradleBuild buildModel,
                                  @NotNull ProjectImportModelProvider.BuildModelConsumer consumer) { }

  @Override
  public void populateProjectModels(@NotNull BuildController controller,
                                    @NotNull Model projectModel,
                                    @NotNull ProjectImportModelProvider.ProjectModelConsumer modelConsumer) {
    ProjectPropertiesTestModelBuilder.ProjectProperties model =
      controller.getModel(projectModel, ProjectPropertiesTestModelBuilder.ProjectProperties.class);
    Map<String, String> propertiesMap = new HashMap<>(model.getPropertiesMap());
    for (String key : new ArrayList<>(propertiesMap.keySet())) {
      if (!key.equals("name") && !key.startsWith("prop_finished_")) {
        propertiesMap.remove(key);
      }
    }
    modelConsumer.consume(new BuildFinishedModel(propertiesMap), BuildFinishedModel.class);
  }
}
