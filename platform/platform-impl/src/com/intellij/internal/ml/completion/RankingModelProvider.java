// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ml.completion;

import com.intellij.internal.ml.DecisionFunction;
import com.intellij.lang.Language;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Provides model to predict relevance of each element in the completion popup
 */
@ApiStatus.Internal
public interface RankingModelProvider {
  @NotNull
  DecisionFunction getModel();

  @NotNull
  String getDisplayNameInSettings();

  boolean isLanguageSupported(@NotNull Language language);

  default boolean isEnabledByDefault() {
    return false;
  }
}
