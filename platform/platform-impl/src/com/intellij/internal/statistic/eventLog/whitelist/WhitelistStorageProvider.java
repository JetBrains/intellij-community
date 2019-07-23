// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.whitelist;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentMap;

public class WhitelistStorageProvider {
  private static final ConcurrentMap<String, WhitelistGroupRulesStorage> ourInstances = ContainerUtil.newConcurrentMap();

  @NotNull
  public static WhitelistGroupRulesStorage getInstance(@NotNull String recorderId) {
    return ourInstances.computeIfAbsent(recorderId, id -> newStorage(recorderId));
  }

  @NotNull
  private static WhitelistGroupRulesStorage newStorage(@NotNull String recorderId) {
    final WhitelistGroupRulesStorage whitelist =
      ApplicationManager.getApplication().isUnitTestMode() ? InMemoryWhitelistStorage.INSTANCE : new WhitelistStorage(recorderId);
    if (ApplicationManager.getApplication().isInternal()) {
      return new CompositeWhitelistStorage(whitelist, WhitelistTestGroupStorage.getInstance(recorderId));
    }
    return whitelist;
  }
}
