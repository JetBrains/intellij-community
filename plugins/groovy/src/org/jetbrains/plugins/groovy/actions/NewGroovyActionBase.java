// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.actions;

import com.intellij.CommonBundle;
import com.intellij.ide.actions.CreateElementActionBase;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts.DialogMessage;
import com.intellij.openapi.util.NlsContexts.DialogTitle;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.config.GroovyFacetUtil;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

import javax.swing.*;
import java.util.function.Supplier;

public abstract class NewGroovyActionBase extends CreateElementActionBase {

  @NonNls
  public static final String GROOVY_EXTENSION = ".groovy";

  protected NewGroovyActionBase() {}

  public NewGroovyActionBase(@NotNull Supplier<String> text, @NotNull Supplier<String> description, Icon icon) {
    super(text, description, icon);
  }

  @Override
  protected final PsiElement @NotNull [] invokeDialog(final @NotNull Project project, final @NotNull PsiDirectory directory) {
    MyInputValidator validator = new MyInputValidator(project, directory);
    Messages.showInputDialog(project, getDialogPrompt(), getDialogTitle(), Messages.getQuestionIcon(), "", validator);

    return validator.getCreatedElements();
  }

  protected abstract @DialogMessage String getDialogPrompt();

  protected abstract @DialogTitle String getDialogTitle();

  @Override
  protected boolean isAvailable(DataContext dataContext) {
    if (!super.isAvailable(dataContext)) {
      return false;
    }

    Module module = PlatformCoreDataKeys.MODULE.getData(dataContext);
    return GroovyFacetUtil.isSuitableModule(module) && LibrariesUtil.hasGroovySdk(module);
  }

  @Override
  protected PsiElement @NotNull [] create(@NotNull String newName, @NotNull PsiDirectory directory) throws Exception {
    return doCreate(newName, directory);
  }

  protected abstract PsiElement @NotNull [] doCreate(String newName, PsiDirectory directory) throws Exception;


  @Override
  protected String getErrorTitle() {
    return CommonBundle.getErrorTitle();
  }
}