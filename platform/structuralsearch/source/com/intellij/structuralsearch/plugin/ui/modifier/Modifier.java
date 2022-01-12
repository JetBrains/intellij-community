// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.plugin.ui.modifier;

import com.intellij.structuralsearch.NamedScriptableDefinition;

import javax.swing.*;

/**
 * @author Bas Leijdekkers
 */
public interface Modifier {

  int position();

  JComponent getRenderer();

  default ModifierEditor<? extends NamedScriptableDefinition> getEditor() {
    return null;
  }
}
