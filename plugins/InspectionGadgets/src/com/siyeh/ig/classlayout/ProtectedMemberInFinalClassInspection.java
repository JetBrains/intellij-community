/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.*;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RemoveModifierFix;
import org.jetbrains.annotations.NotNull;

public class ProtectedMemberInFinalClassInspection extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "protected.member.in.final.class.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "protected.member.in.final.class.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ProtectedMemberInFinalClassVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new RemoveModifierFix(location);
    }

    private static class ProtectedMemberInFinalClassVisitor
            extends BaseInspectionVisitor {

        @Override public void visitMethod(@NotNull PsiMethod method) {
            //no call to super, so we don't drill into anonymous classes
            if (!method.hasModifierProperty(PsiModifier.PROTECTED)) {
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if (containingClass == null) {
                return;
            }
            if (!containingClass.hasModifierProperty(PsiModifier.FINAL)) {
                return;
            }
            final Query<MethodSignatureBackedByPsiMethod> superMethodQuery =
                    SuperMethodsSearch.search(method, null, true, false);
            if (superMethodQuery.findFirst() != null) {
	            return;
	        }
            registerModifierError(PsiModifier.PROTECTED, method);
        }

	    @Override public void visitField(@NotNull PsiField field) {
	        //no call to super, so we don't drill into anonymous classes
	        if (!field.hasModifierProperty(PsiModifier.PROTECTED)) {
	            return;
	        }
	        final PsiClass containingClass = field.getContainingClass();
	        if (containingClass == null) {
	            return;
	        }
	        if (!containingClass.hasModifierProperty(PsiModifier.FINAL)) {
	            return;
	        }
	        registerModifierError(PsiModifier.PROTECTED, field);
	    }
    }
}