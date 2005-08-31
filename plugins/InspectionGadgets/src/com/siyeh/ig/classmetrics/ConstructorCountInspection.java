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
package com.siyeh.ig.classmetrics;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class ConstructorCountInspection
        extends ClassMetricInspection {
    private static final int CONSTRUCTOR_COUNT_LIMIT = 5;

    public String getID(){
        return "ClassWithTooManyConstructors";
    }
    public String getDisplayName() {
        return InspectionGadgetsBundle.message("too.many.constructors.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.CLASSMETRICS_GROUP_NAME;
    }

    protected int getDefaultLimit() {
        return CONSTRUCTOR_COUNT_LIMIT;
    }

    protected String getConfigurationLabel() {
        return InspectionGadgetsBundle.message("too.many.constructors.count.limit.option");
    }

    public String buildErrorString(PsiElement location) {
        final PsiClass aClass = (PsiClass) location.getParent();
        final int count = calculateTotalConstructorCount(aClass);
        return InspectionGadgetsBundle.message("too.many.constructors.problem.descriptor", count);
    }

    public BaseInspectionVisitor buildVisitor() {
        return new MethodCountVisitor();
    }

    private class MethodCountVisitor extends BaseInspectionVisitor {

        public void visitClass(@NotNull PsiClass aClass) {
            // note: no call to super
            final int totalComplexity = calculateTotalConstructorCount(aClass);
            if (totalComplexity <= getLimit()) {
                return;
            }
            registerClassError(aClass);
        }
    }

    private static int calculateTotalConstructorCount(PsiClass aClass) {
        final PsiMethod[] methods = aClass.getMethods();
        int totalCount = 0;
        for(final PsiMethod method : methods){
            if(method.isConstructor()){
                totalCount++;
            }
        }
        return totalCount;
    }

}
