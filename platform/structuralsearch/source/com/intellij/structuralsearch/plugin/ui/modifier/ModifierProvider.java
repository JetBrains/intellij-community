// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.plugin.ui.modifier;

import com.intellij.openapi.extensions.ExtensionPointName;

import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public interface ModifierProvider {
  ExtensionPointName<ModifierProvider> EP_NAME = ExtensionPointName.create("com.intellij.structuralsearch.modifierProvider");

  List<ModifierAction> getModifiers();
}
