// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.i18n.batch;

import org.jetbrains.annotations.NotNull;

public record I18nizedPropertyData<D>(@NotNull String key, @NotNull String value, @NotNull D contextData, boolean markAsNonNls) {
  public I18nizedPropertyData(@NotNull String key, @NotNull String value, @NotNull D contextData) {
    this(key, value, contextData, false);
  }

  public I18nizedPropertyData<D> changeKey(@NotNull String newKey) {
    return new I18nizedPropertyData<>(newKey, value, contextData, markAsNonNls);
  }

  public I18nizedPropertyData<D> setMarkAsNonNls(boolean markAsNonNls) {
    return new I18nizedPropertyData<>(key, value, contextData, markAsNonNls);
  }
}
