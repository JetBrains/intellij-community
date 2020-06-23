// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import static org.jetbrains.annotations.Nls.Capitalization.Title;

@ApiStatus.Experimental
public interface UsageSearchPresentation {

  /**
   * <ul>
   *   <li>the string must not include the search scope</li>
   *   <li>the string must include what results are searched and the target,
   *   for example <i>Usages and Implementations of Method 'foo'</i></li>
   * </ul>
   */
  @Nls(capitalization = Title) @NotNull String getSearchString();
}
