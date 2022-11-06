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
package com.siyeh.ig.memory;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public class ReplaceEmptyArrayToConstantFix extends InspectionGadgetsFix {
  private final String myText;
  private final @IntentionName String myName;

  public ReplaceEmptyArrayToConstantFix(PsiClass aClass, PsiField field) {
    myText = aClass.getQualifiedName() + "." + field.getName();
    myName = CommonQuickFixBundle.message("fix.replace.with.x", aClass.getName() + "." + field.getName());
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return InspectionGadgetsBundle.message("constant.for.zero.length.array.quickfix.family");
  }

  @Override
  protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiExpression newExp = JavaPsiFacade.getInstance(project).getElementFactory().createExpressionFromText(myText, descriptor.getPsiElement());
    PsiElement element = descriptor.getPsiElement().replace(newExp);
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(element);
  }
}
