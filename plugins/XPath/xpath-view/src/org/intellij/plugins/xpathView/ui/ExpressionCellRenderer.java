/*
 * Copyright 2002-2005 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.xpathView.ui;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.ui.EditorTextField;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

public class ExpressionCellRenderer extends DefaultTableCellRenderer implements TableCellRenderer {
    private final Project project;

    public ExpressionCellRenderer(Project project) {
        this.project = project;
    }

    public Component getTableCellRendererComponent(final JTable jtable, Object obj, boolean flag, boolean flag1, int i, int j) {
        super.getTableCellRendererComponent(jtable, "", flag, flag1, i, j);

        Expression expression = (Expression)obj;
        if (expression != null && expression.getExpression().length() != 0) {
            final Document document = PsiDocumentManager.getInstance(project).getDocument(expression.getFile());
            return new MyEditorTextField(document, project, expression.getFileType());
        } else {
            return this;
        }
    }

    private class MyEditorTextField extends EditorTextField {

        public MyEditorTextField(Document document, Project project, FileType fileType) {
            super(document, project, fileType, false);
        }

        protected boolean shouldHaveBorder() {
            return false;
        }

        protected EditorEx createEditor() {
            final EditorEx editor = super.createEditor();
            editor.setBackgroundColor(ExpressionCellRenderer.this.getBackground());
            return editor;
        }
    }
}
