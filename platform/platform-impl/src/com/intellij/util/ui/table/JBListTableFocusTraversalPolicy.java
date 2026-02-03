// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.table;

import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Arrays;
import java.util.List;

/**
* @author Konstantin Bulenkov
*/
final class JBListTableFocusTraversalPolicy extends ComponentsListFocusTraversalPolicy {
  private final JBTableRowEditor myEditor;

  JBListTableFocusTraversalPolicy(JBTableRowEditor editor) {
    myEditor = editor;
  }

  @Override
  public Component getDefaultComponent(Container aContainer) {
    return myEditor.getPreferredFocusedComponent();
  }

  @Override
  protected @NotNull List<Component> getOrderedComponents() {
    return Arrays.asList(myEditor.getFocusableComponents());
  }
}
