/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.compiler;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.compiler.options.ExcludeEntryDescription;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.plugins.groovy.GroovyFileType;

/**
 * @author peter
 */
public class ExcludeFromStubGenerationAction extends AnAction implements DumbAware {
  public void actionPerformed(final AnActionEvent e) {
    final PsiFile file = e.getData(DataKeys.PSI_FILE);

    assert file != null && file.getLanguage() == GroovyFileType.GROOVY_LANGUAGE;

    doExcludeFromStubGeneration(file);
  }

  public static void doExcludeFromStubGeneration(PsiFile file) {
    final VirtualFile virtualFile = file.getVirtualFile();
    assert virtualFile != null;
    final Project project = file.getProject();

    final GroovyCompilerConfigurable configurable = new GroovyCompilerConfigurable(project);
    ShowSettingsUtil.getInstance().editConfigurable(project, configurable, new Runnable() {
      public void run() {
        configurable.getExcludes().addEntry(new ExcludeEntryDescription(virtualFile, false, true, project));
      }
    });
  }

  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();

    boolean enabled = isEnabled(e);
    presentation.setEnabled(enabled);
    presentation.setVisible(enabled);
  }

  private static boolean isEnabled(AnActionEvent e) {
    PsiFile file = e.getData(DataKeys.PSI_FILE);
    if (file == null || file.getLanguage() != GroovyFileType.GROOVY_LANGUAGE) {
      return false;
    }

    final VirtualFile virtualFile = file.getVirtualFile();
    return virtualFile != null && !GroovyCompilerConfiguration.getExcludeConfiguration(file.getProject()).isExcluded(virtualFile);
  }

}
