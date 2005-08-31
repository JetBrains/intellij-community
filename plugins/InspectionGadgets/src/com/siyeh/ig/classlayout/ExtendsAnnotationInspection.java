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
package com.siyeh.ig.classlayout;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class ExtendsAnnotationInspection extends ClassInspection {

  public String getID() {
    return "ClassExplicitlyAnnotation";
  }

  public String getGroupDisplayName() {
    return GroupNames.INHERITANCE_GROUP_NAME;
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  public String buildErrorString(PsiElement location) {
    final PsiClass containingClass = ClassUtils.getContainingClass(location);
    assert containingClass != null;
    return InspectionGadgetsBundle.message("extends.annotation.problem.descriptor", containingClass.getName());
  }

  public BaseInspectionVisitor buildVisitor() {
    return new ExtendsAnnotationVisitor();
  }

  private static class ExtendsAnnotationVisitor extends BaseInspectionVisitor {


    public void visitClass(@NotNull PsiClass aClass) {
      final PsiManager manager = aClass.getManager();
      final LanguageLevel languageLevel =
        manager.getEffectiveLanguageLevel();
      if (languageLevel.equals(LanguageLevel.JDK_1_3) ||
          languageLevel.equals(LanguageLevel.JDK_1_4)) {
        return;
      }
      if (aClass.isAnnotationType()) {
        return;
      }
      final PsiReferenceList extendsList = aClass.getExtendsList();
      if (extendsList != null) {
        final PsiJavaCodeReferenceElement[] elements =
          extendsList.getReferenceElements();
        for (final PsiJavaCodeReferenceElement element : elements) {
          final PsiElement referent = element.resolve();
          if (referent instanceof PsiClass) {
            ((PsiClass)referent).isAnnotationType();
            if (((PsiClass)referent).isAnnotationType()) {
              registerError(element);
            }
          }
        }
      }
      final PsiReferenceList implementsList = aClass.getImplementsList();
      if (implementsList != null) {
        final PsiJavaCodeReferenceElement[] elements =
          implementsList.getReferenceElements();
        for (final PsiJavaCodeReferenceElement element : elements) {
          final PsiElement referent = element.resolve();
          if (referent instanceof PsiClass) {
            ((PsiClass)referent).isAnnotationType();
            if (((PsiClass)referent).isAnnotationType()) {
              registerError(element);
            }
          }
        }
      }
    }
  }
}
