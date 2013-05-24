/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.fixes;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

public class MakeCloneableFix extends InspectionGadgetsFix {

  private final boolean isInterface;

  public MakeCloneableFix(boolean isInterface) {
    this.isInterface = isInterface;
  }

  @Override
  @NotNull
  public String getName() {
    if (isInterface) {
      return InspectionGadgetsBundle.message(
        "make.interface.cloneable.quickfix");
    }
    else {
      return InspectionGadgetsBundle.message(
        "make.class.cloneable.quickfix");
    }
  }

  @Override
  public void doFix(Project project, ProblemDescriptor descriptor)
    throws IncorrectOperationException {
    final PsiElement nameElement = descriptor.getPsiElement();
    final PsiClass containingClass =
      ClassUtils.getContainingClass(nameElement);
    if (containingClass == null) {
      return;
    }
    final PsiElementFactory elementFactory =
      JavaPsiFacade.getElementFactory(project);
    final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    final PsiJavaCodeReferenceElement ref =
      elementFactory.createReferenceElementByFQClassName(
        CommonClassNames.JAVA_LANG_CLONEABLE, scope);
    final PsiReferenceList extendsImplementsList;
    if (containingClass.isInterface()) {
      extendsImplementsList = containingClass.getExtendsList();
    }
    else {
      extendsImplementsList = containingClass.getImplementsList();
    }
    if (extendsImplementsList == null) {
      return;
    }
    extendsImplementsList.add(ref);
  }
}
