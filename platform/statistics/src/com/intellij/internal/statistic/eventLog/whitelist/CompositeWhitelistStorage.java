// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.whitelist;

import com.intellij.internal.statistic.eventLog.validator.rules.beans.WhiteListGroupRules;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CompositeWhitelistStorage implements WhitelistGroupRulesStorage {
  @NotNull
  private final WhitelistGroupRulesStorage myWhitelistStorage;
  @NotNull
  private final WhitelistGroupRulesStorage myWhitelistTestGroupStorage;

  CompositeWhitelistStorage(@NotNull WhitelistGroupRulesStorage whitelistStorage,
                            @NotNull WhitelistGroupRulesStorage testWhitelistStorage) {
    myWhitelistStorage = whitelistStorage;
    myWhitelistTestGroupStorage = testWhitelistStorage;
  }

  @Nullable
  @Override
  public WhiteListGroupRules getGroupRules(@NotNull String groupId) {
    final WhiteListGroupRules testGroupRules = myWhitelistTestGroupStorage.getGroupRules(groupId);
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
}
