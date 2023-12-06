// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.builder;

import org.gradle.api.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.tooling.Message;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService;

import java.io.Serializable;

public class FailingTestModelBuilder implements ModelBuilderService {
  @Override
  public boolean canBuild(String modelName) {
    return Model.class.getName().equals(modelName);
  }

  @Override
  public Object buildAll(String modelName, Project project) {
    throw new RuntimeException("Boom! '\"{}}\n\t");
  }

  @Override
  public void reportErrorMessage(
    @NotNull String modelName,
    @NotNull Project project,
    @NotNull ModelBuilderContext context,
    @NotNull Exception exception
  ) {
    context.getMessageReporter().createMessage()
      .withGroup("gradle.test.group")
      .withKind(Message.Kind.ERROR)
      .withTitle("Test import errors")
      .withText("Unable to import Test model")
      .withException(exception)
      .reportMessage(project);
  }

  public interface Model extends Serializable {
  }
}
