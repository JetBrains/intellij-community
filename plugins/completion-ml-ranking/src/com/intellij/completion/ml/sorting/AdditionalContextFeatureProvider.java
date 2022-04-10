// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.sorting;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

@ApiStatus.Internal
public interface AdditionalContextFeatureProvider {
  LanguageExtension<AdditionalContextFeatureProvider> EP_NAME = new LanguageExtension<>("com.intellij.completion.ml.additionalContextFeatures");

  @NotNull
  static List<AdditionalContextFeatureProvider> forLanguage(Language language) {
    return EP_NAME.allForLanguageOrAny(language);
  }

  @NotNull
  Map<String, String> calculateFeatures(@NotNull Map<String, String> contextFeatures);
}
