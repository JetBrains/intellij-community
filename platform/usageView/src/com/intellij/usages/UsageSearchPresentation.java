// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public interface UsageSearchPresentation {

  /**
   * @return string displayed in the Show Usages action popup title and Find Usages action usage view tab;
   * the string must include what results are searched, the target and search scope,
   * for example "Usages and implementations of method 'foo' in 'Project and Libraries'"
   */
  @Nls(capitalization = Nls.Capitalization.Title) @NotNull String getSearchTitle();
}
