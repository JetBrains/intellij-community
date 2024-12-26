// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.lang.properties.psi;

import com.intellij.codeInsight.daemon.impl.quickfix.SetupJDKFix;
import com.intellij.ide.fileTemplates.JavaTemplateUtil;
import com.intellij.java.i18n.JavaI18nBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DefaultResourceBundleManager extends ResourceBundleManager {
  public DefaultResourceBundleManager(final Project project) {
    super(project);
  }

  @Override
  public @Nullable PsiClass getResourceBundle() {
    return JavaPsiFacade.getInstance(myProject).findClass("java.util.ResourceBundle", GlobalSearchScope.allScope(myProject));
  }

  @Override
  public String getTemplateName() {
    return JavaTemplateUtil.TEMPLATE_I18NIZED_EXPRESSION;
  }

  @Override
  public String getConcatenationTemplateName() {
    return JavaTemplateUtil.TEMPLATE_I18NIZED_CONCATENATION;
  }

  @Override
  public boolean isActive(@NotNull PsiFile context) throws ResourceBundleNotFoundException {
    if (getResourceBundle() != null) {
      return true;
    }
    throw new ResourceBundleNotFoundException(JavaI18nBundle.message("i18nize.dialog.error.jdk.message"), SetupJDKFix.getInstance());
  }

  @Override
  public boolean canShowJavaCodeInfo() {
    return true;
  }
}