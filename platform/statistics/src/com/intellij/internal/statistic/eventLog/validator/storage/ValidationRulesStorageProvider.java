// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.validator.storage;

import com.intellij.internal.statistic.eventLog.validator.DictionaryStorage;
import com.intellij.internal.statistic.eventLog.validator.rules.beans.EventGroupRules;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ValidationRulesStorageProvider {
  /**
   * @deprecated Do not use this. Metadata/dictionary storage is handled internally by ap-validation library.
   */
  @Deprecated
  public static @NotNull IntellijValidationRulesStorage newStorage(@NotNull String recorderId) {
    return new IntellijValidationRulesStorage() {
      @Override
      public @Nullable EventGroupRules getGroupRules(@NotNull String groupId) { return EventGroupRules.EMPTY; }

      @Override
      public boolean update() { return false; }

      @Override
      public void reload() {}

      @Override
      public @Nullable DictionaryStorage getDictionaryStorage() { return null; }

      @Override
      public boolean isUnreachable() { return true; }
    };
  }
}
