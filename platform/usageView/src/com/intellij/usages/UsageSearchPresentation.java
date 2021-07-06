// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import static org.jetbrains.annotations.Nls.Capitalization.Title;

@ApiStatus.Experimental
public interface UsageSearchPresentation {
  /**
   * Returns formatted psi element name: {item type} {item name} {item origin}
   * Where:
   * {item type} is capitalized type like "Method", "Local variable"
   * {item name} is element name enclosed in HTML bold attributes
   * {item origin} optional origin of the element e.g. FQN of enclosing class
   */
  @Nls(capitalization = Title) @NotNull String getSearchTargetString();

  /**
   * Search options converted to formatted string.
   */
  @Nls
  @NotNull String getOptionsString();
}
