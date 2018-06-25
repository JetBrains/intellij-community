// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui.filters;

import com.intellij.structuralsearch.MatchVariableConstraint;
import com.intellij.util.ui.table.JBTableRow;
import com.intellij.util.ui.table.JBTableRowEditor;

import javax.swing.*;

/**
 * @author Bas Leijdekkers
 */
public abstract class FilterEditor extends JBTableRowEditor {

  protected final MatchVariableConstraint myConstraint;

  public FilterEditor(MatchVariableConstraint constraint) {
    myConstraint = constraint;
  }

  @Override
  public final JBTableRow getValue() {
    saveValues();
    return __ -> this;
  }

  @Override
  public final void prepareEditor(JTable table, int row) {
    layoutComponents();
    loadValues();
  }

  protected abstract void layoutComponents();

  protected abstract void loadValues();

  protected abstract void saveValues();
}
