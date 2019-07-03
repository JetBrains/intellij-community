// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.whitelist;

import com.intellij.internal.statistic.eventLog.validator.rules.beans.WhiteListGroupRules;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

public class MergedWhiteListStorage implements WhiteListGroupRulesStorage {
  private static final ConcurrentMap<String, MergedWhiteListStorage> instances = ContainerUtil.newConcurrentMap();

  private final WhiteListGroupRulesStorage myWhiteListStorage;
  private final WhiteListGroupRulesStorage myWhiteListStorageForTest;

  public MergedWhiteListStorage(WhiteListGroupRulesStorage whiteListStorage,
                                WhiteListGroupRulesStorage whiteListStorageForTest) {
    myWhiteListStorage = whiteListStorage;
    myWhiteListStorageForTest = whiteListStorageForTest;
  }

  @NotNull
  public static MergedWhiteListStorage getInstance(@NotNull String recorderId) {
    WhiteListStorage whiteListStorage = WhiteListStorage.getInstance(recorderId);
    WhiteListStorageForTest whiteListStorageForTest = WhiteListStorageForTest.getInstance(recorderId);

    return instances.computeIfAbsent(recorderId, id -> new MergedWhiteListStorage(whiteListStorage, whiteListStorageForTest));
  }

  @NotNull
  @Override
  public Map<String, WhiteListGroupRules> getEventsValidators() {
    Map<String, WhiteListGroupRules> validators = new HashMap<>(myWhiteListStorage.getEventsValidators());
    validators.putAll(myWhiteListStorageForTest.getEventsValidators());
    return validators;
  }

  @Override
  public boolean isUnreachableWhitelist() {
    return myWhiteListStorage.isUnreachableWhitelist() && myWhiteListStorageForTest.isUnreachableWhitelist();
  }

}
