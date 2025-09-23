// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.validator.storage;

import com.intellij.internal.statistic.eventLog.validator.DictionaryStorage;
import com.intellij.internal.statistic.eventLog.validator.rules.beans.EventGroupRules;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CompositeValidationRulesStorage implements IntellijValidationRulesStorage, ValidationTestRulesStorageHolder {
  private final @NotNull IntellijValidationRulesStorage myRulesStorage;
  private final @NotNull ValidationTestRulesPersistedStorage myTestRulesStorage;

  CompositeValidationRulesStorage(@NotNull IntellijValidationRulesStorage rulesStorage,
                                  @NotNull ValidationTestRulesPersistedStorage testRulesStorage) {
    myRulesStorage = rulesStorage;
    myTestRulesStorage = testRulesStorage;
  }

  @Override
  public @Nullable EventGroupRules getGroupRules(@NotNull String groupId) {
    final EventGroupRules testGroupRules = myTestRulesStorage.getGroupRules(groupId);
    if (testGroupRules != null) {
      return testGroupRules;
    }
    return myRulesStorage.getGroupRules(groupId);
  }

  @Override
  public boolean isUnreachable() {
    return myRulesStorage.isUnreachable() && myTestRulesStorage.isUnreachable();
  }

  @Override
  public boolean update() {
    return myRulesStorage.update() && myRulesStorage.update();
  }

  @Override
  public void reload() {
    myRulesStorage.reload();
    myTestRulesStorage.reload();
  }

  @Override
  public @NotNull ValidationTestRulesPersistedStorage getTestGroupStorage() {
    return myTestRulesStorage;
  }

  @Override
  public @Nullable DictionaryStorage getDictionaryStorage() {
    return myRulesStorage.getDictionaryStorage();
  }
}
