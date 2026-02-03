// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.console;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;

public class GrExecuteCommandAction extends AnAction {

  public GrExecuteCommandAction() {
    super(AllIcons.Actions.Execute);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    final Editor editor = e.getData(CommonDataKeys.EDITOR);
    final VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
    if (project == null || editor == null || virtualFile == null) return;

    FileDocumentManager.getInstance().saveAllDocuments();

    final Document document = editor.getDocument();
    final TextRange selectedRange = EditorUtil.getSelectionInAnyMode(editor);
    final String command;
    if (selectedRange.isEmpty()) {
      command = document.getText(); // whole document
    }
    else {
      StringBuilder commandBuilder = new StringBuilder();
      PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
      if (file instanceof GroovyFile) {
        GrImportStatement[] statements = ((GroovyFile)file).getImportStatements();
        for (GrImportStatement statement : statements) {
          if (!selectedRange.contains(statement.getTextRange())) {
            commandBuilder.append(statement.getText()).append("\n");
          }
        }
      }
      commandBuilder.append(document.getText(selectedRange));
      command = commandBuilder.toString();
    }

    GroovyConsole.getOrCreateConsole(project, virtualFile, console -> console.execute(command));
  }
}
