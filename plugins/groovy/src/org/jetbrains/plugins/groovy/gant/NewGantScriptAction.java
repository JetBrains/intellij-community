// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.gant;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.actions.GroovyTemplates;
import org.jetbrains.plugins.groovy.actions.GroovyTemplatesFactory;
import org.jetbrains.plugins.groovy.actions.NewGroovyActionBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

/**
 * @author ilyas
 */
public class NewGantScriptAction extends NewGroovyActionBase {

  public NewGantScriptAction() {
    super("Gant Script", "Create new Gant Script", JetgroovyIcons.Groovy.Gant_16x16);
  }

  @Override
  protected String getActionName(PsiDirectory directory, String newName) {
    return null;
  }

  @Override
  protected String getDialogPrompt() {
    return "Enter name for new Gant Script";
  }

  @Override
  protected String getDialogTitle() {
    return "New Gant Script";
  }

  @Override
  protected String getCommandName() {
    return "Create Gant Script";
  }

  @Override
  protected boolean isAvailable(DataContext dataContext) {
    return super.isAvailable(dataContext) &&
           GantUtils.isSDKConfiguredToRun(ObjectUtils.assertNotNull(LangDataKeys.MODULE.getData(dataContext)));
  }

  @Override
  @NotNull
  protected PsiElement[] doCreate(String newName, PsiDirectory directory) throws Exception {
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
