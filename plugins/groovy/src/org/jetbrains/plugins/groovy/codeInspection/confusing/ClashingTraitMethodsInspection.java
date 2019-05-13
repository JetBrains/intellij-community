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
package org.jetbrains.plugins.groovy.codeInspection.confusing;

import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
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
    public String getName() {
      return GroovyInspectionBundle.message("declare.explicit.implementations.of.trait");
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Declare explicit implementation of clashing traits";
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
