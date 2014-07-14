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
package org.jetbrains.plugins.groovy.gant;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
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
           GantUtils.isSDKConfiguredToRun(ObjectUtils.assertNotNull(DataKeys.MODULE.getData(dataContext)));
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
