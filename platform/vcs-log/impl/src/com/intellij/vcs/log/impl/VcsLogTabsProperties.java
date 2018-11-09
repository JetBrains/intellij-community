// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl;

import org.jetbrains.annotations.NotNull;

public interface VcsLogTabsProperties {
  @NotNull
  MainVcsLogUiProperties createProperties(@NotNull String id);
}
