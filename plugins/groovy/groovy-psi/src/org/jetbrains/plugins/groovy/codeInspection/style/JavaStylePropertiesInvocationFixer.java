// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.style;

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.utils.JavaStylePropertiesUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;

public class JavaStylePropertiesInvocationFixer extends PsiUpdateModCommandQuickFix {
  @Override
  public @NotNull String getName() {
    return GroovyBundle.message("java.style.properties.invocation.intention.name");
  }

  @Override
  public @NotNull String getFamilyName() {
    return GroovyBundle.message("java.style.properties.invocation.intention.family.name");
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    final PsiElement pparent = element.getParent().getParent();
    if (pparent instanceof GrMethodCall call) {
      JavaStylePropertiesUtil.fixJavaStyleProperty(call);
    }
  }
}
