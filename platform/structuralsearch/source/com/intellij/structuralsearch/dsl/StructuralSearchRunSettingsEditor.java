// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.dsl;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Bas Leijdekkers
 */
public class StructuralSearchRunSettingsEditor extends SettingsEditor<StructuralSearchRunConfiguration> {
  @Override
  protected void resetEditorFrom(@NotNull StructuralSearchRunConfiguration s) {

  }

  @Override
  protected void applyEditorTo(@NotNull StructuralSearchRunConfiguration s) throws ConfigurationException {

  }

  @NotNull
  @Override
  protected JComponent createEditor() {
    return new JPanel();
  }
}
