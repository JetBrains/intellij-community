/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.actions;

import com.intellij.ide.actions.CreateFileFromTemplateDialog;
import com.intellij.ide.actions.JavaCreateTemplateInPackageAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

public class NewScriptAction extends JavaCreateTemplateInPackageAction<GroovyFile> implements DumbAware {

  public NewScriptAction() {
    super(GroovyBundle.message("newscript.menu.action.text"), GroovyBundle.message("newscript.menu.action.description"),
          JetgroovyIcons.Groovy.Groovy_16x16, false);
  }

  @Override
  protected void buildDialog(Project project, PsiDirectory directory, CreateFileFromTemplateDialog.Builder builder) {
    builder
      .setTitle(GroovyBundle.message("newscript.dlg.prompt"))
      .addKind("Groovy script", JetgroovyIcons.Groovy.Groovy_16x16, GroovyTemplates.GROOVY_SCRIPT)
      .addKind("GroovyDSL script", JetgroovyIcons.Groovy.Groovy_16x16, GroovyTemplates.GROOVY_DSL_SCRIPT);
  }

  @Override
  protected boolean isAvailable(DataContext dataContext) {
    return super.isAvailable(dataContext) && LibrariesUtil.hasGroovySdk(LangDataKeys.MODULE.getData(dataContext));
  }

  @Override
  protected String getActionName(PsiDirectory directory, String newName, String templateName) {
    return GroovyBundle.message("newscript.menu.action.text");
  }

  @Override
  protected PsiElement getNavigationElement(@NotNull GroovyFile createdFile) {
    return createdFile.getLastChild();
  }

  @Override
  @NotNull
  protected GroovyFile doCreate(PsiDirectory directory, String newName, String templateName) throws IncorrectOperationException {
    String fileName = newName + "." + extractExtension(templateName);
    PsiFile file = GroovyTemplatesFactory.createFromTemplate(directory, newName, fileName, templateName, true);
    if (file instanceof GroovyFile) return (GroovyFile)file;
    final String description = file.getFileType().getDescription();
    throw new IncorrectOperationException(GroovyBundle.message("groovy.file.extension.is.not.mapped.to.groovy.file.type", description));
  }

  private static String extractExtension(String templateName) {
    if (GroovyTemplates.GROOVY_DSL_SCRIPT.equals(templateName)) {
      return "gdsl";
    }
    return GroovyFileType.DEFAULT_EXTENSION;
  }
}
