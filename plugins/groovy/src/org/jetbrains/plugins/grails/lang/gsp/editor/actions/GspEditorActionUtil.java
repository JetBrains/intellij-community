/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.grails.lang.gsp.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import org.jetbrains.plugins.grails.fileType.GspFileType;

/**
 * @author ilyas
 */
public abstract class GspEditorActionUtil {
  static void insertSpacesByIndent(Editor editor, Project project) {
    int indentSize = CodeStyleSettingsManager.getSettings(project).getIndentSize(GspFileType.GSP_FILE_TYPE);
    StringBuffer buffer = new StringBuffer();
    for (int i = 0; i < indentSize; i++) {
      buffer.append(" ");
    }
    EditorModificationUtil.insertStringAtCaret(editor, buffer.toString());
  }

  static void insertSpacesByIndent(Editor editor, DataContext dataContext) {
    final Project project = DataKeys.PROJECT.getData(dataContext);
    int indentSize = CodeStyleSettingsManager.getSettings(project).getIndentSize(GspFileType.GSP_FILE_TYPE);
    StringBuffer buffer = new StringBuffer();
    for (int i = 0; i < indentSize; i++) {
      buffer.append(" ");
    }
    EditorModificationUtil.insertStringAtCaret(editor, buffer.toString());
  }
}
