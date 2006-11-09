/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.j2me;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEnumConstantInitializer;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.MoveAnonymousToInnerClassFix;
import com.siyeh.ig.performance.InnerClassReferenceVisitor;
import org.jetbrains.annotations.NotNull;

public class AnonymousInnerClassMayBeStaticInspection extends BaseInspection {

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "anonymous.inner.may.be.named.static.inner.class.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.J2ME_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos) {
      return InspectionGadgetsBundle.message(
              "anonymous.inner.may.be.named.static.inner.class.problem.descriptor");
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return new MoveAnonymousToInnerClassFix(
                InspectionGadgetsBundle.message(
                        "anonymous.inner.may.be.named.static.inner.class.quickfix"));
    }

    public BaseInspectionVisitor buildVisitor() {
        return new AnonymousInnerClassMayBeStaticVisitor();
    }

    private static class AnonymousInnerClassMayBeStaticVisitor
            extends BaseInspectionVisitor {

        public void visitClass(@NotNull PsiClass aClass){
            if (!(aClass instanceof PsiAnonymousClass)) {
                return;
            }
            if (aClass instanceof PsiEnumConstantInitializer) {
                return;
            }
            final PsiAnonymousClass anAnonymousClass =
                    (PsiAnonymousClass) aClass;
            final InnerClassReferenceVisitor visitor =
                    new InnerClassReferenceVisitor(anAnonymousClass);
            anAnonymousClass.accept(visitor);
            if(!visitor.canInnerClassBeStatic()) {
                return;
            }
            registerClassError(aClass);
        }
    }
}