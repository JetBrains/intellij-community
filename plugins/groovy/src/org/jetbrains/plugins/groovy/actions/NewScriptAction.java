// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.actions;

import com.intellij.ide.actions.CreateFileFromTemplateDialog;
import com.intellij.ide.actions.JavaCreateTemplateInPackageAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GdslFileType;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

final class NewScriptAction extends JavaCreateTemplateInPackageAction<GroovyFile> implements DumbAware {

  NewScriptAction() {
    super(null);
  }

  @Override
  protected void buildDialog(@NotNull Project project, @NotNull PsiDirectory directory,
                             @NotNull CreateFileFromTemplateDialog.Builder builder) {
    builder
      .setTitle(GroovyBundle.message("new.script.dialog.title"))
      .addKind(GroovyBundle.message("new.script.list.item.script"), JetgroovyIcons.Groovy.GroovyFile, GroovyTemplates.GROOVY_SCRIPT)
      .addKind(GroovyBundle.message("new.script.list.item.script.dsl"), JetgroovyIcons.Groovy.GroovyFile, GroovyTemplates.GROOVY_DSL_SCRIPT);
  }

  @Override
  protected boolean isAvailable(@NotNull DataContext dataContext) {
    return super.isAvailable(dataContext) && LibrariesUtil.hasGroovySdk(PlatformCoreDataKeys.MODULE.getData(dataContext));
  }

  @Override
  protected String getActionName(PsiDirectory directory, @NotNull String newName, String templateName) {
    return GroovyBundle.message("action.Groovy.NewScript.text");
  }

  @Override
  protected PsiElement getNavigationElement(@NotNull GroovyFile createdFile) {
    return createdFile.getLastChild();
  }

  @Override
  protected @NotNull GroovyFile doCreate(PsiDirectory directory, String newName, String templateName) throws IncorrectOperationException {
    String fileName = newName + "." + extractExtension(templateName);
    PsiFile file = GroovyTemplatesFactory.createFromTemplate(directory, newName, fileName, templateName, true);
    if (file instanceof GroovyFile) return (GroovyFile)file;
    final String description = file.getFileType().getDescription();
    throw new IncorrectOperationException(GroovyBundle.message("groovy.file.extension.is.not.mapped.to.groovy.file.type", description));
  }

  private static String extractExtension(String templateName) {
    if (GroovyTemplates.GROOVY_DSL_SCRIPT.equals(templateName)) {
      return GdslFileType.INSTANCE.getDefaultExtension();
    }
    return GroovyFileType.DEFAULT_EXTENSION;
  }
}