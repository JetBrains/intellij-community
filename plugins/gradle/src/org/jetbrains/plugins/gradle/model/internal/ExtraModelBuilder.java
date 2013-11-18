/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.gradle.model.internal;

import org.gradle.api.Project;
import org.gradle.tooling.provider.model.ToolingModelBuilder;
import org.jetbrains.plugins.gradle.model.ModelBuilderService;

import java.util.ServiceLoader;

/**
 * @author Vladislav.Soroka
 * @since 11/5/13
 */
@SuppressWarnings("UnusedDeclaration")
public class ExtraModelBuilder implements ToolingModelBuilder {
  private static ServiceLoader<ModelBuilderService> buildersLoader =
    ServiceLoader.load(ModelBuilderService.class, ExtraModelBuilder.class.getClassLoader());

  @Override
  public boolean canBuild(String modelName) {
    for (ModelBuilderService service : buildersLoader) {
      if (service.canBuild(modelName)) return true;
    }
    return false;
  }

  @Override
  public Object buildAll(String modelName, Project project) {
    for (ModelBuilderService service : buildersLoader) {
      if (service.canBuild(modelName)) {
        return service.buildAll(modelName, project);
      }
    }
    throw new IllegalArgumentException("Unsupported model: " + modelName);
  }
}
