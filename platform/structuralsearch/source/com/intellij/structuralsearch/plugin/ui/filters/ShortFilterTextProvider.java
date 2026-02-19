// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui.filters;

import com.intellij.structuralsearch.NamedScriptableDefinition;

/**
 * @author Bas Leijdekkers
 */
public interface ShortFilterTextProvider {
  String getShortFilterText(NamedScriptableDefinition variable);
}
