// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.quickfix;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;

import static org.jetbrains.idea.devkit.util.PsiUtil.createPointer;

public class CreateConstructorFix extends BaseFix {
  public CreateConstructorFix(@NotNull PsiClass aClass, boolean isOnTheFly) {
    super(createPointer(aClass), isOnTheFly);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return DevKitBundle.message("inspections.registration.problems.quickfix.create.constructor");
  }

  @Override
  protected void doFix(Project project, ProblemDescriptor descriptor, boolean external) throws IncorrectOperationException {
    PsiElement element = myPointer.getElement();
    if (!(element instanceof PsiClass)) return;
    PsiClass clazz = (PsiClass)element;

    PsiMethod ctor = JavaPsiFacade.getInstance(clazz.getProject()).getElementFactory().createConstructor();
    PsiUtil.setModifierProperty(ctor, PsiModifier.PUBLIC, true);

    PsiMethod[] constructors = clazz.getConstructors();
    if (constructors.length > 0) {
      ctor = (PsiMethod)clazz.addBefore(ctor, constructors[0]);
    } else {
      // shouldn't get here - it's legal if there's no ctor present at all
      ctor = (PsiMethod)clazz.add(ctor);
    }

    if (myOnTheFly) ctor.navigate(true);
  }
}
