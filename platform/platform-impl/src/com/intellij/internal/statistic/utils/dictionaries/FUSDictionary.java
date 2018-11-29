// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.utils.dictionaries;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public class FUSDictionary {
  private final String myVersion;
  private final Map<String, List<String>> myContents;

  public FUSDictionary(@NotNull String version, @NotNull Map<String, List<String>> contents) {
    myVersion = version;
    myContents = ContainerUtil.unmodifiableOrEmptyMap(contents);
  }

  @NotNull
  public String getVersion() {
    return myVersion;
  }

  @NotNull
  public Map<String, List<String>> getContents() {
    return myContents;
  }
}