/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.siyeh.ig.abstraction;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.FindSuperElementsHelper;
import com.intellij.util.ArrayUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.AddToIgnoreIfAnnotatedByListQuickFix;
import com.siyeh.ig.psiutils.LibraryUtil;
import com.siyeh.ig.psiutils.TestUtils;
import com.siyeh.ig.ui.ExternalizableStringSet;
import org.jetbrains.annotations.NotNull;

public class PublicMethodNotExposedInInterfaceInspectionBase extends BaseInspection {
  @SuppressWarnings("PublicField")
  public final ExternalizableStringSet ignorableAnnotations =
    new ExternalizableStringSet();
  @SuppressWarnings("PublicField")
  public boolean onlyWarnIfContainingClassImplementsAnInterface = false;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "public.method.not.in.interface.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "public.method.not.in.interface.problem.descriptor");
  }

  @NotNull
  @Override
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    return AddToIgnoreIfAnnotatedByListQuickFix.build((PsiModifierListOwner)infos[0], ignorableAnnotations);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new PublicMethodNotExposedInInterfaceVisitor();
  }

  private class PublicMethodNotExposedInInterfaceVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      if (method.isConstructor()) {
        return;
      }
      if (method.getNameIdentifier() == null) {
        return;
      }
      if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
        return;
      }
      if (method.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      if (containingClass.isInterface() ||
          containingClass.isAnnotationType()) {
        return;
      }
      if (!containingClass.hasModifierProperty(PsiModifier.PUBLIC)) {
        return;
      }
      if (AnnotationUtil.isAnnotated(method, ignorableAnnotations)) {
        return;
      }
      if (onlyWarnIfContainingClassImplementsAnInterface) {
        final PsiClass[] superClasses = containingClass.getSupers();
        boolean implementsInterface = false;
        for (PsiClass superClass : superClasses) {
          if (superClass.isInterface() &&
              !LibraryUtil.classIsInLibrary(superClass)) {
            implementsInterface = true;
            break;
          }
        }
        if (!implementsInterface) {
          return;
        }
      }
      if (exposedInInterface(method)) {
        return;
      }
      if (TestUtils.isJUnitTestMethod(method)) {
        return;
      }
      registerMethodError(method, method);
    }

    private boolean exposedInInterface(PsiMethod method) {
      PsiMethod[] superMethods = method.findSuperMethods();
      PsiMethod siblingInherited = FindSuperElementsHelper.getSiblingInheritedViaSubClass(method);
      if (siblingInherited != null && !ArrayUtil.contains(siblingInherited, superMethods)) {
        superMethods = ArrayUtil.append(superMethods, siblingInherited);
      }
      for (final PsiMethod superMethod : superMethods) {
        final PsiClass superClass = superMethod.getContainingClass();
        if (superClass == null) {
          continue;
        }
        if (superClass.isInterface()) {
          return true;
        }
        final String superclassName = superClass.getQualifiedName();
        if (CommonClassNames.JAVA_LANG_OBJECT.equals(superclassName)) {
          return true;
        }
        if (exposedInInterface(superMethod)) {
          return true;
        }
      }
      return false;
    }
  }
}
