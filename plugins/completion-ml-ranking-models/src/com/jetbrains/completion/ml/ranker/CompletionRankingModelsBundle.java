package com.jetbrains.completion.ml.ranker;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class CompletionRankingModelsBundle {
  private static final String COMPLETION_RANKING_MODELS_BUNDLE = "messages.CompletionRankingModelsBundle";

  public static @Nls String message(@NotNull @PropertyKey(resourceBundle = COMPLETION_RANKING_MODELS_BUNDLE) String key,
                                    Object @NotNull ... params) {
    return ourInstance.getMessage(key, params);
  }

  private static final DynamicBundle ourInstance = new DynamicBundle(CompletionRankingModelsBundle.class, COMPLETION_RANKING_MODELS_BUNDLE);
}
