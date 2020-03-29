/*
 * Copyright 2000-2019 JetBrains s.r.o.
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
import com.intellij.ide.actions.CreateElementActionBase;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsActions;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.config.GroovyFacetUtil;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

import javax.swing.*;
import java.util.function.Supplier;

public abstract class NewGroovyActionBase extends CreateElementActionBase {

  @NonNls
  public static final String GROOVY_EXTENSION = ".groovy";

  public NewGroovyActionBase(@NlsActions.ActionText String text,
                             @NlsActions.ActionDescription String description,
                             Icon icon) {
    super(text, description, icon);
  }

  public NewGroovyActionBase(@NotNull Supplier<String> text, @NotNull Supplier<String> description, Icon icon) {
    super(text, description, icon);
  }

  @Override
  protected final PsiElement @NotNull [] invokeDialog(final Project project, final PsiDirectory directory) {
    MyInputValidator validator = new MyInputValidator(project, directory);
    Messages.showInputDialog(project, getDialogPrompt(), getDialogTitle(), Messages.getQuestionIcon(), "", validator);

    return validator.getCreatedElements();
  }

  protected abstract String getDialogPrompt();

  protected abstract String getDialogTitle();

  @Override
  protected boolean isAvailable(DataContext dataContext) {
    if (!super.isAvailable(dataContext)) {
      return false;
    }

    Module module = LangDataKeys.MODULE.getData(dataContext);
    return GroovyFacetUtil.isSuitableModule(module) && LibrariesUtil.hasGroovySdk(module);
  }

  @Override
  protected PsiElement @NotNull [] create(@NotNull String newName, PsiDirectory directory) throws Exception {
    return doCreate(newName, directory);
  }

  protected abstract PsiElement @NotNull [] doCreate(String newName, PsiDirectory directory) throws Exception;


  @Override
  protected String getErrorTitle() {
    return CommonBundle.getErrorTitle();
  }
}
