// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.confusing;

import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrTraitMethod;

import java.util.List;

public class ClashingTraitMethodsInspection extends ClashingTraitMethodsInspectionBase {

  @NotNull
  @Override
  protected LocalQuickFix getFix(){
    return new MyQuickFix();
  }

  private static class MyQuickFix implements LocalQuickFix {

    @NotNull
    @Override
    public String getFamilyName() {
      return GroovyBundle.message("declare.explicit.implementations.of.trait");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull final ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      PsiElement parent = element.getParent();
      if (parent instanceof GrTypeDefinition && ((GrTypeDefinition)parent).getNameIdentifierGroovy() == element) {
        final GrTypeDefinition aClass = (GrTypeDefinition)parent;

        WriteCommandAction.writeCommandAction(project, aClass.getContainingFile()).run(() -> {
          final List<ClashingMethod> clashingMethods = collectClassingMethods(aClass);

          for (ClashingMethod method : clashingMethods) {
            PsiMethod traitMethod = method.getSignature().getMethod();
            LOG.assertTrue(traitMethod instanceof GrTraitMethod);
            OverrideImplementUtil.overrideOrImplement(aClass, traitMethod);
          }
        });
      }
    }
  }
}
