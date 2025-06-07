// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.rules;

import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.usages.Usage;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public interface UsageInLibrary extends Usage {
  OrderEntry getLibraryEntry();

  default @NotNull List<SyntheticLibrary> getSyntheticLibraries() {
    return Collections.emptyList();
  }
}
