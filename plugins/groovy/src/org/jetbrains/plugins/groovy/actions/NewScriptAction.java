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

package org.jetbrains.plugins.groovy.actions;

import com.intellij.CommonBundle;
import com.intellij.ide.actions.CreateFileFromTemplateDialog;
import com.intellij.ide.actions.CreateTemplateInPackageAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

public class NewScriptAction extends CreateTemplateInPackageAction<GroovyFile> implements DumbAware {
  private static final String GROOVY_DSL_SCRIPT_TMPL = "GroovyDslScript.gdsl";

  public NewScriptAction() {
    super(GroovyBundle.message("newscript.menu.action.text"), GroovyBundle.message("newscript.menu.action.description"), GroovyIcons.GROOVY_ICON_16x16, false);
  }

  @NotNull
  @Override
  protected CreateFileFromTemplateDialog.Builder buildDialog(Project project, final PsiDirectory directory) {
    final CreateFileFromTemplateDialog.Builder builder = CreateFileFromTemplateDialog.
      createDialog(project, GroovyBundle.message("newscript.dlg.prompt"));
    builder.addKind("Groovy script", GroovyIcons.GROOVY_ICON_16x16, "GroovyScript.groovy");
    builder.addKind("GroovyDSL script", GroovyIcons.GROOVY_ICON_16x16, GROOVY_DSL_SCRIPT_TMPL);
    return builder;
  }

  @Override
  protected boolean isAvailable(DataContext dataContext) {
    return super.isAvailable(dataContext) && LibrariesUtil.hasGroovySdk(DataKeys.MODULE.getData(dataContext));
  }

  @Override
  protected String getActionName(PsiDirectory directory, String newName, String templateName) {
    return GroovyBundle.message("newscript.menu.action.text");
  }

  protected String getErrorTitle() {
    return CommonBundle.getErrorTitle();
  }

  @Override
  protected PsiElement getNavigationElement(@NotNull GroovyFile createdFile) {
    return createdFile.getLastChild();
  }

  @Override
  protected void doCheckCreate(PsiDirectory dir, String className, String templateName) throws IncorrectOperationException {
    dir.checkCreateFile(className + "." + extractExtension(templateName));
  }

  @NotNull
  protected GroovyFile doCreate(PsiDirectory directory, String newName, String templateName) throws IncorrectOperationException {
    PsiFile file = GroovyTemplatesFactory.createFromTemplate(directory, newName, newName + "." + extractExtension(templateName), templateName);
    assert file instanceof GroovyFile;
    return (GroovyFile)file;
  }

  private static String extractExtension(String templateName) {
    if (GROOVY_DSL_SCRIPT_TMPL.equals(templateName)) {
      return "gdsl";
    }
    return GroovyFileType.DEFAULT_EXTENSION;
  }


}