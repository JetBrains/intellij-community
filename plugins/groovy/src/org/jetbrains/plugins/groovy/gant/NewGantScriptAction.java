// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.gant;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.util.NlsContexts.DialogMessage;
import com.intellij.openapi.util.NlsContexts.DialogTitle;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.actions.GroovyTemplates;
import org.jetbrains.plugins.groovy.actions.GroovyTemplatesFactory;
import org.jetbrains.plugins.groovy.actions.NewGroovyActionBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

import java.util.Objects;

/**
 * @author ilyas
 */
public class NewGantScriptAction extends NewGroovyActionBase {

  @Override
  protected @NotNull String getActionName(@NotNull PsiDirectory directory, @NotNull String newName) {
    return GroovyBundle.message("new.gant.script.dialog.title");
  }

  @Override
  protected @DialogMessage String getDialogPrompt() {
    return GroovyBundle.message("new.gant.script.dialog.message");
  }

  @Override
  protected @DialogTitle String getDialogTitle() {
    return GroovyBundle.message("new.gant.script.dialog.title");
  }

  @Override
  protected boolean isAvailable(DataContext dataContext) {
    return super.isAvailable(dataContext) &&
           GantUtils.isSDKConfiguredToRun(Objects.requireNonNull(PlatformCoreDataKeys.MODULE.getData(dataContext)));
  }

  @Override
  protected PsiElement @NotNull [] doCreate(String newName, PsiDirectory directory) throws Exception {
    PsiFile file = createGantScriptFromTemplate(directory, newName, GroovyTemplates.GANT_SCRIPT);
    PsiElement lastChild = file.getLastChild();
    PsiElement child = null;
    if (lastChild instanceof GrMethodCallExpression) {
      child = lastChild;
    }
    if (child == null && file.getChildren().length > 0) {
      child = file.getLastChild();
    }
    return child != null ? new PsiElement[]{file, child} : new PsiElement[]{file};
  }

  private static PsiFile createGantScriptFromTemplate(final PsiDirectory directory,
                                                      String className,
                                                      String templateName,
                                                      @NonNls String... parameters) throws IncorrectOperationException {
    return GroovyTemplatesFactory
      .createFromTemplate(directory, className, className + "." + GantScriptType.DEFAULT_EXTENSION, templateName, true, parameters);
  }

}