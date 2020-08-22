// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.whitelist;

import com.intellij.internal.statistic.eventLog.validator.rules.beans.EventGroupRules;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CompositeWhitelistStorage implements WhitelistGroupRulesStorage, WhitelistTestRulesStorageHolder {
  @NotNull
  private final WhitelistGroupRulesStorage myWhitelistStorage;
  @NotNull
  private final WhitelistTestGroupStorage myWhitelistTestGroupStorage;

  CompositeWhitelistStorage(@NotNull WhitelistGroupRulesStorage whitelistStorage,
                            @NotNull WhitelistTestGroupStorage testWhitelistStorage) {
    myWhitelistStorage = whitelistStorage;
    myWhitelistTestGroupStorage = testWhitelistStorage;
  }

  @Nullable
  @Override
  public EventGroupRules getGroupRules(@NotNull String groupId) {
    final EventGroupRules testGroupRules = myWhitelistTestGroupStorage.getGroupRules(groupId);
    if (testGroupRules != null) {
      return testGroupRules;
    }
    return myWhitelistStorage.getGroupRules(groupId);
  }

  @Override
  public boolean isUnreachableWhitelist() {
    return myWhitelistStorage.isUnreachableWhitelist() && myWhitelistTestGroupStorage.isUnreachableWhitelist();
  }

  @Override
  public void update() {
    myWhitelistStorage.update();
    myWhitelistTestGroupStorage.update();
  }

  @Override
  public void reload() {
    myWhitelistStorage.reload();
    myWhitelistTestGroupStorage.reload();
  }

  @NotNull
  @Override
  public WhitelistTestGroupStorage getTestGroupStorage() {
    return myWhitelistTestGroupStorage;
  }
}
