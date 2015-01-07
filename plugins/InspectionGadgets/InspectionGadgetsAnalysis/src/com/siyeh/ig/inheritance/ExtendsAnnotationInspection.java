/*
 * Copyright 2003-2014 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.inheritance;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class ExtendsAnnotationInspection extends BaseInspection {

  @Override
  @NotNull
  public String getID() {
    return "ClassExplicitlyAnnotation";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "extends.annotation.display.name");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiClass containingClass = (PsiClass)infos[0];
    return containingClass.isInterface()
           ? InspectionGadgetsBundle.message("extends.annotation.interface.problem.descriptor", containingClass.getName())
           : InspectionGadgetsBundle.message("extends.annotation.problem.descriptor", containingClass.getName());
  }

  @Override
  public boolean shouldInspect(PsiFile file) {
    return PsiUtil.isLanguageLevel5OrHigher(file);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ExtendsAnnotationVisitor();
  }

  private static class ExtendsAnnotationVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      if (aClass.isAnnotationType() || InheritanceUtil.isInheritor(aClass, "javax.enterprise.util.AnnotationLiteral")) {
        return;
      }
      checkReferenceList(aClass.getExtendsList(), aClass);
      checkReferenceList(aClass.getImplementsList(), aClass);
    }

    private void checkReferenceList(PsiReferenceList referenceList,
                                    PsiClass containingClass) {
      if (referenceList == null) {
        return;
      }
      final PsiJavaCodeReferenceElement[] elements =
        referenceList.getReferenceElements();
      for (final PsiJavaCodeReferenceElement element : elements) {
        final PsiElement referent = element.resolve();
        if (!(referent instanceof PsiClass)) {
          continue;
        }
        final PsiClass psiClass = (PsiClass)referent;
        if (psiClass.isAnnotationType()) {
          registerError(element, containingClass);
        }
      }
    }
  }
}