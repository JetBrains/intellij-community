/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.style;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

public class ExtendsObjectInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("extends.object.display.name");
  }

  @Override
  @NotNull
  public String getID() {
    return "ClassExplicitlyExtendsObject";
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "extends.object.problem.descriptor");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new ExtendsObjectFix();
  }

  private static class ExtendsObjectFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message(
        "extends.object.remove.quickfix");
    }

    @Override
    public void doFix(@NotNull Project project, ProblemDescriptor descriptor) {
      final PsiElement extendClassIdentifier = descriptor.getPsiElement();
      final PsiClass element = (PsiClass)extendClassIdentifier.getParent();
      if (element == null) {
        return;
      }
      final PsiReferenceList extendsList = element.getExtendsList();
      if (extendsList == null) {
        return;
      }
      final PsiJavaCodeReferenceElement[] referenceElements = extendsList.getReferenceElements();
      for (PsiJavaCodeReferenceElement referenceElement :
        referenceElements) {
        deleteElement(referenceElement);
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ExtendsObjectVisitor();
  }

  private static class ExtendsObjectVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      if (aClass.isInterface() || aClass.isAnnotationType()) {
        return;
      }
      if (aClass instanceof PsiTypeParameter) {
        return;
      }
      final PsiClassType[] types = aClass.getExtendsListTypes();
      for (final PsiClassType type : types) {
        if (type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
          registerClassError(aClass);
        }
      }
    }
  }
}