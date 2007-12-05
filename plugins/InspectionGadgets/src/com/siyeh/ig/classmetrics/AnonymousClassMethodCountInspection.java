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
package com.siyeh.ig.classmetrics;

import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.MoveAnonymousToInnerClassFix;
import org.jetbrains.annotations.NotNull;

public class AnonymousClassMethodCountInspection
        extends ClassMetricInspection {

    private static final int DEFAULT_METHOD_COUNT_LIMIT = 1;

    @NotNull
    public String getID(){
        return "AnonymousInnerClassWithTooManyMethods";
    }

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "anonymous.inner.class.with.too.many.methods.display.name");
    }

    protected int getDefaultLimit() {
        return DEFAULT_METHOD_COUNT_LIMIT;
    }

    protected String getConfigurationLabel() {
        return InspectionGadgetsBundle.message("method.count.limit.option");
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return new MoveAnonymousToInnerClassFix();
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        final Integer count = (Integer)infos[0];
        return InspectionGadgetsBundle.message(
                "anonymous.inner.class.with.too.many.methods.problem.descriptor",
                count);
    }

    public BaseInspectionVisitor buildVisitor() {
        return new AnonymousClassMethodCountVisitor();
    }

    private class AnonymousClassMethodCountVisitor
            extends BaseInspectionVisitor {

        @Override public void visitClass(@NotNull PsiClass psiClass) {
            // no call to super, to prevent double counting
        }

        @Override public void visitAnonymousClass(@NotNull PsiAnonymousClass aClass) {
            final int totalMethodCount = calculateTotalMethodCount(aClass);
            if (totalMethodCount <= getLimit()) {
                return;
            }
            registerClassError(aClass, Integer.valueOf(totalMethodCount));
        }

        private int calculateTotalMethodCount(PsiClass aClass) {
            final PsiMethod[] methods = aClass.getMethods();
            int totalCount = 0;
            for(final PsiMethod method : methods){
                if(!method.isConstructor()){
                    totalCount++;
                }
            }
            return totalCount;
        }
    }
}