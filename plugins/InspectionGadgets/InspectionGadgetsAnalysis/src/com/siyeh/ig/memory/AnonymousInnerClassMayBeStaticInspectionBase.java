/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.performance.InnerClassReferenceVisitor;
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
      final InnerClassReferenceVisitor visitor = new InnerClassReferenceVisitor(anonymousClass);
      anonymousClass.accept(visitor);
      if (!visitor.canInnerClassBeStatic()) {
        return;
      }
      registerClassError(anonymousClass);
    }
  }
}
