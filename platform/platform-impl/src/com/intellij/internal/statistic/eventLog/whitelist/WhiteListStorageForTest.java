// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.whitelist;

import com.intellij.internal.statistic.eventLog.validator.rules.beans.WhiteListGroupRules;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentMap;

public class WhiteListStorageForTest implements WhiteListGroupRulesStorage{
  private static final ConcurrentMap<String, WhiteListStorageForTest> instances = ContainerUtil.newConcurrentMap();

  @NotNull
  public static WhiteListStorageForTest getInstance(@NotNull String recorderId) {
    return instances.computeIfAbsent(recorderId, id -> new WhiteListStorageForTest());
  }

  @NotNull
  @Override
  public Map<String, WhiteListGroupRules> getEventsValidators() {
    return null;
  }

  @Override
  public boolean isUnreachableWhitelist() {
    return false;
  }

  @Override
  public void update() {

  }

  @Override
  public void reload() {

  }


}
