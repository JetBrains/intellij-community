// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.utils.dictionaries;

import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public interface FUSDictionariesService {
  static FUSDictionariesService getInstance() {
    return ServiceManager.getService(FUSDictionariesService.class);
  }

  @Nullable
  FUSDictionary getDictionary(@NotNull String id);

  void asyncPreloadDictionaries(@NotNull Set<String> ids);
}
