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
package org.jetbrains.plugins.groovy.refactoring.javaToGrovyRename;

import com.intellij.lang.StdLanguages;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.GroovyFileType;

/**
 * @author ven
 */
public class RenameJavaFileToGroovyFileAction extends AnAction {
  public void actionPerformed(final AnActionEvent e) {
    final PsiFile file = e.getData(DataKeys.PSI_FILE);
    assert isEnabled(file);
    assert file != null;
    VirtualFile virtualFile = file.getVirtualFile();
    assert virtualFile != null;
    String newExt = file.getLanguage() == StdLanguages.JAVA ? GroovyFileType.DEFAULT_EXTENSION : StdFileTypes.JAVA.getDefaultExtension();

    final String newName = virtualFile.getNameWithoutExtension() + "." + newExt;
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          file.setName(newName);
        } catch (final IncorrectOperationException e1) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              Project project = e.getData(DataKeys.PROJECT);
              Messages.showErrorDialog(project, "Cannot rename java file" + e1.getMessage(), "Canot Rename");
            }
          });
        }
      }
    });
  }

  public void update(AnActionEvent e) {
    PsiFile file = e.getData(DataKeys.PSI_FILE);
    Presentation presentation = e.getPresentation();
    boolean enabled = isEnabled(file);
    presentation.setEnabled(enabled);
    presentation.setVisible(enabled);
    if (enabled) {
      assert file != null;
      presentation.setText(file.getLanguage() == StdLanguages.JAVA ? "Rename To Groovy" : "Rename To Java");
    }
  }

  private boolean isEnabled(PsiFile file) {
    return file != null && (file.getLanguage() == StdLanguages.JAVA || file.getLanguage() == GroovyFileType.GROOVY_LANGUAGE) &&
                                   file.getManager().isInProject(file);
  }
}
