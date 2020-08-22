// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui.filters;

import com.intellij.structuralsearch.NamedScriptableDefinition;
import com.intellij.util.ui.table.JBTableRow;
import com.intellij.util.ui.table.JBTableRowEditor;

import javax.swing.*;

/**
 * @author Bas Leijdekkers
 */
public abstract class FilterEditor<T extends NamedScriptableDefinition> extends JBTableRowEditor {

  protected final T myConstraint;
  private final Runnable myConstraintChangedCallback;

  public FilterEditor(T constraint, Runnable constraintChangedCallback) {
    myConstraint = constraint;
    myConstraintChangedCallback = constraintChangedCallback;
  }

  @Override
  public final JBTableRow getValue() {
    saveValues();
    myConstraintChangedCallback.run();
    return __ -> this;
  }

  @Override
  public final void prepareEditor(JTable table, int row) {
    loadValues();
    layoutComponents();
  }

  protected abstract void layoutComponents();

  protected abstract void loadValues();

  protected abstract void saveValues();
}
