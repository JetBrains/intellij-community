// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.table;

import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.ui.EditorTextFieldCellRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class EditorTextFieldJBTableRowRenderer extends EditorTextFieldCellRenderer implements JBTableRowRenderer {
  /** @deprecated Use {@link EditorTextFieldJBTableRowRenderer#EditorTextFieldJBTableRowRenderer(Project, Language, Disposable)}*/
  @Deprecated(forRemoval = true)
  protected EditorTextFieldJBTableRowRenderer(@Nullable Project project, @Nullable FileType fileType, @NotNull Disposable parent) {
    super(project, fileType, parent);
  }

  protected EditorTextFieldJBTableRowRenderer(@Nullable Project project, @Nullable Language language, @NotNull Disposable parent) {
    super(project, language, parent);
  }

  protected EditorTextFieldJBTableRowRenderer(@Nullable Project project, @NotNull Disposable parent) {
    super(project, (Language)null, parent);
  }

  @Override
  public final JComponent getRowRendererComponent(JTable table, int row, boolean selected, boolean focused) {
    return (JComponent)getTableCellRendererComponent(table, null, selected, focused, row, 0);
  }

  @Override
  protected final String getText(JTable table, Object value, int row, int column) {
    return getText(table, row);
  }

  @Override
  protected final @Nullable TextAttributes getTextAttributes(JTable table, Object value, int row, int column) {
    return getTextAttributes(table, row);
  }

  protected abstract String getText(JTable table, int row);

  protected @Nullable TextAttributes getTextAttributes(JTable table, int row) {
    return null;
  }
}
