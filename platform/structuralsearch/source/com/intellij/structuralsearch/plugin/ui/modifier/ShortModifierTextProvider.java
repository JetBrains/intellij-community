// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.plugin.ui.modifier;

import com.intellij.structuralsearch.NamedScriptableDefinition;

/**
 * @author Bas Leijdekkers
 */
public interface ShortModifierTextProvider {
  String getShortModifierText(NamedScriptableDefinition variable);
}
