/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.tooling;

import org.gradle.api.Project;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

/**
 * @author Vladislav.Soroka
 */
public interface ModelBuilderService extends Serializable {

  /**
   * A {@link ParameterizedToolingModelBuilder}'s parameter interface to be used when requesting
   * a parametrized model provided by an implementation of {@link ModelBuilderService.Ex}.
   *
   * The string value set via {@link Parameter#setValue(String)} will be passed to the model builder
   * via the {@link ModelBuilderContext#getParameter()}.
   */
  interface Parameter {
    String getValue();
    void setValue(String value);
  }

  interface Ex extends ModelBuilderService {
    Object buildAll(String modelName, Project project, @NotNull ModelBuilderContext context);
  }

  boolean canBuild(String modelName);

  Object buildAll(String modelName, Project project);

  @NotNull
  ErrorMessageBuilder getErrorMessageBuilder(@NotNull Project project, @NotNull Exception e);
}
