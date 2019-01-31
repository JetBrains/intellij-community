// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.cachedValueProfiler;

import com.intellij.execution.filters.ExceptionFilter;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.impl.EditorHyperlinkSupport;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.EditorTextField;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.util.List;

class CVPTableCellEditor extends AbstractCellEditor implements TableCellEditor {
  private final Project myProject;
  private Document myDocument;

  CVPTableCellEditor(@NotNull Project project) {myProject = project;}

  @Override
  public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
    Document document = EditorFactory.getInstance().createDocument((String)value);
    EditorTextField textField = new EditorTextField(document, myProject, FileTypes.PLAIN_TEXT, true, true) {
      @Override
      protected boolean shouldHaveBorder() {
        return false;
      }

      @Override
      public void addNotify() {
        super.addNotify();
        Editor editor = getEditor();
        if (editor != null) {
          addHyperLinks(editor, editor.getDocument().getText());
        }
      }
    };
    myDocument = textField.getDocument();
    return textField;
  }

  @Override
  public Object getCellEditorValue() {
    return myDocument.getText();
  }

  private void addHyperLinks(@NotNull Editor editor, @NotNull String text) {
    EditorHyperlinkSupport hyperlinkSupport = new EditorHyperlinkSupport(editor, myProject);
    TextAttributes attributes =
      EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.HYPERLINK_ATTRIBUTES);

    ExceptionFilter filter = new ExceptionFilter(GlobalSearchScope.allScope(myProject));
    Filter.Result result = filter.applyFilter(text, text.length());
    if (result != null) {
      List<Filter.ResultItem> items = result.getResultItems();
      for (Filter.ResultItem item : items) {
        HyperlinkInfo hyperlinkInfo = item.getHyperlinkInfo();
        if (hyperlinkInfo != null) {
          hyperlinkSupport.createHyperlink(item.getHighlightStartOffset(), item.getHighlightEndOffset(), attributes, hyperlinkInfo);
        }
      }
    }
  }
}
