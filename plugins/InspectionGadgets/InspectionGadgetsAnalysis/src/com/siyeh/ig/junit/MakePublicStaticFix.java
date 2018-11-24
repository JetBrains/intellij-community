/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettingsFacade;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

class MakePublicStaticFix extends InspectionGadgetsFix {

  private final String myName;
  private final boolean myMakeStatic;

  public MakePublicStaticFix(final String name, final boolean makeStatic) {
    myName = name;
    myMakeStatic = makeStatic;
  }

  @Override
  protected void doFix(Project project, ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    if (element == null) {
      return;
    }
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiMember)) {
      return;
    }
    final PsiMember member = (PsiMember)parent;
    final PsiModifierList modifierList = member.getModifierList();
    if (modifierList == null) {
      return;
    }
    modifierList.setModifierProperty(PsiModifier.PUBLIC, true);
    modifierList.setModifierProperty(PsiModifier.STATIC, myMakeStatic);
    final PsiElement sibling = modifierList.getNextSibling();
    if (sibling instanceof PsiWhiteSpace && sibling.getText().contains("\n")) {
      sibling.replace(PsiParserFacade.SERVICE.getInstance(project).createWhiteSpaceFromText(" "));
    }
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return "Make public/static";
  }
}
