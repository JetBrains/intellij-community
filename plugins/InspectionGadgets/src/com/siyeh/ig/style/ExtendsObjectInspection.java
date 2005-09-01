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
package com.siyeh.ig.style;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiReferenceList;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class ExtendsObjectInspection extends ClassInspection {

  private final ExtendsObjectFix fix = new ExtendsObjectFix();

  public String getID() {
    return "ClassExplicitlyExtendsObject";
  }

  public String getGroupDisplayName() {
    return GroupNames.STYLE_GROUP_NAME;
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  protected InspectionGadgetsFix buildFix(PsiElement location) {
    return fix;
  }

  private static class ExtendsObjectFix extends InspectionGadgetsFix {
    public String getName() {
      return InspectionGadgetsBundle.message("extends.object.remove.quickfix");
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement extendClassIdentifier = descriptor.getPsiElement();
      final PsiClass element =
        (PsiClass)extendClassIdentifier.getParent();
      assert element != null;
      final PsiReferenceList extendsList = element.getExtendsList();
      assert extendsList != null;
      final PsiJavaCodeReferenceElement[] elements =
        extendsList.getReferenceElements();
      for (PsiJavaCodeReferenceElement element1 : elements) {
        deleteElement(element1);
      }
    }
  }

  public BaseInspectionVisitor buildVisitor() {
    return new ExtendsObjectVisitor();
  }

  private static class ExtendsObjectVisitor extends BaseInspectionVisitor {

    public void visitClass(@NotNull PsiClass aClass) {
      if (aClass.isInterface() || aClass.isAnnotationType()) {
        return;
      }
      final PsiReferenceList extendsList = aClass.getExtendsList();
      if (extendsList != null) {
        final PsiJavaCodeReferenceElement[] elements =
          extendsList.getReferenceElements();
        for (final PsiJavaCodeReferenceElement element : elements) {
          final PsiElement referent = element.resolve();
          if (referent instanceof PsiClass) {
            final String className =
              ((PsiClass)referent).getQualifiedName();
            if ("java.lang.Object".equals(className)) {
              registerClassError(aClass);
            }
          }
        }
      }
    }
  }
}
