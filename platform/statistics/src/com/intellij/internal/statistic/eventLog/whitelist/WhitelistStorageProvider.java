// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.whitelist;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

public final class WhitelistStorageProvider {
  @NotNull
  public static WhitelistGroupRulesStorage newStorage(@NotNull String recorderId) {
    final WhitelistGroupRulesStorage whitelist =
      ApplicationManager.getApplication().isUnitTestMode() ? InMemoryWhitelistStorage.INSTANCE : new WhitelistStorage(recorderId);
    if (ApplicationManager.getApplication().isInternal()) {
      return new CompositeWhitelistStorage(whitelist, new WhitelistTestGroupStorage(recorderId));
    }
    return whitelist;
  }
}
