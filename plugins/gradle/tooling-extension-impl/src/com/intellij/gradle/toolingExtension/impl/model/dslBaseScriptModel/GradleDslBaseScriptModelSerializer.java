// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.dslBaseScriptModel;

import com.intellij.gradle.toolingExtension.impl.modelSerialization.DefaultSerializationService;
import org.gradle.tooling.model.dsl.GradleDslBaseScriptModel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.plugins.gradle.tooling.serialization.SerializationService;

import java.io.IOException;

@ApiStatus.Internal
public final class GradleDslBaseScriptModelSerializer implements SerializationService<GradleDslBaseScriptModel> {

  private final SerializationService<GradleDslBaseScriptModel> delegate = new DefaultSerializationService();

  @Override
  public byte[] write(GradleDslBaseScriptModel object, Class<? extends GradleDslBaseScriptModel> modelClazz) throws IOException {
    return delegate.write(object, modelClazz);
  }

  @Override
  public GradleDslBaseScriptModel read(byte[] object, Class<? extends GradleDslBaseScriptModel> modelClazz) throws IOException {
    return delegate.read(object, modelClazz);
  }

  @Override
  public Class<? extends GradleDslBaseScriptModel> getModelClass() {
    return GradleDslBaseScriptModel.class;
  }
}
