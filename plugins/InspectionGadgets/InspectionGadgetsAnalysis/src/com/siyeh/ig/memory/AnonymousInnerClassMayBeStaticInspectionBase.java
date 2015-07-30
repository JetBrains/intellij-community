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
package com.siyeh.ig.memory;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class AnonymousInnerClassMayBeStaticInspectionBase extends BaseInspection {
  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "anonymous.inner.may.be.named.static.inner.class.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "anonymous.inner.may.be.named.static.inner.class.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AnonymousInnerClassMayBeStaticVisitor();
  }

  private static class AnonymousInnerClassMayBeStaticVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitAnonymousClass(@NotNull PsiAnonymousClass anonymousClass) {
      if (anonymousClass instanceof PsiEnumConstantInitializer) {
        return;
      }
      final PsiMember containingMember = PsiTreeUtil.getParentOfType(anonymousClass, PsiMember.class);
      if (containingMember == null || containingMember.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      final PsiJavaCodeReferenceElement reference = anonymousClass.getBaseClassReference();
      if (reference.resolve() == null) {
        // don't warn on broken code
        return;
      }
      final PsiClass containingClass = PsiTreeUtil.getParentOfType(anonymousClass, PsiClass.class);
      if (containingClass == null) {
        return;
      }
      if (containingClass.getContainingClass() != null && !containingClass.hasModifierProperty(PsiModifier.STATIC)) {
        // strictly speaking can be named static inner class but not when part of the current containing class
        return;
      }
      final InnerClassReferenceVisitor visitor = new InnerClassReferenceVisitor(anonymousClass);
      anonymousClass.accept(visitor);
      if (!visitor.canInnerClassBeStatic()) {
        return;
      }
      if (hasReferenceToLocalClass(anonymousClass)) {
        return;
      }
      registerClassError(anonymousClass);
    }

    private static boolean hasReferenceToLocalClass(PsiAnonymousClass anonymousClass) {
      final LocalClassReferenceVisitor visitor = new LocalClassReferenceVisitor();
      anonymousClass.accept(visitor);
      return visitor.hasReferenceToLocalClass();
    }

    private static class LocalClassReferenceVisitor extends JavaRecursiveElementWalkingVisitor {

      private boolean referenceToLocalClass;

      @Override
      public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
        super.visitReferenceElement(reference);
        if (reference.getQualifier() != null) {
          return;
        }
        final PsiElement target = reference.resolve();
        if (!(target instanceof PsiClass) || !PsiUtil.isLocalClass((PsiClass)target)) {
          return;
        }
        referenceToLocalClass = true;
      }

      private boolean hasReferenceToLocalClass() {
        return referenceToLocalClass;
      }
    }
  }
}
