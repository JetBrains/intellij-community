// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.validator.storage;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

public final class ValidationRulesStorageProvider {
  public static @NotNull IntellijValidationRulesStorage newStorage(@NotNull String recorderId) {
    final IntellijValidationRulesStorage storage =
      ApplicationManager.getApplication().isUnitTestMode() ? ValidationRulesInMemoryStorage.INSTANCE : new ValidationRulesPersistedStorage(recorderId);
    if (ApplicationManager.getApplication().isInternal()) {
      return new CompositeValidationRulesStorage(storage, new ValidationTestRulesPersistedStorage(recorderId));
    }
    return storage;
  }
}
