// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.storage;

import com.intellij.internal.statistic.eventLog.EventLogBuild;
import com.intellij.internal.statistic.eventLog.validator.ValidationRuleStorage;
import com.intellij.internal.statistic.eventLog.validator.GroupValidators;
import com.intellij.internal.statistic.eventLog.validator.rules.beans.EventGroupRules;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IntellijValidationRulesStorage extends ValidationRuleStorage<EventLogBuild> {
  @Nullable EventGroupRules getGroupRules(@NotNull String groupId);

  /**
   * Loads and updates events scheme from the server if necessary
   */
  void update();

  /**
   * Re-loads events scheme from local caches
   */
  void reload();

  @Override
  default @NotNull GroupValidators<EventLogBuild> getGroupValidators(@NotNull String groupId) {
    return new GroupValidators<>(getGroupRules(groupId), null);
  }
}
