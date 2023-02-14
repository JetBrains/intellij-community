// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui.filters;

import com.intellij.openapi.extensions.ExtensionPointName;

import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public interface FilterProvider {
  ExtensionPointName<FilterProvider> EP_NAME = ExtensionPointName.create("com.intellij.structuralsearch.filterProvider");

  List<FilterAction> getFilters();
}
