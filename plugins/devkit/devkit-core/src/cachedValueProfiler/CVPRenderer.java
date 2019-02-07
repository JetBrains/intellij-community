// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.cachedValueProfiler;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.ui.EditorTextField;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

class CVPRenderer implements TableCellRenderer {
  private final Project myProject;
  private EditorTextField myComponent;

  CVPRenderer(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public Component getTableCellRendererComponent(JTable table,
                                                 Object value,
                                                 boolean isSelected,
                                                 boolean hasFocus,
                                                 int row,
                                                 int column) {
    if (myComponent == null) {
      Document document = EditorFactory.getInstance().createDocument((String)value);
      myComponent = new EditorTextField(document, myProject, FileTypes.PLAIN_TEXT, true, true) {
        @Override
        protected boolean shouldHaveBorder() {
          return false;
        }
      };
    }
    else {
      myComponent.setText((String)value);
    }
    return myComponent;
  }
}
