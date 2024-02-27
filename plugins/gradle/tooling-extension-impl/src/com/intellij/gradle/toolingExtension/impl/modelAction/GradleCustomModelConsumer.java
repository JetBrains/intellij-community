// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.modelAction;

import com.intellij.gradle.toolingExtension.impl.modelSerialization.ModelConverter;
import io.opentelemetry.context.Context;
import org.gradle.tooling.model.BuildModel;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider;

import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class GradleCustomModelConsumer implements ProjectImportModelProvider.GradleModelConsumer {

  private final @NotNull AllModels myAllModels;
  private final @NotNull ModelConverter myModelConverter;
  private final @NotNull ExecutorService myModelConverterExecutor;

  GradleCustomModelConsumer(
    @NotNull AllModels allModels,
    @NotNull ModelConverter modelConverter,
    @NotNull ExecutorService modelConverterExecutor
  ) {
    myAllModels = allModels;
    myModelConverter = modelConverter;
    myModelConverterExecutor = modelConverterExecutor;
  }

  @Override
  public void consumeProjectModel(
    @NotNull BasicGradleProject projectModel,
    @NotNull Object object,
    @NotNull Class<?> clazz
  ) {
    convertModel(object, converted ->
      myAllModels.addModel(converted, clazz, projectModel)
    );
  }

  @Override
  public void consumeBuildModel(
    @NotNull BuildModel buildModel,
    @NotNull Object object,
    @NotNull Class<?> clazz
  ) {
    convertModel(object, converted ->
      myAllModels.addModel(converted, clazz, buildModel)
    );
  }

  private void convertModel(@NotNull Object object, @NotNull Consumer<Object> action) {
    Context.current()
      .wrap(myModelConverterExecutor)
      .execute(() -> {
        Object converted = myModelConverter.convert(object);
        action.accept(converted);
      });
  }
}
