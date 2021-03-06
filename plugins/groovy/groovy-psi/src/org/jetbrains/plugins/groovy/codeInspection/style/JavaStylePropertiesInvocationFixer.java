// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.style;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.utils.JavaStylePropertiesUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;

public class JavaStylePropertiesInvocationFixer implements LocalQuickFix {
  @NotNull
  @Override
  public String getName() {
    return GroovyBundle.message("java.style.properties.invocation.intention.name");
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return GroovyBundle.message("java.style.properties.invocation.intention.family.name");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement pparent = descriptor.getPsiElement().getParent().getParent();
    if (pparent instanceof GrMethodCall){
      JavaStylePropertiesUtil.fixJavaStyleProperty((GrMethodCall)pparent);
    }
  }
}
