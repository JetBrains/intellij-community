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
package com.siyeh.ig.jdk;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class AssertAsNameInspection extends BaseInspection {
    private final RenameFix fix = new RenameFix();

    public String getID(){
        return "AssertAsIdentifier";
    }
    public String getDisplayName() {
        return InspectionGadgetsBundle.message("use.assert.as.identifier.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.JDK_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return InspectionGadgetsBundle.message("use.assert.as.identifier.problem.descriptor");
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    public ProblemDescriptor[] doCheckClass(PsiClass aClass, InspectionManager mgr, boolean isOnTheFly) {
        if (aClass instanceof PsiAnonymousClass) {
            return super.doCheckClass(aClass, mgr, isOnTheFly);
        }
        final BaseInspectionVisitor visitor = createVisitor(mgr, isOnTheFly);
        aClass.accept(visitor);
        return visitor.getErrors();
    }

    public ProblemDescriptor[] doCheckMethod(PsiMethod method, InspectionManager mgr, boolean isOnTheFly) {
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return super.doCheckMethod(method, mgr, isOnTheFly);
        }
        if (!containingClass.isPhysical()) {
            return super.doCheckMethod(method, mgr, isOnTheFly);
        }

        if (containingClass instanceof PsiAnonymousClass) {
            return super.doCheckClass(containingClass, mgr, isOnTheFly);
        }
        final BaseInspectionVisitor visitor = createVisitor(mgr, isOnTheFly);
        method.accept(visitor);
        return visitor.getErrors();
    }

    public ProblemDescriptor[] doCheckField(PsiField field, InspectionManager mgr, boolean isOnTheFly) {
        final PsiClass containingClass = field.getContainingClass();
        if (containingClass == null) {
            return super.doCheckField(field, mgr, isOnTheFly);
        }
        if (!containingClass.isPhysical()) {
            return super.doCheckField(field, mgr, isOnTheFly);
        }
        if (containingClass instanceof PsiAnonymousClass) {
            return super.doCheckClass(containingClass, mgr, isOnTheFly);
        }
        final BaseInspectionVisitor visitor = createVisitor(mgr, isOnTheFly);
        field.accept(visitor);
        return visitor.getErrors();
    }

    public BaseInspectionVisitor buildVisitor() {
        return new AssertAsNameVisitor();
    }

    private static class AssertAsNameVisitor extends BaseInspectionVisitor {


        public void visitVariable(@NotNull PsiVariable variable) {
            super.visitVariable(variable);
            final String variableName = variable.getName();
            if (!PsiKeyword.ASSERT.equals(variableName)) {
                return;
            }
            registerVariableError(variable);
        }

        public void visitMethod(@NotNull PsiMethod method) {
            super.visitMethod(method);
            final String name = method.getName();
            if (!PsiKeyword.ASSERT.equals(name)) {
                return;
            }
            registerMethodError(method);
        }

        public void visitClass(@NotNull PsiClass aClass) {
            //note: no call to super, to avoid drill-down
            final String name = aClass.getName();
            if (!PsiKeyword.ASSERT.equals(name)) {
                return;
            }
            final PsiTypeParameterList params = aClass.getTypeParameterList();
            if (params != null) {
                params.accept(this);
            }
            registerClassError(aClass);
        }


        public void visitTypeParameter(PsiTypeParameter parameter) {
            super.visitTypeParameter(parameter);
            final String name = parameter.getName();
            if (!PsiKeyword.ASSERT.equals(name)) {
                return;
            }
            registerTypeParameterError(parameter);
        }
    }

}
