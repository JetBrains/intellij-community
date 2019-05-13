// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.rules;

import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.usages.Usage;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author max
 */
public interface UsageInLibrary extends Usage {
  OrderEntry getLibraryEntry();

  @NotNull
  default List<SyntheticLibrary> getSyntheticLibraries() {
    return Collections.emptyList();
  }
}
