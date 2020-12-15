package com.intellij.completion.ml.local;

import com.intellij.AbstractBundle;
import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public class CompletionRankingLocalBundle extends DynamicBundle {
  private static final String COMPLETION_RANKING_LOCAL_BUNDLE = "messages.CompletionRankingLocalBundle";

  public static @Nls String message(@NotNull @PropertyKey(resourceBundle = COMPLETION_RANKING_LOCAL_BUNDLE) String key, Object @NotNull ... params) {
    return ourInstance.getMessage(key, params);
  }

  private static final AbstractBundle ourInstance = new CompletionRankingLocalBundle();

  protected CompletionRankingLocalBundle() {
    super(COMPLETION_RANKING_LOCAL_BUNDLE);
  }
}
