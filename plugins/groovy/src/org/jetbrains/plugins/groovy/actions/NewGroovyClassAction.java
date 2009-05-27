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

package org.jetbrains.plugins.groovy.actions;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.ide.actions.CreateClassAction;
import com.intellij.ide.actions.CreateInPackageFromTemplateActionBase;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

public class NewGroovyClassAction extends CreateInPackageFromTemplateActionBase {
  public NewGroovyClassAction() {
    super(GroovyBundle.message("newclass.menu.action.text"),
        GroovyBundle.message("newclass.menu.action.description"),
        GroovyIcons.CLASS);
  }

  protected String getActionName(PsiDirectory directory, String newName) {
    return GroovyBundle.message("newclass.menu.action.text"); 
  }

  protected String getDialogPrompt() {
    return GroovyBundle.message("newclass.dlg.prompt");
  }

  protected String getDialogTitle() {
    return GroovyBundle.message("newclass.dlg.title");
  }

  @NotNull
  protected final PsiElement[] invokeDialog(final Project project, final PsiDirectory directory) {
    MyInputValidator validator = new MyInputValidator(project, directory);
    Messages.showInputDialog(project, getDialogPrompt(), getDialogTitle(), Messages.getQuestionIcon(), "", validator);

    return validator.getCreatedElements();
  }

  protected String getErrorTitle() {
    return CommonBundle.getErrorTitle();
  }

  protected String getCommandName() {
    return GroovyBundle.message("newclass.command.name");
  }

  protected PsiClass doCreateClass(PsiDirectory dir, String className) throws IncorrectOperationException {
    return ((GroovyFile)GroovyTemplatesFactory.createFromTemplate(dir, className, className + NewGroovyActionBase.GROOVY_EXTENSION, "GroovyClass.groovy"))
      .getClasses()[0];
  }

  protected Template buildTemplate(PsiClass templateClass) {
    final Project project = templateClass.getProject();
    TemplateBuilder builder = new TemplateBuilder(templateClass.getContainingFile());
    final ASTNode classToken = ObjectUtils.assertNotNull(templateClass.getNode()).findChildByType(GroovyTokenTypes.kCLASS);
    assert classToken != null;
    builder.replaceElement(classToken.getPsi(), CreateClassAction.createTypeExpression(project), true);
    builder.setEndVariableBefore(((GrTypeDefinition)templateClass).getLBraceGroovy());

    return builder.buildTemplate();
  }
}
