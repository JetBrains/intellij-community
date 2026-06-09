// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.dslBaseScriptModel;

import com.intellij.gradle.toolingExtension.impl.modelAction.GradleModelHolderState;
import com.intellij.gradle.toolingExtension.impl.modelAction.GradleModelId;
import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase;
import org.gradle.tooling.model.dsl.GradleDslBaseScriptModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.dsl.InternalGradleDslBaseScriptModel;

import java.util.Collections;
import java.util.Map;

/**
 * This hack is required to make sure that the instance of GradleDslBaseScriptModel could be serialized and deserialized on the
 * Gradle Tooling Proxy side.
 */
public final class GradleDslBaseScriptModelHolder extends GradleModelHolderState {

  private GradleDslBaseScriptModelHolder(@NotNull Map<@NotNull GradleModelId, @NotNull Object> models) {
    super(null, Collections.emptyList(), models, GradleModelFetchPhase.BASE_SCRIPT_MODEL_PHASE);
  }

  public static @NotNull GradleDslBaseScriptModelHolder wrap(@Nullable GradleDslBaseScriptModel model) {
    if (model == null) {
      return new GradleDslBaseScriptModelHolder(Collections.emptyMap());
    }
    GradleModelId modelId = GradleModelId.createRootModelId(GradleDslBaseScriptModel.class);
    GradleDslBaseScriptModel convertedModel = InternalGradleDslBaseScriptModel.convertDslBaseScriptModel(model);
    Map<GradleModelId, Object> models = Collections.singletonMap(modelId, convertedModel);
    return new GradleDslBaseScriptModelHolder(models);
  }
}
