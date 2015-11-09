/*
 * Copyright 2006-2009 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

public class BeforeClassOrAfterClassIsPublicStaticVoidNoArgInspection
  extends BeforeClassOrAfterClassIsPublicStaticVoidNoArgInspectionBase {

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    if (infos.length != 1) return null;
    final Object name = infos[0];
    if (!(name instanceof String)) return null;
    return new MakePublicStaticVoidFix((String)name);
  }

  private static class MakePublicStaticVoidFix extends InspectionGadgetsFix {
    private final String myName;

    public MakePublicStaticVoidFix(String name) {
      myName = name;
    }

    @Override
    protected void doFix(final Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiMethod method = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiMethod.class);
      if (method != null) {
        final PsiModifierList modifierList = method.getModifierList();
        if (!modifierList.hasModifierProperty(PsiModifier.PUBLIC)) {
          modifierList.setModifierProperty(PsiModifier.PUBLIC, true);
        }
        if (!modifierList.hasModifierProperty(PsiModifier.STATIC)) {
          modifierList.setModifierProperty(PsiModifier.STATIC, true);
        }

        if (!PsiType.VOID.equals(method.getReturnType())) {
          ChangeSignatureProcessor csp =
            new ChangeSignatureProcessor(project, method, false, PsiModifier.PUBLIC, method.getName(), PsiType.VOID,
                                         new ParameterInfoImpl[0]);
          csp.run();
        }
      }
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Fix modifiers";
    }

    @Override
    @NotNull
    public String getName() {
      return myName;
    }
  }
}
