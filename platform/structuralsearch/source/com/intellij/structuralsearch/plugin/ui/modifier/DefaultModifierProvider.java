// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.plugin.ui.modifier;

import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class DefaultModifierProvider implements ModifierProvider {

  @Override
  public List<ModifierAction> getModifiers() {
    return List.of(new ContextModifier(), new CountModifier(), new ReferenceModifier(), new TextModifier(), new TypeModifier());
  }
}
