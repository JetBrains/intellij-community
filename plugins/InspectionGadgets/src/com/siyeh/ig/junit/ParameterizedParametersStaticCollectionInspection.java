/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

package com.siyeh.ig.junit;

import com.intellij.codeInsight.daemon.impl.quickfix.CreateMethodQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

public class ParameterizedParametersStaticCollectionInspection extends ParameterizedParametersStaticCollectionInspectionBase {

  @Override
  protected InspectionGadgetsFix buildFix(final Object... infos) {
    return new InspectionGadgetsFix() {
      @Override
      protected void doFix(final Project project, ProblemDescriptor descriptor) {
        final PsiElement element = descriptor.getPsiElement();
        final PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
        if (method != null && infos[1] instanceof PsiType) {
          PsiType type = (PsiType)infos[1];
          final ChangeSignatureProcessor csp =
            new ChangeSignatureProcessor(project, method, false, PsiModifier.PUBLIC, method.getName(), type, new ParameterInfoImpl[0]);
          csp.run();
        }
        else {
          final PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
          if (psiClass != null) {
            final CreateMethodQuickFix fix = CreateMethodQuickFix
              .createFix(psiClass, "@" + PARAMETERS_FQN + " public static java.util.Collection parameters()", "");
            if (fix != null) {
              fix.applyFix(project, descriptor);
            }
          }
        }
      }

      @Override
      @NotNull
      public String getName() {
        return infos.length > 0 ? (String)infos[0] : "Create @Parameterized.Parameters data provider";
      }

      @NotNull
      @Override
      public String getFamilyName() {
        return "Fix data provider signature";
      }
    };
  }
}