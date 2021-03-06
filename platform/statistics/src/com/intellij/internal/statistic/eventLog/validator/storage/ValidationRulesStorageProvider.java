// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.storage;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

public final class ValidationRulesStorageProvider {
  @NotNull
  public static IntellijValidationRulesStorage newStorage(@NotNull String recorderId) {
    final IntellijValidationRulesStorage storage =
      ApplicationManager.getApplication().isUnitTestMode() ? ValidationRulesInMemoryStorage.INSTANCE : new ValidationRulesPersistedStorage(recorderId);
    if (ApplicationManager.getApplication().isInternal()) {
      return new CompositeValidationRulesStorage(storage, new ValidationTestRulesPersistedStorage(recorderId));
    }
    return storage;
  }
}
