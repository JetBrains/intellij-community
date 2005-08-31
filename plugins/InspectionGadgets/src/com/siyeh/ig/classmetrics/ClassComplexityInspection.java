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
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class ClassComplexityInspection
        extends ClassMetricInspection {
    public String getID(){
        return "OverlyComplexClass";
    }
    private static final int DEFAULT_COMPLEXITY_LIMIT = 80;

    public String getDisplayName() {
        return InspectionGadgetsBundle.message("overly.complex.class.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.CLASSMETRICS_GROUP_NAME;
    }

    protected int getDefaultLimit() {
        return DEFAULT_COMPLEXITY_LIMIT;
    }

    protected String getConfigurationLabel() {
        return InspectionGadgetsBundle.message("cyclomatic.complexity.limit.option");
    }

    public String buildErrorString(PsiElement location) {
        final PsiClass aClass = (PsiClass) location.getParent();
        final int totalComplexity = calculateTotalComplexity(aClass);
        return InspectionGadgetsBundle.message("overly.complex.class.problem.descriptor", totalComplexity);
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ClassComplexityVisitor();
    }

    private class ClassComplexityVisitor extends BaseInspectionVisitor {


        public void visitClass(@NotNull PsiClass aClass) {
            // note: no call to super
            final int totalComplexity = calculateTotalComplexity(aClass);
            if (totalComplexity <= getLimit()) {
                return;
            }
            registerClassError(aClass);
        }

    }

    private static int calculateTotalComplexity(PsiClass aClass) {
        final PsiMethod[] methods = aClass.getMethods();
        int totalComplexity = calculateComplexityForMethods(methods);
        totalComplexity += calculateInitializerComplexity(aClass);
        return totalComplexity;
    }

    private static int calculateInitializerComplexity(PsiClass aClass) {
        final ComplexityVisitor visitor = new ComplexityVisitor();
        int complexity = 0;
        final PsiClassInitializer[] initializers = aClass.getInitializers();
        for(final PsiClassInitializer initializer : initializers){
            visitor.reset();
            initializer.accept(visitor);
            complexity += visitor.getComplexity();
        }
        return complexity;
    }

    private static int calculateComplexityForMethods(PsiMethod[] methods) {
        final ComplexityVisitor visitor = new ComplexityVisitor();
        int complexity = 0;
        for(final PsiMethod method : methods){
            visitor.reset();
            method.accept(visitor);
            complexity += visitor.getComplexity();
        }
        return complexity;
    }

}
