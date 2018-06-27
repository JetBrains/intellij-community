// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.dsl;

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * @author Bas Leijdekkers
 */
public class StructuralSearchConfigurationProducer extends RunConfigurationProducer<StructuralSearchRunConfiguration> {

  public StructuralSearchConfigurationProducer() {
    super(StructuralSearchRunConfigurationType.getInstance());
  }

  @Override
  protected boolean setupConfigurationFromContext(StructuralSearchRunConfiguration configuration,
                                                  ConfigurationContext context,
                                                  Ref<PsiElement> element) {
    // todo check ssr dsl context
    final PsiVariable variable = PsiTreeUtil.getParentOfType(element.get(), PsiVariable.class);
    if (variable != null && "c".equals(variable.getName())) {
      return true;
    }
    return false;
  }

  @Override
  public boolean isConfigurationFromContext(StructuralSearchRunConfiguration configuration, ConfigurationContext context) {
    return false;
  }
}
