/*
 * Copyright 2003-2005 Dave Griffith
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
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

public class AddSerialVersionUIDFix extends InspectionGadgetsFix {

  @Override
  @NotNull
  public String getName() {
    return InspectionGadgetsBundle.message("add.serialversionuidfield.quickfix");
  }

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

  @Override
  public void doFix(Project project, ProblemDescriptor descriptor)
    throws IncorrectOperationException {
    final PsiElement classIdentifier = descriptor.getPsiElement();
    final PsiClass aClass = (PsiClass)classIdentifier.getParent();
    assert aClass != null;
    final PsiManager psiManager = aClass.getManager();
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory();
    final long serialVersionUID =
      SerialVersionUIDBuilder.computeDefaultSUID(aClass);
    final PsiField field =
      elementFactory.createFieldFromText("private static final long serialVersionUID = " +
                                         serialVersionUID + "L;", aClass);
    aClass.add(field);
  }
}
