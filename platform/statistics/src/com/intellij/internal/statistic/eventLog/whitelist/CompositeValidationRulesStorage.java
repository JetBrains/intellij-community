// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.whitelist;

import com.intellij.internal.statistic.eventLog.validator.rules.beans.EventGroupRules;
import com.intellij.internal.statistic.eventLog.whitelist.ValidationRulesStorage;
import com.intellij.internal.statistic.eventLog.whitelist.ValidationTestRulesPersistedStorage;
import com.intellij.internal.statistic.eventLog.whitelist.ValidationTestRulesStorageHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CompositeValidationRulesStorage implements ValidationRulesStorage, ValidationTestRulesStorageHolder {
  @NotNull
  private final ValidationRulesStorage myRulesStorage;
  @NotNull
  private final ValidationTestRulesPersistedStorage myTestRulesStorage;

  CompositeValidationRulesStorage(@NotNull ValidationRulesStorage rulesStorage,
                                  @NotNull ValidationTestRulesPersistedStorage testRulesStorage) {
    myRulesStorage = rulesStorage;
    myTestRulesStorage = testRulesStorage;
  }

  @Nullable
  @Override
  public EventGroupRules getGroupRules(@NotNull String groupId) {
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
  public void update() {
    myRulesStorage.update();
    myTestRulesStorage.update();
  }

  @Override
  public void reload() {
    myRulesStorage.reload();
    myTestRulesStorage.reload();
  }

  @NotNull
  @Override
  public ValidationTestRulesPersistedStorage getTestGroupStorage() {
    return myTestRulesStorage;
  }
}
