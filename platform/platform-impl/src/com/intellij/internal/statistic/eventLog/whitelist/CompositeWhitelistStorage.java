// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.whitelist;

import com.intellij.internal.statistic.eventLog.validator.rules.beans.WhiteListGroupRules;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentMap;

public class CompositeWhitelistStorage implements WhitelistGroupRulesStorage {
  private static final ConcurrentMap<String, CompositeWhitelistStorage> ourInstances = ContainerUtil.newConcurrentMap();

  @NotNull
  private final WhitelistGroupRulesStorage myWhiteListStorage;
  @NotNull
  private final WhitelistGroupRulesStorage myWhiteListStorageForTest;

  private CompositeWhitelistStorage(@NotNull WhitelistGroupRulesStorage whiteListStorage,
                                    @NotNull WhitelistGroupRulesStorage whiteListStorageForTest) {
    myWhiteListStorage = whiteListStorage;
    myWhiteListStorageForTest = whiteListStorageForTest;
  }

  @NotNull
  public static CompositeWhitelistStorage getInstance(@NotNull String recorderId) {
    WhitelistGroupRulesStorage whiteListStorage =
      ApplicationManager.getApplication().isUnitTestMode() ? InMemoryWhitelistStorage.INSTANCE : WhitelistStorage.getInstance(recorderId);
    WhitelistStorageForTest whiteListStorageForTest = WhitelistStorageForTest.getInstance(recorderId);

    return ourInstances.computeIfAbsent(recorderId, id -> new CompositeWhitelistStorage(whiteListStorage, whiteListStorageForTest));
  }

  @Nullable
  @Override
  public WhiteListGroupRules getGroupRules(@NotNull String groupId) {
    WhiteListGroupRules testGroupRules = myWhiteListStorageForTest.getGroupRules(groupId);
    if (testGroupRules != null) return testGroupRules;
    return myWhiteListStorage.getGroupRules(groupId);
  }

  @Override
  public boolean isUnreachableWhitelist() {
    return myWhiteListStorage.isUnreachableWhitelist() && myWhiteListStorageForTest.isUnreachableWhitelist();
  }

}
