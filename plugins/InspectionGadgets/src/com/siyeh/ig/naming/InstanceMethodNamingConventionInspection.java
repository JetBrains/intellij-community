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
package com.siyeh.ig.naming;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.psiutils.LibraryUtil;
import org.jetbrains.annotations.NotNull;

public class InstanceMethodNamingConventionInspection
        extends ConventionInspection {

    private static final int DEFAULT_MIN_LENGTH = 4;
    private static final int DEFAULT_MAX_LENGTH = 32;
    private final RenameFix fix = new RenameFix();

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "instance.method.naming.convention.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.NAMING_CONVENTIONS_GROUP_NAME;
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    public String buildErrorString(PsiElement location) {
        final PsiMethod method = (PsiMethod)location.getParent();
        assert method != null;
        final String methodName = method.getName();
        if (methodName.length() < getMinLength()) {
            return InspectionGadgetsBundle.message(
                    "instance.method.name.convention.problem.descriptor.short");
        }
        else if (methodName.length() > getMaxLength()) {
            return InspectionGadgetsBundle.message(
                    "instance.method.name.convention.problem.descriptor.long");
        }
        return InspectionGadgetsBundle.message(
                "instance.method.name.convention.problem.descriptor.regex.mismatch",
                getRegex());
    }

    protected String getDefaultRegex() {
        return "[a-z][A-Za-z]*";
    }

    protected int getDefaultMinLength() {
        return DEFAULT_MIN_LENGTH;
    }

    protected int getDefaultMaxLength() {
        return DEFAULT_MAX_LENGTH;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new NamingConventionsVisitor();
    }

    public ProblemDescriptor[] doCheckMethod(PsiMethod method,
                                             InspectionManager manager,
                                             boolean isOnTheFly) {
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return super.doCheckMethod(method, manager, isOnTheFly);
        }
        if (!containingClass.isPhysical()) {
            return super.doCheckMethod(method, manager, isOnTheFly);
        }
        final BaseInspectionVisitor visitor =
                createVisitor(manager, isOnTheFly);
        method.accept(visitor);
        return visitor.getErrors();
    }

    private class NamingConventionsVisitor extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
            super.visitMethod(method);
            if (method.isConstructor()) {
                return;
            }
            if (method.hasModifierProperty(PsiModifier.STATIC)) {
                return;
            }
            final String name = method.getName();
            if (isValid(name)) {
                return;
            }
            if (LibraryUtil.isOverrideOfLibraryMethod(method)) {
                return;
            }
            registerMethodError(method);
        }
    }
}