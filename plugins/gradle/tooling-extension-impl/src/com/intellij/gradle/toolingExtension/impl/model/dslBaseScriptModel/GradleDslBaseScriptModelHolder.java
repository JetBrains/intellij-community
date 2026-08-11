// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.dslBaseScriptModel;

import com.intellij.gradle.toolingExtension.impl.modelAction.GradleModelHolderState;
import com.intellij.gradle.toolingExtension.impl.modelAction.GradleModelId;
import com.intellij.gradle.toolingExtension.impl.modelSerialization.SerializationServiceNotFoundException;
import com.intellij.gradle.toolingExtension.impl.modelSerialization.ToolingSerializer;
import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase;
import com.intellij.util.ArrayUtilRt;
import org.gradle.tooling.model.dsl.GradleDslBaseScriptModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.dsl.InternalGradleDslBaseScriptModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * This hack is required to make sure that the instance of GradleDslBaseScriptModel could be serialized and deserialized on the
 * Gradle Tooling Proxy side.
 */
public final class GradleDslBaseScriptModelHolder extends GradleModelHolderState {

  private static final Logger LOG = LoggerFactory.getLogger("com.intellij.gradle.toolingExtension.impl.model.dslBaseScriptModel");

  private GradleDslBaseScriptModelHolder(@NotNull Map<@NotNull GradleModelId, @NotNull Object> models) {
    super(null, Collections.emptyList(), models, GradleModelFetchPhase.BASE_SCRIPT_MODEL_PHASE);
  }

  public static @NotNull GradleDslBaseScriptModelHolder wrap(@Nullable GradleDslBaseScriptModel model) {
    if (model == null) {
      return new GradleDslBaseScriptModelHolder(Collections.emptyMap());
    }
    GradleModelId modelId = GradleModelId.createRootModelId(GradleDslBaseScriptModel.class);
    byte[] convertedModel = convertModel(model);
    if (convertedModel.length == 0) {
      return new GradleDslBaseScriptModelHolder(Collections.emptyMap());
    }
    Map<GradleModelId, Object> models = Collections.singletonMap(modelId, convertedModel);
    return new GradleDslBaseScriptModelHolder(models);
  }

  private static byte[] convertModel(@NotNull GradleDslBaseScriptModel model) {
    GradleDslBaseScriptModel convertedModel = InternalGradleDslBaseScriptModel.convertDslBaseScriptModel(model);
    try {
      return new ToolingSerializer().write(convertedModel);
    }
    catch (SerializationServiceNotFoundException e) {
      throw new IllegalStateException("Unable to find a serializer for InternalGradleDslBaseScriptModel", e);
    }
    catch (IOException e) {
      LOG.error("Unable to convert InternalGradleDslBaseScriptModel", e);
      return ArrayUtilRt.EMPTY_BYTE_ARRAY;
    }
  }
}
