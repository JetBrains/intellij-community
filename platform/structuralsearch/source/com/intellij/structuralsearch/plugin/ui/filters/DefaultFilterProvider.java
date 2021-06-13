// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui.filters;

import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class DefaultFilterProvider implements FilterProvider {

  @Override
  public List<FilterAction> getFilters() {
    return List.of(new ContextFilter(), new CountFilter(), new ReferenceFilter(), new TextFilter(), new TypeFilter());
  }
}
